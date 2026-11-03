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
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class ProductoInsertDto(
    val nombre: String,
    val marca: String? = null,
    val negocio_id: Long
)

@Serializable
data class ProductoResultDto(
    val id: Long? = null
)

@Serializable
data class VarianteInsertDto(
    val producto_id: Long,
    val negocio_id: Long,
    val sku: String? = null,
    val talla: String? = null,
    val color: String? = null,
    val precio_costo: Double? = null,
    val precio_venta: Double? = null,
    val stock_actual: Int = 0
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

        val variantesAInsertar = mutableListOf<VarianteInsertDto>()
        for ((index, v) in listaVariantesInput.withIndex()) {
            val sku = v.sku.trim()
            val precioVentaStr = v.precioVenta.trim()
            val stockStr = v.stock.trim()

            if (sku.isEmpty() || precioVentaStr.isEmpty() || stockStr.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios de la Variante #${index + 1}", Toast.LENGTH_SHORT).show()
                return
            }

            val precioVenta = precioVentaStr.toDoubleOrNull() ?: 0.0
            val precioCosto = v.precioCosto.trim().toDoubleOrNull() ?: 0.0
            val stock = stockStr.toIntOrNull() ?: 0

            variantesAInsertar.add(
                VarianteInsertDto(
                    producto_id = 0L,
                    negocio_id = 0L,
                    sku = sku,
                    talla = if (v.talla.trim().isNotEmpty()) v.talla.trim() else null,
                    color = if (v.color.trim().isNotEmpty()) v.color.trim() else null,
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
                    btnCrearProducto.isEnabled = true
                    return@launch
                }

                // 1. Insertar Producto (1 vez)
                val nuevoProducto = ProductoInsertDto(
                    nombre = nombre,
                    marca = if (marca.isNotEmpty()) marca else null,
                    negocio_id = negocioId
                )

                val productoCreado = SupabaseModule.client.from("producto")
                    .insert(nuevoProducto) {
                        select()
                    }
                    .decodeSingle<ProductoResultDto>()

                val productoId = productoCreado.id
                if (productoId == null) {
                    Toast.makeText(requireContext(), "Error al obtener ID del producto creado", Toast.LENGTH_SHORT).show()
                    btnCrearProducto.isEnabled = true
                    return@launch
                }

                // 2. Insertar variantes en lote (batch insert)
                val listaFinalVariantes = variantesAInsertar.map {
                    it.copy(producto_id = productoId, negocio_id = negocioId)
                }

                SupabaseModule.client.from("variante").insert(listaFinalVariantes)

                Toast.makeText(requireContext(), "¡Producto y variantes creados con éxito!", Toast.LENGTH_SHORT).show()

                parentFragmentManager.setFragmentResult(
                    "request_inventario_actualizado",
                    bundleOf("actualizado" to true)
                )

                dismiss()

            } catch (e: Exception) {
                Log.e("NuevoProducto", "Error al crear producto", e)
                btnCrearProducto.isEnabled = true
                Toast.makeText(requireContext(), "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
