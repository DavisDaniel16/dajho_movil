package com.example.dajho

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CatalogRepository {
    suspend fun load(negocio: Long, query: String = ""): List<VarianteDto> {
        val all = mutableListOf<VarianteDto>()
        do {
            val page = SupabaseModule.client.postgrest.rpc("catalogo_variantes", buildJsonObject {
                put("p_negocio", negocio); put("p_busqueda", query)
                put("p_offset", all.size); put("p_limite", 200)
            }).decodeList<VarianteDto>()
            all.addAll(page)
        } while (page.size == 200)
        return all
    }
}
