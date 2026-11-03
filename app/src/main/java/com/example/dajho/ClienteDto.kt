package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class ClienteDto(
    val id: Long? = null,
    val nombre: String? = null,
    val telefono: String? = null,
    val negocio_id: Long? = null
)
