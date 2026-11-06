package com.example.dajho.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "variante")
data class VarianteEntity(
    @PrimaryKey val id: Long,
    val producto_id: Long,
    val negocio_id: Long,
    val sku: String? = null,
    val codigo_barras: String? = null,
    val talla: String? = null,
    val color: String? = null,
    val precio_costo: Double = 0.0,
    val precio_venta: Double = 0.0,
    val precio_minimo: Double? = null,
    val stock_actual: Int = 0,
    val stock_minimo: Int = 0,
    val producto_nombre: String = ""
)
