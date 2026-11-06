package com.example.dajho

import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

object SkuGenerator {
    /**
     * Genera un SKU automático y único basado en el nombre del producto, talla, color y un código aleatorio.
     * Ejemplo: Nombre "Camiseta Polo", Talla "M", Color "Negro" -> "CAM-M-NEG-4892"
     */
    fun generate(nombreProducto: String = "", talla: String = "", color: String = ""): String {
        val cleanNombre = sanitize(nombreProducto)
        val prefix = if (cleanNombre.length >= 3) {
            cleanNombre.take(3)
        } else if (cleanNombre.isNotEmpty()) {
            cleanNombre.padEnd(3, 'X')
        } else {
            "PRD"
        }

        val cleanTalla = sanitize(talla).uppercase(Locale.ROOT)
        val cleanColor = sanitize(color).uppercase(Locale.ROOT)
        val colorCode = if (cleanColor.length >= 3) cleanColor.take(3) else cleanColor

        val parts = mutableListOf<String>()
        parts.add(prefix.uppercase(Locale.ROOT))
        if (cleanTalla.isNotEmpty()) parts.add(cleanTalla)
        if (cleanColor.isNotEmpty()) parts.add(colorCode)

        // Número aleatorio de 4 dígitos para garantizar unicidad
        val randomNum = Random.nextInt(1000, 9999)
        parts.add(randomNum.toString())

        return parts.joinToString("-")
    }

    private fun sanitize(input: String): String {
        val normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFD)
        return normalized.replace(Regex("[^A-Za-z0-9]"), "")
    }
}
