package com.example.dajho

@kotlinx.serialization.Serializable
data class MovimientoDto(
    val venta_id: Long? = null,
    val titulo: String,
    val tipo: String, // "ingreso" o "egreso"
    val monto: Double,
    val fecha: String,
    val detallesVenta: List<DetalleVentaBalanceDto> = emptyList()
)
