package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class ProductoSimpleDto(
    val id: Long? = null,
    val nombre: String? = null,
    val marca: String? = null,
    val categoria_id: Long? = null,
    val temporada_id: Long? = null
)

@Serializable
data class VarianteDto(
    val id: Long? = null,
    val sku: String? = null,
    val codigo_barras: String? = null,
    val talla: String? = null,
    val color: String? = null,
    val precio_venta: Double? = null,
    val precio_costo: Double? = null,
    val precio_minimo: Double? = null,
    val stock_actual: Int? = null,
    val stock_minimo: Int? = null,
    val producto_id: Long? = null,
    val producto: ProductoSimpleDto? = null
)
