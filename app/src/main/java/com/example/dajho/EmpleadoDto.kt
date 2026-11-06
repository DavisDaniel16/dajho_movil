package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class EmpleadoDto(
    val usuario_id: String? = null,
    val nombre: String? = null,
    val correo: String? = null,
    val rol: String? = null,
    val activo: Boolean? = null,
    val tope_descuento_usd: Double? = null
)
