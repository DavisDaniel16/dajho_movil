package com.example.dajho

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

data class PendingSyncItem(
    val id: Long = 0,
    val negocioId: Long,
    val tipo: String,
    val payloadJson: String,
    val requestId: String,
    val createdAt: Long = System.currentTimeMillis()
)

class DajhoDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    companion object {
        private const val DB_NAME = "dajho_offline.db"
        private const val DB_VERSION = 1

        @Volatile
        private var INSTANCE: DajhoDatabase? = null

        fun getInstance(context: Context): DajhoDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DajhoDatabase(context).also { INSTANCE = it }
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Table for Variants (Catalog)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS variantes (
                id INTEGER PRIMARY KEY,
                producto_id INTEGER,
                negocio_id INTEGER,
                sku TEXT,
                codigo_barras TEXT,
                talla TEXT,
                color TEXT,
                precio_venta REAL,
                precio_costo REAL,
                stock_actual INTEGER,
                nombre TEXT,
                raw_json TEXT
            )
            """.trimIndent()
        )

        // Table for Clients
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS clientes (
                id INTEGER PRIMARY KEY,
                negocio_id INTEGER,
                nombre TEXT,
                telefono TEXT,
                raw_json TEXT
            )
            """.trimIndent()
        )

        // Table for Debts
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS deudas (
                id INTEGER PRIMARY KEY,
                negocio_id INTEGER,
                cliente_id INTEGER,
                monto_original REAL,
                saldo REAL,
                estado TEXT,
                raw_json TEXT
            )
            """.trimIndent()
        )

        // Table for Management module items (JSON objects per module)
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS gestion (
                id TEXT PRIMARY KEY,
                negocio_id INTEGER,
                modulo TEXT,
                data_json TEXT
            )
            """.trimIndent()
        )

        // Table for Daily Metrics
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS resumen_dia (
                negocio_id INTEGER,
                fecha TEXT,
                ventas REAL,
                ayer REAL,
                gastos REAL,
                PRIMARY KEY (negocio_id, fecha)
            )
            """.trimIndent()
        )

        // Table for Pending Operations Sync Queue
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_sync (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                negocio_id INTEGER,
                tipo TEXT,
                payload_json TEXT,
                request_id TEXT,
                created_at INTEGER
            )
            """.trimIndent()
        )

        // Table for Business Memberships Cache
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS negocio_sesion (
                user_id TEXT PRIMARY KEY,
                selected_negocio_id INTEGER,
                memberships_json TEXT
            )
            """.trimIndent()
        )

        // Table for Cash Register Open/Close State
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS caja_estado (
                negocio_id INTEGER PRIMARY KEY,
                abierta INTEGER,
                updated_at INTEGER
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS variantes")
        db.execSQL("DROP TABLE IF EXISTS clientes")
        db.execSQL("DROP TABLE IF EXISTS deudas")
        db.execSQL("DROP TABLE IF EXISTS gestion")
        db.execSQL("DROP TABLE IF EXISTS resumen_dia")
        db.execSQL("DROP TABLE IF EXISTS pending_sync")
        db.execSQL("DROP TABLE IF EXISTS negocio_sesion")
        db.execSQL("DROP TABLE IF EXISTS caja_estado")
        onCreate(db)
    }

    // --- CATALOG / VARIANTES ---

    fun saveVariantes(negocioId: Long, variantes: List<VarianteDto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("variantes", "negocio_id = ?", arrayOf(negocioId.toString()))
            for (v in variantes) {
                val values = ContentValues().apply {
                    put("id", v.id ?: 0L)
                    put("producto_id", v.producto_id)
                    put("negocio_id", negocioId)
                    put("sku", v.sku)
                    put("codigo_barras", v.codigo_barras)
                    put("talla", v.talla)
                    put("color", v.color)
                    put("precio_venta", v.precio_venta)
                    put("precio_costo", v.precio_costo)
                    put("stock_actual", v.stock_actual)
                    put("nombre", v.producto?.nombre)
                    put("raw_json", ScreenArgs.json.encodeToString(v))
                }
                db.insertWithOnConflict("variantes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getVariantes(negocioId: Long, query: String = ""): List<VarianteDto> {
        val db = readableDatabase
        val list = mutableListOf<VarianteDto>()
        val selection: String
        val selectionArgs: Array<String>

        if (query.isBlank()) {
            selection = "negocio_id = ?"
            selectionArgs = arrayOf(negocioId.toString())
        } else {
            val q = "%${query.lowercase()}%"
            selection = "negocio_id = ? AND (LOWER(nombre) LIKE ? OR LOWER(sku) LIKE ? OR LOWER(codigo_barras) LIKE ? OR LOWER(talla) LIKE ? OR LOWER(color) LIKE ?)"
            selectionArgs = arrayOf(negocioId.toString(), q, q, q, q, q)
        }

        val cursor = db.query(
            "variantes",
            arrayOf("raw_json"),
            selection,
            selectionArgs,
            null,
            null,
            "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val jsonStr = it.getString(0) ?: continue
                runCatching {
                    val v = ScreenArgs.json.decodeFromString<VarianteDto>(jsonStr)
                    list.add(v)
                }
            }
        }
        return list
    }

    fun updateStockLocally(negocioId: Long, varianteId: Long, cantidadDescontada: Int) {
        val db = writableDatabase
        val cursor = db.query(
            "variantes",
            arrayOf("raw_json"),
            "id = ? AND negocio_id = ?",
            arrayOf(varianteId.toString(), negocioId.toString()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val jsonStr = it.getString(0) ?: return
                val v = ScreenArgs.json.decodeFromString<VarianteDto>(jsonStr)
                val nuevoStock = ((v.stock_actual ?: 0) - cantidadDescontada).coerceAtLeast(0)
                val varianteActualizada = v.copy(stock_actual = nuevoStock)

                val values = ContentValues().apply {
                    put("stock_actual", nuevoStock)
                    put("raw_json", ScreenArgs.json.encodeToString(varianteActualizada))
                }
                db.update("variantes", values, "id = ?", arrayOf(varianteId.toString()))
            }
        }
    }

    // --- CLIENTES ---

    fun saveClientes(negocioId: Long, clientes: List<ClienteDto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("clientes", "negocio_id = ?", arrayOf(negocioId.toString()))
            for (c in clientes) {
                val values = ContentValues().apply {
                    put("id", c.id ?: 0L)
                    put("negocio_id", negocioId)
                    put("nombre", c.nombre)
                    put("telefono", c.telefono)
                    put("raw_json", ScreenArgs.json.encodeToString(c))
                }
                db.insertWithOnConflict("clientes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getClientes(negocioId: Long): List<ClienteDto> {
        val db = readableDatabase
        val list = mutableListOf<ClienteDto>()
        val cursor = db.query(
            "clientes",
            arrayOf("raw_json"),
            "negocio_id = ?",
            arrayOf(negocioId.toString()),
            null, null, "nombre ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val jsonStr = it.getString(0) ?: continue
                runCatching {
                    list.add(ScreenArgs.json.decodeFromString<ClienteDto>(jsonStr))
                }
            }
        }
        return list
    }

    // --- DEUDAS ---

    fun saveDeudas(negocioId: Long, deudas: List<DeudaDto>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("deudas", "negocio_id = ?", arrayOf(negocioId.toString()))
            for (d in deudas) {
                val values = ContentValues().apply {
                    put("id", d.id ?: 0L)
                    put("negocio_id", negocioId)
                    put("cliente_id", d.cliente_id)
                    put("monto_original", d.monto_original)
                    put("saldo", d.saldo)
                    put("estado", d.estado)
                    put("raw_json", ScreenArgs.json.encodeToString(d))
                }
                db.insertWithOnConflict("deudas", null, values, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getDeudas(negocioId: Long): List<DeudaDto> {
        val db = readableDatabase
        val list = mutableListOf<DeudaDto>()
        val cursor = db.query(
            "deudas",
            arrayOf("raw_json"),
            "negocio_id = ?",
            arrayOf(negocioId.toString()),
            null, null, "id DESC"
        )
        cursor.use {
            while (it.moveToNext()) {
                val jsonStr = it.getString(0) ?: continue
                runCatching {
                    list.add(ScreenArgs.json.decodeFromString<DeudaDto>(jsonStr))
                }
            }
        }
        return list
    }

    fun abonarDeudaClienteLocally(negocioId: Long, clienteId: Long, montoAbono: Double) {
        val db = writableDatabase
        var restante = montoAbono
        val cursor = db.query(
            "deudas",
            arrayOf("id", "raw_json"),
            "negocio_id = ? AND cliente_id = ? AND saldo > 0",
            arrayOf(negocioId.toString(), clienteId.toString()),
            null, null, "id ASC"
        )
        val deudasModificar = mutableListOf<Pair<Long, DeudaDto>>()
        cursor.use {
            while (it.moveToNext() && restante > 0) {
                val id = it.getLong(0)
                val jsonStr = it.getString(1) ?: continue
                val d = ScreenArgs.json.decodeFromString<DeudaDto>(jsonStr)
                val saldoActual = d.saldo ?: 0.0
                val abonoAplicado = Math.min(restante, saldoActual)
                val nuevoSaldo = (saldoActual - abonoAplicado).coerceAtLeast(0.0)
                val nuevoEstado = if (nuevoSaldo <= 0.0) "pagado" else "parcial"
                val deudaActualizada = d.copy(
                    saldo = nuevoSaldo,
                    estado = nuevoEstado
                )
                deudasModificar.add(Pair(id, deudaActualizada))
                restante -= abonoAplicado
            }
        }

        for ((id, deuda) in deudasModificar) {
            val values = ContentValues().apply {
                put("saldo", deuda.saldo)
                put("estado", deuda.estado)
                put("raw_json", ScreenArgs.json.encodeToString(deuda))
            }
            db.update("deudas", values, "id = ?", arrayOf(id.toString()))
        }
    }

    // --- GESTION (MODULOS GENERALES) ---

    fun saveGestionItems(negocioId: Long, modulo: String, itemsJson: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("id", "${negocioId}_$modulo")
            put("negocio_id", negocioId)
            put("modulo", modulo)
            put("data_json", itemsJson)
        }
        db.insertWithOnConflict("gestion", null, values, SQLiteDatabase.CONFLICT_REPLACE)

        if (modulo == "caja") {
            val hasOpenCaja = itemsJson.contains("\"estado\":\"abierta\"") || itemsJson.contains("\"estado\": \"abierta\"")
            setCajaEstado(negocioId, hasOpenCaja)
        }
    }

    fun setCajaEstado(negocioId: Long, isOpen: Boolean) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("negocio_id", negocioId)
            put("abierta", if (isOpen) 1 else 0)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("caja_estado", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun isCajaAbierta(negocioId: Long): Boolean {
        val db = readableDatabase
        val cursor = db.query(
            "caja_estado",
            arrayOf("abierta"),
            "negocio_id = ?",
            arrayOf(negocioId.toString()),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getInt(0) == 1
            }
        }
        // Default to true if state hasn't been synced yet
        return true
    }

    fun getGestionItems(negocioId: Long, modulo: String): String? {
        val db = readableDatabase
        val cursor = db.query(
            "gestion",
            arrayOf("data_json"),
            "negocio_id = ? AND modulo = ?",
            arrayOf(negocioId.toString(), modulo),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    // --- RESUMEN DEL DIA ---

    fun saveResumenDia(negocioId: Long, fecha: String, ventas: Double, ayer: Double, gastos: Double) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("negocio_id", negocioId)
            put("fecha", fecha)
            put("ventas", ventas)
            put("ayer", ayer)
            put("gastos", gastos)
        }
        db.insertWithOnConflict("resumen_dia", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getResumenDia(negocioId: Long, fecha: String): ResumenDiaDto? {
        val db = readableDatabase
        val cursor = db.query(
            "resumen_dia",
            arrayOf("ventas", "ayer", "gastos"),
            "negocio_id = ? AND fecha = ?",
            arrayOf(negocioId.toString(), fecha),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                return ResumenDiaDto(
                    ventas = it.getDouble(0),
                    ayer = it.getDouble(1),
                    gastos = it.getDouble(2)
                )
            }
        }
        return null
    }

    fun addVentaLocalAResumen(negocioId: Long, fecha: String, montoVenta: Double) {
        val actual = getResumenDia(negocioId, fecha)
        val ventas = (actual?.ventas ?: 0.0) + montoVenta
        val ayer = actual?.ayer ?: 0.0
        val gastos = actual?.gastos ?: 0.0
        saveResumenDia(negocioId, fecha, ventas, ayer, gastos)
    }

    fun addGastoLocalAResumen(negocioId: Long, fecha: String, montoGasto: Double) {
        val actual = getResumenDia(negocioId, fecha)
        val ventas = actual?.ventas ?: 0.0
        val ayer = actual?.ayer ?: 0.0
        val gastos = (actual?.gastos ?: 0.0) + montoGasto
        saveResumenDia(negocioId, fecha, ventas, ayer, gastos)
    }

    // --- PENDING SYNC QUEUE ---

    fun insertPendingSync(negocioId: Long, tipo: String, payloadJson: String, requestId: String): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("negocio_id", negocioId)
            put("tipo", tipo)
            put("payload_json", payloadJson)
            put("request_id", requestId)
            put("created_at", System.currentTimeMillis())
        }
        return db.insert("pending_sync", null, values)
    }

    fun getPendingSyncItems(): List<PendingSyncItem> {
        val db = readableDatabase
        val list = mutableListOf<PendingSyncItem>()
        val cursor = db.query(
            "pending_sync",
            arrayOf("id", "negocio_id", "tipo", "payload_json", "request_id", "created_at"),
            null, null, null, null, "id ASC"
        )
        cursor.use {
            while (it.moveToNext()) {
                list.add(
                    PendingSyncItem(
                        id = it.getLong(0),
                        negocioId = it.getLong(1),
                        tipo = it.getString(2),
                        payloadJson = it.getString(3),
                        requestId = it.getString(4),
                        createdAt = it.getLong(5)
                    )
                )
            }
        }
        return list
    }

    fun deletePendingSync(id: Long) {
        val db = writableDatabase
        db.delete("pending_sync", "id = ?", arrayOf(id.toString()))
    }

    fun getPendingSyncCount(): Int {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM pending_sync", null)
        cursor.use {
            if (it.moveToFirst()) return it.getInt(0)
        }
        return 0
    }

    // --- BUSINESS SESSION CACHE ---

    fun saveSession(userId: String, selectedNegocioId: Long?, memberships: List<NegocioUsuarioDto>) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("user_id", userId)
            put("selected_negocio_id", selectedNegocioId)
            put("memberships_json", ScreenArgs.json.encodeToString(memberships))
        }
        db.insertWithOnConflict("negocio_sesion", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getSessionMemberships(userId: String): List<NegocioUsuarioDto>? {
        val db = readableDatabase
        val cursor = db.query(
            "negocio_sesion",
            arrayOf("memberships_json"),
            "user_id = ?",
            arrayOf(userId),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val jsonStr = it.getString(0) ?: return null
                return runCatching { ScreenArgs.json.decodeFromString<List<NegocioUsuarioDto>>(jsonStr) }.getOrNull()
            }
        }
        return null
    }

    fun getSessionSelectedNegocioId(userId: String): Long? {
        val db = readableDatabase
        val cursor = db.query(
            "negocio_sesion",
            arrayOf("selected_negocio_id"),
            "user_id = ?",
            arrayOf(userId),
            null, null, null
        )
        cursor.use {
            if (it.moveToFirst() && !it.isNull(0)) {
                return it.getLong(0)
            }
        }
        return null
    }
}
