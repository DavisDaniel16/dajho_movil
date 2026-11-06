package com.example.dajho

import java.math.BigDecimal
import java.math.RoundingMode
import kotlinx.serialization.json.JsonPrimitive

object Money {
    fun amount(text: String): BigDecimal = text.trim().replace(',', '.').toBigDecimal()
        .also { require(it.scale() <= 2) { "Usa máximo dos decimales" } }
        .setScale(2, RoundingMode.UNNECESSARY)
    fun decimal(value: Double): BigDecimal = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
    fun json(value: BigDecimal): JsonPrimitive = JsonPrimitive(value.setScale(2, RoundingMode.UNNECESSARY))
    fun total(items: List<ItemCarritoDto>): BigDecimal = items.fold(BigDecimal.ZERO) { total, item ->
        total + decimal(requireNotNull(item.variante.precio_venta)) * item.cantidad.toBigDecimal()
    }.setScale(2)
}
