package com.example.dajho

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

data class VarianteInsertDto(
    val sku: String,
    val talla: String?,
    val color: String?,
    val precio_costo: Double,
    val precio_venta: Double,
    val stock_actual: Int
)

class NuevoProductoBottomSheet : BottomSheetDialogFragment() {

    private lateinit var etNombreProducto: TextInputEditText
    private lateinit var etMarcaProducto: TextInputEditText
    private lateinit var rvVariantes: RecyclerView
    private lateinit var btnAgregarVariante: MaterialButton
    private lateinit var btnCrearProducto: MaterialButton

    private val listaVariantesInput = mutableListOf<VarianteInputDto>()
    private lateinit var varianteInputAdapter: VarianteInputAdapter
    private var userNegocioId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_nuevo_producto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etNombreProducto = view.findViewById(R.id.etNombreProducto)
        etMarcaProducto = view.findViewById(R.id.etMarcaProducto)
        rvVariantes = view.findViewById(R.id.rvVariantes)
        btnAgregarVariante = view.findViewById(R.id.btnAgregarVariante)
        btnCrearProducto = view.findViewById(R.id.btnCrearProducto)

        if (listaVariantesInput.isEmpty()) {
            listaVariantesInput.add(VarianteInputDto())
        }

        varianteInputAdapter = VarianteInputAdapter(listaVariantesInput) { position ->
            if (listaVariantesInput.size > 1) {
                listaVariantesInput.removeAt(position)
                varianteInputAdapter.notifyItemRemoved(position)
                varianteInputAdapter.notifyItemRangeChanged(position, listaVariantesInput.size)
            } else {
                Toast.makeText(requireContext(), "Debe haber al menos una variante", Toast.LENGTH_SHORT).show()
            }
        }

        rvVariantes.layoutManager = LinearLayoutManager(requireContext())
        rvVariantes.adapter = varianteInputAdapter

        btnAgregarVariante.setOnClickListener {
            listaVariantesInput.add(VarianteInputDto())
            varianteInputAdapter.notifyItemInserted(listaVariantesInput.size - 1)
        }

        lifecycleScope.launch {
            userNegocioId = getNegocioId()
        }

        btnCrearProducto.setOnClickListener {
            crearProductoYVariantes()
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
            Log.e("NuevoProducto", "Error al obtener negocio_id", e)
            null
        }
    }

    private fun crearProductoYVariantes() {
        val nombre = etNombreProducto.text.toString().trim()
        val marca = etMarcaProducto.text.toString().trim()

        if (nombre.isEmpty()) {
            Toast.makeText(requireContext(), "Por favor ingresa el nombre del producto", Toast.LENGTH_SHORT).show()
            return
        }

        val skus = mutableSetOf<String>()
        val variantesAInsertar = mutableListOf<VarianteInsertDto>()

        for ((index, v) in listaVariantesInput.withIndex()) {
            val numero = index + 1
            val sku = v.sku.trim()
            val precioVentaTexto = v.precioVenta.trim()
            val precioCostoTexto = v.precioCosto.trim()
            val stockTexto = v.stock.trim()

            if (sku.isEmpty() || precioVentaTexto.isEmpty() || stockTexto.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios de la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            if (!skus.add(sku.uppercase(Locale.ROOT))) {
                Toast.makeText(requireContext(), "El SKU $sku está repetido en el formulario", Toast.LENGTH_SHORT).show()
                return
            }

            val precioVenta = precioVentaTexto.toDoubleOrNull()
            val precioCosto = if (precioCostoTexto.isEmpty()) 0.0 else precioCostoTexto.toDoubleOrNull()
            val stock = stockTexto.toIntOrNull()

            if (precioVenta == null || !precioVenta.isFinite() || precioVenta < 0.0 ||
                precioCosto == null || !precioCosto.isFinite() || precioCosto < 0.0 ||
                stock == null || stock < 0
            ) {
                Toast.makeText(requireContext(), "Precios o stock inválidos en la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            if (precioVenta < precioCosto) {
                Toast.makeText(requireContext(), "El precio de venta no puede ser menor al costo en la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            variantesAInsertar.add(
                VarianteInsertDto(
                    sku = sku,
                    talla = v.talla.trim().takeIf { it.isNotEmpty() },
                    color = v.color.trim().takeIf { it.isNotEmpty() },
                    precio_costo = precioCosto,
                    precio_venta = precioVenta,
                    stock_actual = stock
                )
            )
        }

        btnCrearProducto.isEnabled = false

        lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val params = buildJsonObject {
                    put("p_negocio", negocioId)
                    put("p_nombre", nombre)
                    put("p_marca", marca.takeIf { it.isNotEmpty() })
                    put("p_variantes", buildJsonArray {
                        variantesAInsertar.forEach { variante ->
                            add(buildJsonObject {
                                put("sku", variante.sku)
                                put("talla", variante.talla)
                                put("color", variante.color)
                                put("precio_costo", variante.precio_costo)
                                put("precio_venta", variante.precio_venta)
                                put("stock_actual", variante.stock_actual)
                            })
                        }
                    })
                }

                SupabaseModule.client.postgrest.rpc("crear_producto_con_variantes", params)

                Toast.makeText(requireContext(), "¡Producto y variantes creados con éxito!", Toast.LENGTH_SHORT).show()
                parentFragmentManager.setFragmentResult(
                    "request_inventario_actualizado",
                    bundleOf("actualizado" to true)
                )
                dismiss()
            } catch (e: Exception) {
                Log.e("NuevoProducto", "Error al crear producto", e)
                Toast.makeText(requireContext(), "No se pudo guardar el producto", Toast.LENGTH_LONG).show()
            } finally {
                btnCrearProducto.isEnabled = true
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
