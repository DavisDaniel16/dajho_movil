package com.example.dajho

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.Locale

class DetalleVentaBottomSheet(
    private val tituloVenta: String,
    private val detalles: List<DetalleVentaBalanceDto>
) : BottomSheetDialogFragment() {

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
