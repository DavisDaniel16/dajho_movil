package com.example.dajho

import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from

/** Resolves only active memberships; all screens use the same selected business. */
object BusinessSession {
    var selectedId: Long? = null
        private set
    private var ownerId: String? = null

    fun select(id: Long) { selectedId = id }
    fun clear() { selectedId = null; ownerId = null }

    suspend fun memberships(): List<NegocioUsuarioDto> {
        SupabaseModule.client.auth.awaitInitialization()
        val uid = SupabaseModule.client.auth.currentUserOrNull()?.id ?: return emptyList()
        if (ownerId != uid) { clear(); ownerId = uid }
        return SupabaseModule.client.from("negocio_usuario").select {
            filter { eq("usuario_id", uid); eq("activo", true) }
        }.decodeList<NegocioUsuarioDto>().sortedBy { it.negocio_id }
    }

    suspend fun current(): NegocioUsuarioDto? {
        val rows = memberships()
        val membership = rows.firstOrNull { it.negocio_id == selectedId } ?: rows.firstOrNull()
        selectedId = membership?.negocio_id
        return membership
    }

    suspend fun requireId(): Long = current()?.negocio_id
        ?: error("No tienes una membresía activa. Contacta al propietario.")
}
