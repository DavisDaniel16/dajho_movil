package com.example.dajho

import kotlinx.serialization.Serializable

@Serializable
data class ClienteSimpleDto(
    val id: Long? = null,
    val nombre: String? = null,
    val telefono: String? = null
)

@Serializable
data class ProductoNombreSimpleDto(
    val nombre: String? = null
)

@Serializable
data class VarianteSimpleDto(
    val id: Long? = null,
    val sku: String? = null,
    val talla: String? = null,
    val color: String? = null,
    val producto: ProductoNombreSimpleDto? = null
)

@Serializable
data class DetalleVentaSimpleDto(
    val cantidad: Int? = null,
    val variante: VarianteSimpleDto? = null
)

@Serializable
data class VentaDetalleSimpleDto(
    val id: Long? = null,
    val fecha_dispositivo: String? = null,
    val detalle_venta: List<DetalleVentaSimpleDto>? = null
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
    val venta: VentaDetalleSimpleDto? = null
)

data class DeudaClienteAgrupadaDto(
    val clienteId: Long?,
    val clienteNombre: String,
    val clienteTelefono: String,
    val saldoTotal: Double,
    val cantidadDeudas: Int,
    val listaDeudasIndividuales: List<DeudaDto>
)
