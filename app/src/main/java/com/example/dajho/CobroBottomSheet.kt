package com.example.dajho

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale

class CobroBottomSheet(
    private val totalOriginal: Double,
    private val listaCarrito: List<ItemCarritoDto>
) : BottomSheetDialogFragment() {

    private lateinit var tvTotalOriginal: TextView
    private lateinit var etDescuento: TextInputEditText
    private lateinit var tvTotalFinal: TextView

    private lateinit var btnEfectivo: MaterialButton
    private lateinit var btnTransferencia: MaterialButton
    private lateinit var btnQR: MaterialButton
    private lateinit var btnFiado: MaterialButton

    private lateinit var layoutEfectivo: LinearLayout
    private lateinit var etMontoRecibido: TextInputEditText
    private lateinit var tvCambio: TextView

    private lateinit var layoutReferencia: LinearLayout
    private lateinit var etReferencia: TextInputEditText

    private lateinit var layoutFiado: LinearLayout
    private lateinit var btnSeleccionarCliente: MaterialButton

    private lateinit var btnConfirmarVenta: MaterialButton

    private var metodoPagoSeleccionado: String = "Efectivo"
    private var totalCalculado: Double = totalOriginal

    private var clienteSeleccionadoId: Long? = null
    private var clienteSeleccionadoNombre: String? = null
    private var userNegocioId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_cobro, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTotalOriginal = view.findViewById(R.id.tvTotalOriginal)
        etDescuento = view.findViewById(R.id.etDescuento)
        tvTotalFinal = view.findViewById(R.id.tvTotalFinal)

        btnEfectivo = view.findViewById(R.id.btnEfectivo)
        btnTransferencia = view.findViewById(R.id.btnTransferencia)
        btnQR = view.findViewById(R.id.btnQR)
        btnFiado = view.findViewById(R.id.btnFiado)

        layoutEfectivo = view.findViewById(R.id.layoutEfectivo)
        etMontoRecibido = view.findViewById(R.id.etMontoRecibido)
        tvCambio = view.findViewById(R.id.tvCambio)

        layoutReferencia = view.findViewById(R.id.layoutReferencia)
        etReferencia = view.findViewById(R.id.etReferencia)

        layoutFiado = view.findViewById(R.id.layoutFiado)
        btnSeleccionarCliente = view.findViewById(R.id.btnSeleccionarCliente)

        btnConfirmarVenta = view.findViewById(R.id.btnConfirmarVenta)

        // Cargar negocio_id
        lifecycleScope.launch {
            userNegocioId = getNegocioId()
        }

        // Inicializar total y estilos por defecto
        tvTotalOriginal.text = "$${String.format(Locale.US, "%.2f", totalOriginal)}"
        recalcularTotalFinal()
        actualizarEstilosBotones("Efectivo")

        // Recalcular total al ingresar descuento
        etDescuento.doAfterTextChanged {
            recalcularTotalFinal()
        }

        // Recalcular cambio al ingresar monto recibido
        etMontoRecibido.doAfterTextChanged {
            recalcularCambio()
        }

        // Selección de Métodos de Pago
        btnEfectivo.setOnClickListener { seleccionarMetodo("Efectivo") }
        btnTransferencia.setOnClickListener { seleccionarMetodo("Transferencia") }
        btnQR.setOnClickListener { seleccionarMetodo("QR") }
        btnFiado.setOnClickListener { seleccionarMetodo("Fiado") }

        // Seleccionar cliente mediante ClientesBottomSheet
        btnSeleccionarCliente.setOnClickListener {
            abrirBottomSheetClientes()
        }

        btnConfirmarVenta.setOnClickListener {
            confirmarVenta()
        }
    }

    private suspend fun getNegocioId(): Long? {
        val currentUser = SupabaseModule.client.auth.currentUserOrNull() ?: return null
        return try {
            val negocioUsuario = SupabaseModule.client.from("negocio_usuario")
                .select {
                    filter {
                        eq("usuario_id", currentUser.id)
                    }
                }
                .decodeSingleOrNull<NegocioUsuarioDto>()
            negocioUsuario?.negocio_id
        } catch (e: Exception) {
            Log.e("CobroBottomSheet", "Error al obtener negocio_id", e)
            null
        }
    }

    private fun abrirBottomSheetClientes() {
        lifecycleScope.launch {
            val negocioId = userNegocioId ?: getNegocioId()
            if (negocioId == null) {
                Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                return@launch
            }

            childFragmentManager.setFragmentResultListener("request_cliente_seleccionado", viewLifecycleOwner) { _, bundle ->
                val id = bundle.getLong("cliente_id", -1L)
                val nombre = bundle.getString("cliente_nombre", "")
                if (id != -1L) {
                    clienteSeleccionadoId = id
                    clienteSeleccionadoNombre = nombre
                    btnSeleccionarCliente.text = "Cliente: $nombre"
                }
            }

            val clientesBottomSheet = ClientesBottomSheet(negocioId)
            clientesBottomSheet.show(childFragmentManager, "ClientesBottomSheet")
        }
    }

    private fun recalcularTotalFinal() {
        val descuentoStr = etDescuento.text.toString().trim()
        val descuento = descuentoStr.toDoubleOrNull() ?: 0.0
        totalCalculado = (totalOriginal - descuento).coerceAtLeast(0.0)
        tvTotalFinal.text = "$${String.format(Locale.US, "%.2f", totalCalculado)}"
        recalcularCambio()
    }

    private fun recalcularCambio() {
        val recibidoStr = etMontoRecibido.text.toString().trim()
        val recibido = recibidoStr.toDoubleOrNull() ?: 0.0
        val cambio = (recibido - totalCalculado).coerceAtLeast(0.0)
        tvCambio.text = "Cambio: $${String.format(Locale.US, "%.2f", cambio)}"
    }

    private fun seleccionarMetodo(metodo: String) {
        metodoPagoSeleccionado = metodo
        actualizarEstilosBotones(metodo)

        when (metodo) {
            "Efectivo" -> {
                layoutEfectivo.visibility = View.VISIBLE
                layoutReferencia.visibility = View.GONE
                layoutFiado.visibility = View.GONE
            }
            "Transferencia", "QR" -> {
                layoutEfectivo.visibility = View.GONE
                layoutReferencia.visibility = View.VISIBLE
                layoutFiado.visibility = View.GONE
            }
            "Fiado" -> {
                layoutEfectivo.visibility = View.GONE
                layoutReferencia.visibility = View.GONE
                layoutFiado.visibility = View.VISIBLE
            }
        }
    }

    private fun actualizarEstilosBotones(metodoSeleccionado: String) {
        val colorDark = Color.parseColor("#1E293B")
        val colorWhite = Color.WHITE
        val colorGrayText = Color.parseColor("#64748B")
        val colorGrayStroke = Color.parseColor("#CBD5E1")
        val colorTransparent = Color.TRANSPARENT

        val botones = mapOf(
            "Efectivo" to btnEfectivo,
            "Transferencia" to btnTransferencia,
            "QR" to btnQR,
            "Fiado" to btnFiado
        )

        botones.forEach { (metodo, btn) ->
            if (metodo == metodoSeleccionado) {
                btn.backgroundTintList = ColorStateList.valueOf(colorDark)
                btn.setTextColor(colorWhite)
                btn.strokeColor = ColorStateList.valueOf(colorDark)
            } else {
                btn.backgroundTintList = ColorStateList.valueOf(colorTransparent)
                btn.setTextColor(colorGrayText)
                btn.strokeColor = ColorStateList.valueOf(colorGrayStroke)
            }
        }
    }

    private fun confirmarVenta() {
        when (metodoPagoSeleccionado) {
            "Efectivo" -> {
                val recibidoStr = etMontoRecibido.text.toString().trim()
                val recibido = recibidoStr.toDoubleOrNull() ?: 0.0
                if (recibido < totalCalculado) {
                    Toast.makeText(requireContext(), "El monto recibido es menor al total a pagar", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            "Transferencia", "QR" -> {
                val ref = etReferencia.text.toString().trim()
                if (ref.isEmpty()) {
                    Toast.makeText(requireContext(), "Por favor ingresa el número de referencia", Toast.LENGTH_SHORT).show()
                    return
                }
            }
            "Fiado" -> {
                if (clienteSeleccionadoId == null) {
                    Toast.makeText(requireContext(), "Debes seleccionar un cliente para fiar", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }

        btnConfirmarVenta.isEnabled = false

        lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                    btnConfirmarVenta.isEnabled = true
                    return@launch
                }

                val uuidDispositivo = java.util.UUID.randomUUID().toString()
                val fechaDispositivo = java.time.OffsetDateTime.now().toString()

                val descuentoStr = etDescuento.text.toString().trim()
                val descuento = descuentoStr.toDoubleOrNull() ?: 0.0

                // 1. Construir JSON de ítems (p_items)
                val itemsJson = buildJsonArray {
                    listaCarrito.forEach { item ->
                        add(buildJsonObject {
                            put("variante_id", item.variante.id)
                            put("cantidad", item.cantidad)
                            put("precio_unitario", item.variante.precio_venta ?: 0.0)
                        })
                    }
                }

                // 2. Construir JSON de pagos (p_pagos)
                val pagosJson = buildJsonArray {
                    add(buildJsonObject {
                        val metodoClean = when (metodoPagoSeleccionado) {
                            "Fiado" -> "fiado"
                            "Efectivo" -> "efectivo"
                            "Transferencia" -> "transferencia"
                            "QR" -> "qr"
                            else -> metodoPagoSeleccionado.lowercase(Locale.ROOT)
                        }
                        put("metodo", metodoClean)
                        put("monto", totalCalculado)

                        if (metodoPagoSeleccionado == "Transferencia" || metodoPagoSeleccionado == "QR") {
                            put("referencia", etReferencia.text.toString().trim())
                        }

                        if (metodoPagoSeleccionado == "Fiado") {
                            put("modalidad", "sin_fecha")
                            put("fecha_vencimiento", null as String?)
                        }
                    })
                }

                // 3. Parámetros exactos esperados por la función RPC registrar_venta en Supabase
                val params = buildJsonObject {
                    put("p_negocio", negocioId)
                    put("p_uuid_dispositivo", uuidDispositivo)
                    put("p_fecha_dispositivo", fechaDispositivo)
                    put("p_cliente", if (metodoPagoSeleccionado == "Fiado") clienteSeleccionadoId else null)
                    put("p_nota_preimpresa", null as String?)
                    put("p_identificacion_comprador", null as String?)
                    put("p_descuento", descuento)
                    put("p_descuento_motivo", "Regateo")
                    put("p_items", itemsJson)
                    put("p_pagos", pagosJson)
                }

                // 4. Llamada RPC a Supabase
                SupabaseModule.client.postgrest.rpc("registrar_venta", params)

                Toast.makeText(requireContext(), "¡Venta registrada con éxito!", Toast.LENGTH_LONG).show()

                // Notificar a VentaFragment para limpiar el carrito
                parentFragmentManager.setFragmentResult(
                    "request_venta_exitosa",
                    bundleOf("exito" to true)
                )

                dismiss()

            } catch (e: Exception) {
                Log.e("CobroBottomSheet", "Error al registrar venta", e)
                btnConfirmarVenta.isEnabled = true
                Toast.makeText(requireContext(), "Error al registrar venta: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }
}
