package com.example.dajho

import android.content.Context
import io.github.jan.supabase.postgrest.from

/** Resolves active memberships and supports offline caching. */
object BusinessSession {
    var selectedId: Long? = null
        private set
    private var ownerId: String? = null
    private var cachedMemberships: List<NegocioUsuarioDto>? = null

    fun select(id: Long) {
        selectedId = id
    }

    fun restore(userId: String, membership: NegocioUsuarioDto) {
        ownerId = userId
        cachedMemberships = listOf(membership)
        selectedId = membership.negocio_id
    }

    fun clear() {
        selectedId = null
        ownerId = null
        cachedMemberships = null
    }

    internal fun cachedFor(userId: String?): List<NegocioUsuarioDto>? =
        cachedMemberships?.takeIf { userId != null && ownerId == userId }

    suspend fun memberships(context: Context? = null, forceRefresh: Boolean = false): List<NegocioUsuarioDto> {
        val uid = SessionAccess.localUser()?.id
        if (uid == null) {
            clear()
            return emptyList()
        }
        if (ownerId != uid) {
            clear()
            ownerId = uid
        }

        if (!forceRefresh) cachedFor(uid)?.let { return it }

        val db = context?.let { DajhoDatabase.getInstance(it) }

        if (context == null || context.hasInternetConnection()) {
            try {
                val result = SupabaseModule.client.from("negocio_usuario").select {
                    filter { eq("usuario_id", uid); eq("activo", true) }
                }.decodeList<NegocioUsuarioDto>().sortedBy { it.negocio_id }

                cachedMemberships = result
                if (selectedId == null) {
                    selectedId = db?.getSessionSelectedNegocioId(uid) ?: result.firstOrNull()?.negocio_id
                }
                db?.saveSession(uid, selectedId, result)
                return result
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                val local = db?.getSessionMemberships(uid)
                if (!local.isNullOrEmpty()) {
                    cachedMemberships = local
                    if (selectedId == null) {
                        selectedId = db.getSessionSelectedNegocioId(uid) ?: local.firstOrNull()?.negocio_id
                    }
                    return local
                }
            }
        } else if (db != null) {
            val local = db.getSessionMemberships(uid)
            if (!local.isNullOrEmpty()) {
                cachedMemberships = local
                if (selectedId == null) {
                    selectedId = db.getSessionSelectedNegocioId(uid) ?: local.firstOrNull()?.negocio_id
                }
                return local
            }
        }

        return emptyList()
    }

    suspend fun current(context: Context? = null, forceRefresh: Boolean = false): NegocioUsuarioDto? {
        val rows = memberships(context, forceRefresh)
        val membership = rows.firstOrNull { it.negocio_id == selectedId } ?: rows.firstOrNull()
        selectedId = membership?.negocio_id
        return membership
    }

    suspend fun requireId(context: Context? = null): Long = current(context)?.negocio_id
        ?: error("No tienes una membresía activa. Contacta al propietario.")
}
