package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class DetalleVentaBottomSheet() : BottomSheetDialogFragment() {
    constructor(tituloVenta: String, detalles: List<DetalleVentaBalanceDto>) : this() {
        arguments = android.os.Bundle().apply {
            putString("tituloVenta", ScreenArgs.json.encodeToString(tituloVenta))
            putString("detalles", ScreenArgs.json.encodeToString(detalles))
        }
    }
    private val tituloVenta: String get() = ScreenArgs.json.decodeFromString(requireArguments().getString("tituloVenta")!!)
    private val detalles: List<DetalleVentaBalanceDto> get() = ScreenArgs.json.decodeFromString(requireArguments().getString("detalles")!!)


    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_detalle_venta, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvTituloDetalleVenta).text = tituloVenta
        val lista = view.findViewById<LinearLayout>(R.id.layoutDetallesVenta)
        val receiptLines = detalles.map { "${it.variante?.producto?.nombre ?: "Producto"} · ${it.cantidad} × $${it.precio_unitario}" }
        lista.addView(com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "Imprimir comprobante"
            setOnClickListener { ReceiptPrinter.print(requireActivity(), tituloVenta, receiptLines) }
        })
        lista.addView(com.google.android.material.button.MaterialButton(requireContext()).apply {
            text = "Compartir comprobante"
            setOnClickListener {
                startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, (listOf(tituloVenta, "DAJHO · Comprobante interno") + receiptLines).joinToString("\n"))
                }, "Compartir"))
            }
        })

        if (detalles.isEmpty()) {
            lista.addView(TextView(requireContext()).apply {
                text = "Esta venta no tiene productos registrados"
                textSize = 14f
            })
            return
        }

        detalles.forEach { detalle ->
            val nombre = detalle.variante?.producto?.nombre ?: "Producto"
            val cantidad = detalle.cantidad ?: 0
            val precio = detalle.precio_unitario ?: 0.0

            lista.addView(TextView(requireContext()).apply {
                text = "$nombre · $cantidad × $${String.format(Locale.US, "%.2f", precio)}"
                textSize = 14f
                setPadding(0, 12, 0, 12)
            })
        }
    }
}
