package com.example.dajho

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.util.Locale

data class VarianteInsertDto(
    val sku: String,
    val codigo_barras: String?,
    val talla: String?,
    val color: String?,
    val precio_costo: Double,
    val precio_venta: Double,
    val precio_minimo: Double?,
    val stock_actual: Int,
    val stock_minimo: Int?
)

class NuevoProductoBottomSheet : BottomSheetDialogFragment() {

    private lateinit var etNombreProducto: TextInputEditText
    private lateinit var etMarcaProducto: TextInputEditText
    private lateinit var actCategoriaProducto: AutoCompleteTextView
    private lateinit var actTemporadaProducto: AutoCompleteTextView
    private lateinit var rvVariantes: RecyclerView
    private lateinit var btnAgregarVariante: MaterialButton
    private lateinit var btnCrearProducto: MaterialButton

    private val listaVariantesInput = mutableListOf<VarianteInputDto>()
    private lateinit var varianteInputAdapter: VarianteInputAdapter
    private var userNegocioId: Long? = null

    private var categoriasList: List<JsonObject> = emptyList()
    private var temporadasList: List<JsonObject> = emptyList()
    private var selectedCategoriaId: Long? = null
    private var selectedTemporadaId: Long? = null

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
        actCategoriaProducto = view.findViewById(R.id.actCategoriaProducto)
        actTemporadaProducto = view.findViewById(R.id.actTemporadaProducto)
        rvVariantes = view.findViewById(R.id.rvVariantes)
        btnAgregarVariante = view.findViewById(R.id.btnAgregarVariante)
        btnCrearProducto = view.findViewById(R.id.btnCrearProducto)

        if (listaVariantesInput.isEmpty()) {
            listaVariantesInput.add(VarianteInputDto())
        }

        varianteInputAdapter = VarianteInputAdapter(
            listaVariantesInput,
            getNombreProducto = { etNombreProducto.text.toString().trim() }
        ) { position ->
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
            val nombre = etNombreProducto.text.toString().trim()
            val nueva = VarianteInputDto()
            if (nombre.isNotEmpty()) {
                nueva.sku = SkuGenerator.generate(nombre)
            }
            listaVariantesInput.add(nueva)
            varianteInputAdapter.notifyItemInserted(listaVariantesInput.size - 1)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val negocioId = getNegocioId()
                userNegocioId = negocioId
                if (negocioId != null) {
                    cargarCategoriasYTemporadas(negocioId)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show()
            }
        }

