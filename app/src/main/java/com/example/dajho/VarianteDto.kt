package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class ProductoSimpleDto(
    val id: Long? = null,
    val nombre: String? = null
)

@Serializable
data class VarianteDto(
    val id: Long? = null,
    val sku: String? = null,
    val talla: String? = null,
    val color: String? = null,
    val precio_venta: Double? = null,
    val precio_costo: Double? = null,
    val stock_actual: Int? = null,
    val producto_id: Long? = null,
    val producto: ProductoSimpleDto? = null
)
