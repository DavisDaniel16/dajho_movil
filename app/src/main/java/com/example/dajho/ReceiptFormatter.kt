package com.example.dajho

import java.util.Locale

object ReceiptFormatter {
    fun formatTextReceipt(
        businessName: String,
        ticketTitle: String,
        fecha: String? = null,
        detalles: List<DetalleVentaBalanceDto>
    ): String {
        val sb = StringBuilder()
        val border = "========================================"
        val divider = "----------------------------------------"

        val negocioUpper = (businessName.ifBlank { "DAJHO POS" }).uppercase(Locale.ROOT)

        sb.appendLine(border)
        sb.appendLine(centerText(negocioUpper, 40))
        sb.appendLine(centerText("COMPROBANTE DE VENTA", 40))
        sb.appendLine(border)
        sb.appendLine(" Ticket: $ticketTitle")
        if (!fecha.isNullOrBlank()) {
            sb.appendLine(" Fecha:  $fecha")
        }
        sb.appendLine(divider)
        sb.appendLine(" CANT   DESCRIPCIÓN              TOTAL")
        sb.appendLine(divider)

        var totalCalculado = 0.0

        if (detalles.isEmpty()) {
            sb.appendLine("  Sin productos registrados")
        } else {
            detalles.forEach { item ->
                val prodNombre = item.variante?.producto?.nombre ?: "Producto"
                val cant = item.cantidad ?: 1
                val pu = item.precio_unitario ?: 0.0
                val subtotal = cant * pu
                totalCalculado += subtotal

                val talla = item.variante?.talla?.takeIf { it.isNotBlank() }
                val color = item.variante?.color?.takeIf { it.isNotBlank() }
                val sku = item.variante?.sku?.takeIf { it.isNotBlank() }

                val specs = listOfNotNull(
                    talla?.let { "Talla: $it" },
                    color?.let { "Color: $it" },
                    sku?.let { "SKU: $it" }
                ).joinToString(" | ")

                val subtotalStr = "$${String.format(Locale.US, "%.2f", subtotal)}"
                val cantStr = " ${cant}x ".padEnd(7)
                val descStr = prodNombre.take(20).padEnd(20)

                sb.appendLine("$cantStr$descStr ${subtotalStr.padStart(10)}")
                if (specs.isNotEmpty()) {
                    sb.appendLine("        ($specs)")
                }
            }
        }

        sb.appendLine(divider)
        val totalFormatted = "$${String.format(Locale.US, "%.2f", totalCalculado)}"
        sb.appendLine(" TOTAL A PAGAR:".padEnd(28) + totalFormatted.padStart(12))
        sb.appendLine(border)
        sb.appendLine(centerText("¡Gracias por su compra!", 40))
        sb.appendLine(centerText("Generado desde DAJHO POS", 40))
        sb.appendLine(border)

        return sb.toString()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text
        val padding = (width - text.length) / 2
        return " ".repeat(padding) + text
    }
}
