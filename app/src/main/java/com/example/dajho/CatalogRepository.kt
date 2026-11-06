package com.example.dajho

import android.content.Context
import com.example.dajho.db.AppDatabase
import com.example.dajho.db.entities.VarianteEntity
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CatalogRepository {
    suspend fun load(negocio: Long, query: String = "", context: Context? = null): List<VarianteDto> {
        val db = context?.let { AppDatabase.getInstance(it) }

        // 1. Cargar desde la base de datos local (Room) primero para UI instantánea
        var localList: List<VarianteDto> = emptyList()
        if (db != null) {
            withContext(Dispatchers.IO) {
                try {
                    val entities = if (query.isBlank()) {
                        db.inventarioDao().getVariantesByNegocio(negocio)
                    } else {
                        db.inventarioDao().buscarVariantes(negocio, query)
                    }
                    localList = entities.map { it.toDto() }
                } catch (e: Exception) {
                    // Fallback silencioso si falla la lectura local
                }
            }
        }

        // 2. Intentar refrescar desde Supabase si hay red
        try {
            val all = mutableListOf<VarianteDto>()
            do {
                val page = SupabaseModule.client.postgrest.rpc("catalogo_variantes", buildJsonObject {
                    put("p_negocio", negocio)
                    put("p_busqueda", query)
                    put("p_offset", all.size)
                    put("p_limite", 200)
                }).decodeList<VarianteDto>()
                all.addAll(page)
            } while (page.size == 200)

            // Guardar en la base de datos local Room
            if (db != null && query.isBlank()) {
                withContext(Dispatchers.IO) {
                    try {
                        val entities = all.mapNotNull { it.toEntity(negocio) }
                        db.inventarioDao().insertOrUpdateVariantes(entities)
                    } catch (e: Exception) {
                        // Ignore local cache error
                    }
                }
            }

            return all
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Si la red falla y tenemos datos locales en Room, los devolvemos (Modo Offline)
            if (localList.isNotEmpty()) {
                return localList
            }
            throw e
        }
    }

    private fun VarianteDto.toEntity(negocioId: Long): VarianteEntity? {
        val varId = id ?: return null
        return VarianteEntity(
            id = varId,
            producto_id = producto_id ?: 0,
            negocio_id = negocioId,
            sku = sku,
            codigo_barras = codigo_barras,
            talla = talla,
            color = color,
            precio_costo = precio_costo ?: 0.0,
            precio_venta = precio_venta ?: 0.0,
            precio_minimo = precio_minimo,
            stock_actual = stock_actual ?: 0,
            stock_minimo = stock_minimo ?: 0,
            producto_nombre = producto?.nombre ?: ""
        )
    }

    private fun VarianteEntity.toDto(): VarianteDto {
        return VarianteDto(
            id = id,
            sku = sku,
            codigo_barras = codigo_barras,
            talla = talla,
            color = color,
            precio_venta = precio_venta,
            precio_costo = precio_costo,
            precio_minimo = precio_minimo,
            stock_actual = stock_actual,
            stock_minimo = stock_minimo,
            producto_id = producto_id,
            producto = ProductoSimpleDto(id = producto_id, nombre = producto_nombre)
        )
    }
}