        btnCrearProducto.setOnClickListener {
            crearProductoYVariantes()
        }
    }

    private suspend fun cargarCategoriasYTemporadas(negocioId: Long) {
        try {
            val catPage = SupabaseModule.client.postgrest.rpc("listar_gestion", buildJsonObject {
                put("p_negocio", negocioId)
                put("p_modulo", "categoria")
            }).decodeAs<JsonArray>().map { it.jsonObject }
            categoriasList = catPage

            val tempPage = SupabaseModule.client.postgrest.rpc("listar_gestion", buildJsonObject {
                put("p_negocio", negocioId)
                put("p_modulo", "temporada")
            }).decodeAs<JsonArray>().map { it.jsonObject }
            temporadasList = tempPage

            if (isAdded) {
                val catNombres = listOf("Sin categoría") + categoriasList.map { it.str("nombre") }
                val catAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, catNombres)
                actCategoriaProducto.setAdapter(catAdapter)
                actCategoriaProducto.setOnItemClickListener { _, _, position, _ ->
                    selectedCategoriaId = if (position == 0) null else categoriasList.getOrNull(position - 1)?.str("id")?.toLongOrNull()
                }

                val tempNombres = listOf("Sin temporada") + temporadasList.map { it.str("nombre") }
                val tempAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, tempNombres)
                actTemporadaProducto.setAdapter(tempAdapter)
                actTemporadaProducto.setOnItemClickListener { _, _, position, _ ->
                    selectedTemporadaId = if (position == 0) null else temporadasList.getOrNull(position - 1)?.str("id")?.toLongOrNull()
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("NuevoProducto", "Error al cargar listas: ${e.message}")
        }
    }

    private suspend fun getNegocioId(): Long? = BusinessSession.current()?.negocio_id

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
            var sku = v.sku.trim()
            val codigoBarras = v.codigoBarras.trim().takeIf { it.isNotEmpty() }
            val precioVentaTexto = v.precioVenta.trim()
            val precioCostoTexto = v.precioCosto.trim()
            val precioMinimoTexto = v.precioMinimo.trim()
            val stockTexto = v.stock.trim()
            val stockMinimoTexto = v.stockMinimo.trim()

            if (precioVentaTexto.isEmpty() || stockTexto.isEmpty()) {
                Toast.makeText(requireContext(), "Completa los campos obligatorios de la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            if (sku.isEmpty()) {
                sku = SkuGenerator.generate(nombre, v.talla, v.color)
            }

            if (!skus.add(sku.uppercase(Locale.ROOT))) {
                sku = SkuGenerator.generate(nombre, v.talla, v.color)
                skus.add(sku.uppercase(Locale.ROOT))
            }

            val precioVenta = precioVentaTexto.toDoubleOrNull()
            val precioCosto = if (precioCostoTexto.isEmpty()) null else precioCostoTexto.toDoubleOrNull()
            val precioMinimo = if (precioMinimoTexto.isEmpty()) null else precioMinimoTexto.toDoubleOrNull()
            val stock = stockTexto.toIntOrNull()
            val stockMinimo = if (stockMinimoTexto.isEmpty()) 0 else stockMinimoTexto.toIntOrNull()

            if (precioVenta == null || !precioVenta.isFinite() || precioVenta <= 0.0 ||
                precioCosto == null || !precioCosto.isFinite() || precioCosto <= 0.0 ||
                stock == null || stock < 0
            ) {
                Toast.makeText(requireContext(), "Precios o stock inválidos en la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            if (precioVenta < precioCosto) {
                Toast.makeText(requireContext(), "El precio de venta no puede ser menor al costo en la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            if (precioMinimo != null && (precioMinimo <= 0 || precioMinimo > precioVenta)) {
                Toast.makeText(requireContext(), "El precio mínimo debe ser mayor a 0 y menor o igual al precio de venta en la Variante #$numero", Toast.LENGTH_SHORT).show()
                return
            }

            variantesAInsertar.add(
                VarianteInsertDto(
                    sku = sku,
                    codigo_barras = codigoBarras,
                    talla = v.talla.trim().takeIf { it.isNotEmpty() },
                    color = v.color.trim().takeIf { it.isNotEmpty() },
                    precio_costo = precioCosto,
                    precio_venta = precioVenta,
                    precio_minimo = precioMinimo,
                    stock_actual = stock,
                    stock_minimo = stockMinimo
                )
            )
        }

        btnCrearProducto.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
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
                    put("p_categoria", selectedCategoriaId)
                    put("p_temporada", selectedTemporadaId)
                    put("p_variantes", buildJsonArray {
                        variantesAInsertar.forEach { variante ->
                            add(buildJsonObject {
                                put("sku", variante.sku)
                                put("codigo_barras", variante.codigo_barras)
                                put("talla", variante.talla)
                                put("color", variante.color)
                                put("precio_costo", variante.precio_costo)
                                put("precio_venta", variante.precio_venta)
                                put("precio_minimo", variante.precio_minimo)
                                put("stock_actual", variante.stock_actual)
                                put("stock_minimo", variante.stock_minimo)
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
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("NuevoProducto", "Error al crear producto" + ": " + e.userMessage())
                Toast.makeText(requireContext(), e.userMessage() ?: "No se pudo guardar el producto", Toast.LENGTH_LONG).show()
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
