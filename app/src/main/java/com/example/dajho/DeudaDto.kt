package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class ClienteSimpleDto(
    val id: Long? = null,
    val nombre: String? = null,
    val telefono: String? = null
)

@Serializable
data class VentaSimpleDto(
    val id: Long? = null,
    val fecha_dispositivo: String? = null
)

@Serializable
data class DeudaDto(
    val id: Long? = null,
    val cliente_id: Long? = null,
    val venta_id: Long? = null,
    val monto_original: Double? = null,
    val saldo: Double? = null,
    val estado: String? = null,
    val fecha_vencimiento: String? = null,
    val cliente: ClienteSimpleDto? = null,
    val venta: VentaSimpleDto? = null
)

data class DeudaClienteAgrupadaDto(
    val clienteId: Long?,
    val clienteNombre: String,
    val clienteTelefono: String,
    val saldoTotal: Double,
    val cantidadDeudas: Int,
    val listaDeudasIndividuales: List<DeudaDto>
)
