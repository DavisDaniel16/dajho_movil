package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class ResumenDiaDto(val ventas: Double, val ayer: Double, val gastos: Double)

@Serializable
data class BalanceNegocioDto(val ingresos: Double, val egresos: Double, val movimientos: List<MovimientoDto>)
