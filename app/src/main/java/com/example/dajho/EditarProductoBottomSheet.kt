package com.example.dajho

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.util.Locale

class EditarProductoBottomSheet(
    private val variante: VarianteDto
) : BottomSheetDialogFragment() {

    private lateinit var tvProductoNombre: TextView
    private lateinit var tvSku: TextView
    private lateinit var etStock: TextInputEditText
    private lateinit var etPrecioVenta: TextInputEditText
    private lateinit var etPrecioCosto: TextInputEditText
    private lateinit var btnGuardarCambios: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_editar_producto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvProductoNombre = view.findViewById(R.id.tvProductoNombre)
        tvSku = view.findViewById(R.id.tvSku)
        etStock = view.findViewById(R.id.etStock)
        etPrecioVenta = view.findViewById(R.id.etPrecioVenta)
        etPrecioCosto = view.findViewById(R.id.etPrecioCosto)
        btnGuardarCambios = view.findViewById(R.id.btnGuardarCambios)

        // Pre-llenar datos
        val nombre = variante.producto?.nombre ?: "Producto"
        val talla = variante.talla ?: "-"
        val color = variante.color ?: "-"
        tvProductoNombre.text = "$nombre ($talla / $color)"
        tvSku.text = "SKU: ${variante.sku ?: "Sin SKU"}"

        etStock.setText(variante.stock_actual?.toString() ?: "0")
        etPrecioVenta.setText(String.format(Locale.US, "%.2f", variante.precio_venta ?: 0.0))
        etPrecioCosto.setText(String.format(Locale.US, "%.2f", variante.precio_costo ?: 0.0))

        btnGuardarCambios.setOnClickListener {
            guardarCambios()
        }
    }

    private fun guardarCambios() {
        val stockStr = etStock.text.toString().trim()
        val precioVentaStr = etPrecioVenta.text.toString().trim()
        val precioCostoStr = etPrecioCosto.text.toString().trim()

        val nuevoStock = stockStr.toIntOrNull()
        val nuevoPrecioVenta = precioVentaStr.toDoubleOrNull()
        val nuevoPrecioCosto = precioCostoStr.toDoubleOrNull()

        if (nuevoStock == null || nuevoPrecioVenta == null || nuevoPrecioCosto == null) {
            Toast.makeText(requireContext(), "Por favor ingresa valores válidos", Toast.LENGTH_SHORT).show()
            return
        }

        val varianteId = variante.id
        if (varianteId == null) {
            Toast.makeText(requireContext(), "Error: ID de producto no válido", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardarCambios.isEnabled = false

        lifecycleScope.launch {
            try {
                // Actualizar variante en Supabase
                SupabaseModule.client.from("variante").update(
                    {
                        set("stock_actual", nuevoStock)
                        set("precio_venta", nuevoPrecioVenta)
                        set("precio_costo", nuevoPrecioCosto)
                    }
                ) {
                    filter {
                        eq("id", varianteId)
                    }
                }

                Toast.makeText(requireContext(), "¡Producto actualizado con éxito!", Toast.LENGTH_SHORT).show()

                // Notificar al InventarioFragment para refrescar la lista
                parentFragmentManager.setFragmentResult(
                    "request_inventario_actualizado",
                    bundleOf("actualizado" to true)
                )

                dismiss()

            } catch (e: Exception) {
                Log.e("EditarProducto", "Error al actualizar producto", e)
                btnGuardarCambios.isEnabled = true
                Toast.makeText(requireContext(), "Error al actualizar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
