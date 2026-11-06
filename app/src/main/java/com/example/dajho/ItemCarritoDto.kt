package com.example.dajho

@kotlinx.serialization.Serializable
data class ItemCarritoDto(
    val variante: VarianteDto,
    var cantidad: Int = 1
) {
    val subtotal: Double
        get() = (variante.precio_venta ?: 0.0) * cantidad
}
