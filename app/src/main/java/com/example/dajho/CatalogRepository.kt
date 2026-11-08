package com.example.dajho

import android.content.Context
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CatalogRepository {
    suspend fun load(
        negocio: Long,
        query: String = "",
        context: Context? = null,
        forceRefresh: Boolean = false
    ): List<VarianteDto> {
        val db = context?.let { DajhoDatabase.getInstance(it) }

        if (context != null && context.hasInternetConnection()) {
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

                if (query.isBlank() && db != null) {
                    db.saveVariantes(negocio, all)
                }
                return all
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Fallback to local database cache if network call fails
                if (db != null) {
                    return db.getVariantes(negocio, query)
                }
                throw e
            }
        } else if (db != null) {
            return db.getVariantes(negocio, query)
        }

        return emptyList()
    }
}
