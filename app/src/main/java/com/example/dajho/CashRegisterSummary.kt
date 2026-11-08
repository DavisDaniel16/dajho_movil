package com.example.dajho

import java.math.RoundingMode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun cashRegisterSummary(row: JsonObject): String {
    fun amount(key: String): String = row[key]?.jsonPrimitive?.contentOrNull
        ?.toBigDecimalOrNull()?.setScale(2, RoundingMode.HALF_UP)?.let { "$" + it.toPlainString() }
        ?: "Pendiente de cierre"
    return "Inicial: ${amount("monto_inicial")}  ·  Esperado: ${amount("monto_esperado")}\nDeclarado: ${amount("monto_declarado")}"
}
