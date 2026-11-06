package com.example.dajho

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

object CustomerRepository {
    suspend fun clients(business: Long): List<ClienteDto> {
        val all = mutableListOf<ClienteDto>()
        do {
            val page = SupabaseModule.client.postgrest.rpc("listar_gestion", buildJsonObject {
                put("p_negocio", business); put("p_modulo", "cliente"); put("p_offset", all.size)
            }).decodeList<ClienteDto>()
            all.addAll(page)
        } while (page.size == 200)
        return all
    }
    suspend fun debts(business: Long): List<DeudaDto> {
        val all = mutableListOf<DeudaDto>()
        do {
            val page = SupabaseModule.client.postgrest.rpc("deudas_negocio", buildJsonObject {
                put("p_negocio", business); put("p_offset", all.size)
            }).decodeList<DeudaDto>()
            all.addAll(page)
        } while (page.size == 200)
        return all
    }
}
