package com.example.dajho

data class MovimientoDto(
    val titulo: String,
    val tipo: String, // "ingreso" o "egreso"
    val monto: Double,
    val fecha: String
)
