package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.Locale

class EditarProductoBottomSheet() : BottomSheetDialogFragment() {
    constructor(variante: VarianteDto) : this() {
        arguments = android.os.Bundle().apply {
            putString("variante", ScreenArgs.json.encodeToString(variante))
        }
    }
    private val variante: VarianteDto get() = ScreenArgs.json.decodeFromString(requireArguments().getString("variante")!!)


    private var stockEsperado: Int? = null
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
        (btnGuardarCambios.parent as android.view.ViewGroup).addView(MaterialButton(requireContext()).apply {
            text = "Nombre, marca, categoría y temporada"
            setOnClickListener {
                variante.producto_id?.let { id -> editProduct(id) {
                    parentFragmentManager.setFragmentResult("request_inventario_actualizado", Bundle.EMPTY)
                    dismiss()
                } }
            }
        })

        // Pre-llenar datos
        val nombre = variante.producto?.nombre ?: "Producto"
        val talla = variante.talla ?: "-"
        val color = variante.color ?: "-"
        tvProductoNombre.text = "$nombre ($talla / $color)"
        tvSku.text = "SKU: ${variante.sku ?: "Sin SKU"}"

        etStock.setText("")
        btnGuardarCambios.isEnabled = false
        etPrecioVenta.setText(String.format(Locale.US, "%.2f", variante.precio_venta ?: 0.0))
        etPrecioCosto.setText(String.format(Locale.US, "%.2f", variante.precio_costo ?: 0.0))

        btnGuardarCambios.setOnClickListener {
            guardarCambios()
        }
        cargarStockActual()
    }

    private fun cargarStockActual() {
        val varianteId = variante.id
        if (varianteId == null) {
            etStock.error = "No se pudo identificar la variante"
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val actual = SupabaseModule.client.from("variante")
                    .select(Columns.raw("id,stock_actual")) {
                        filter {
                            eq("id", varianteId)
                        }
                    }
                    .decodeSingle<VarianteDto>()

                val stock = actual.stock_actual
                if (stock == null) {
                    etStock.error = "La variante no tiene stock registrado"
                    return@launch
                }

                stockEsperado = stock
                etStock.setText(stock.toString())
                btnGuardarCambios.isEnabled = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("EditarProducto", "Error al consultar stock" + ": " + e.userMessage())
                Toast.makeText(requireContext(), "No se pudo consultar el stock actual", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun guardarCambios() {
        val stockStr = etStock.text.toString().trim()
        val precioVentaStr = etPrecioVenta.text.toString().trim()
        val precioCostoStr = etPrecioCosto.text.toString().trim()

        val nuevoStock = stockStr.toIntOrNull()
        val nuevoPrecioVenta = precioVentaStr.toDoubleOrNull()
        val nuevoPrecioCosto = precioCostoStr.toDoubleOrNull()

        if (nuevoStock == null || nuevoStock < 0 || nuevoPrecioVenta == null || !nuevoPrecioVenta.isFinite() || nuevoPrecioCosto == null || !nuevoPrecioCosto.isFinite() || nuevoPrecioCosto <= 0 || nuevoPrecioVenta < nuevoPrecioCosto) {
            Toast.makeText(requireContext(), "Por favor ingresa valores válidos", Toast.LENGTH_SHORT).show()
            return
        }

        val varianteId = variante.id
        if (varianteId == null) {
            Toast.makeText(requireContext(), "Error: ID de producto no válido", Toast.LENGTH_SHORT).show()
            return
        }

        btnGuardarCambios.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Actualizar variante localmente (Offline First)
                val negocio = BusinessSession.requireId(requireContext())
                val payload = buildJsonObject {
                    put("p_negocio", negocio)
                    put("p_variante", varianteId)
                    put("p_stock_esperado", requireNotNull(stockEsperado))
                    put("p_stock", nuevoStock)
                    put("p_costo", Money.json(Money.amount(precioCostoStr)))
                    put("p_precio", Money.json(Money.amount(precioVentaStr)))
                    put("p_motivo", "Conteo físico desde inventario")
                }

                // Actualizar stock local si se modificó
                val db = DajhoDatabase.getInstance(requireContext())
                val diff = (stockEsperado ?: 0) - nuevoStock
                if (diff != 0) {
                    db.updateStockLocally(negocio, varianteId, diff)
                }

                SyncManager.enqueueAndTrySync(requireContext(), negocio, "editar_variante", payload)

                Toast.makeText(requireContext(), "¡Producto actualizado con éxito!", Toast.LENGTH_SHORT).show()

                // Notificar al InventarioFragment para refrescar la lista
                parentFragmentManager.setFragmentResult(
                    "request_inventario_actualizado",
                    bundleOf("actualizado" to true)
                )

                dismiss()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("EditarProducto", "Error al actualizar producto" + ": " + e.userMessage())
                btnGuardarCambios.isEnabled = true
                Toast.makeText(requireContext(), "Error al actualizar: ${e.userMessage()}", Toast.LENGTH_LONG).show()
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
