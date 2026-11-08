package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class VentaFragment : Fragment() {

    private lateinit var etSearch: TextInputEditText
    private lateinit var rvCarrito: RecyclerView
    private lateinit var tvTotal: TextView
    private lateinit var btnCobrar: MaterialButton
    private lateinit var btnVerCarrito: MaterialButton

    private lateinit var productoAdapter: ProductoAdapter
    private lateinit var carritoAdapter: CarritoAdapter

    private val listaCarrito = mutableListOf<ItemCarritoDto>()
    private var searchJob: Job? = null
    private var userNegocioId: Long? = null
    private var viendoCarrito: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_venta, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.etSearch)
        rvCarrito = view.findViewById(R.id.rvCarrito)
        tvTotal = view.findViewById(R.id.tvTotal)
        btnCobrar = view.findViewById(R.id.btnCobrar)
        btnVerCarrito = view.findViewById(R.id.btnVerCarrito)

        savedInstanceState?.getString("carrito")?.let {
            listaCarrito.clear(); listaCarrito.addAll(ScreenArgs.json.decodeFromString<List<ItemCarritoDto>>(it))
        }
        // Adaptadores
        productoAdapter = ProductoAdapter { variante ->
            agregarAlCarrito(variante)
        }

        carritoAdapter = CarritoAdapter(
            onCantidadChanged = { item, nuevaCantidad ->
                val stockDisponible = item.variante.stock_actual
                if (nuevaCantidad > item.cantidad &&
                    (stockDisponible == null || nuevaCantidad > stockDisponible)
                ) {
                    AppFeedback.makeText(requireContext(), "Límite de stock alcanzado", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                } else {
                    item.cantidad = nuevaCantidad
                    carritoAdapter.updateList(listaCarrito.toList())
                    actualizarTotal()
                    actualizarBotonCarrito()
                }
            },
            onItemRemoved = { item ->
                listaCarrito.remove(item)
                carritoAdapter.updateList(listaCarrito.toList())
                actualizarTotal()
                actualizarBotonCarrito()
            }
        )

        rvCarrito.layoutManager = LinearLayoutManager(requireContext())
        rvCarrito.adapter = productoAdapter // INICIAR MOSTRANDO EL CATÁLOGO DE PRODUCTOS

        // Pre-cargar el negocio_id y cargar los productos iniciales
        viewLifecycleOwner.lifecycleScope.launch {
            try {
            userNegocioId = getNegocioId()
            cargarProductosIniciales()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                AppFeedback.makeText(context, e.userMessage("buscar los productos"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
            }
        }

        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ ->
            btnCobrar.text = "COBRAR"
            if (etSearch.text.isNullOrBlank()) cargarProductosIniciales() else searchProductsAfterReconnect()
        }

        // Escuchar evento de venta exitosa para vaciar el carrito
        parentFragmentManager.setFragmentResultListener("request_venta_exitosa", viewLifecycleOwner) { _, _ ->
            limpiarCarrito()
        }

        // Botón Ver Carrito / Seguir Comprando
        btnVerCarrito.setOnClickListener {
            viendoCarrito = !viendoCarrito
            if (viendoCarrito) {
                rvCarrito.adapter = carritoAdapter
                btnVerCarrito.text = "Seguir Comprando"
            } else {
                rvCarrito.adapter = productoAdapter
                actualizarBotonCarrito()
            }
        }

        // Búsqueda en tiempo real
        etSearch.doAfterTextChanged { text ->
            val query = text.toString().trim()
            searchJob?.cancel()
            if (query.isNotEmpty()) {
                if (viendoCarrito) {
                    viendoCarrito = false
                    rvCarrito.adapter = productoAdapter
                    actualizarBotonCarrito()
                }
                searchJob = viewLifecycleOwner.lifecycleScope.launch {
                    delay(300)
                    searchProducts(query)
                }
            } else {
                if (!viendoCarrito) {
                    cargarProductosIniciales()
                }
            }
        }

        btnCobrar.setOnClickListener {
            val total = calcularTotal()
            if (listaCarrito.isEmpty()) {
                AppFeedback.makeText(requireContext(), "El carrito está vacío", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            } else {
                val negocioId = userNegocioId
                if (negocioId != null && !DajhoDatabase.getInstance(requireContext()).isCajaAbierta(negocioId)) {
                    com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Caja Cerrada")
                        .setMessage("Debes abrir la caja desde el módulo de Gestión para poder registrar ventas.")
                        .setPositiveButton("Ir a Gestión") { _, _ ->
                            (activity as? HomeActivity)?.replaceFragment(ManagementFragment.newInstance("caja"))
                        }
                        .setNegativeButton("Cancelar", null)
                        .show()
                } else {
                    val cobroBottomSheet = CobroBottomSheet(total, listaCarrito.toList())
                    cobroBottomSheet.show(parentFragmentManager, "CobroBottomSheet")
                }
            }
        }

        carritoAdapter.updateList(listaCarrito.toList())
        actualizarTotal()
        actualizarBotonCarrito()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("carrito", ScreenArgs.json.encodeToString(listaCarrito.toList()))
        super.onSaveInstanceState(outState)
    }

    private fun searchProductsAfterReconnect() {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch { searchProducts(etSearch.text.toString().trim()) }
    }

    private fun cargarProductosIniciales() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId() ?: return@launch

                val variantes = CatalogRepository.load(negocioId, context = requireContext())

                productoAdapter.updateList(variantes)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("VentaFragment", "Error al cargar productos iniciales" + ": " + e.userMessage())
            }
        }
    }

    private fun agregarAlCarrito(variante: VarianteDto) {
        val stockDisponible = variante.stock_actual
        if (stockDisponible == null || stockDisponible <= 0) {
            AppFeedback.makeText(requireContext(), "No hay stock disponible de este producto", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            return
        }

        val existente = listaCarrito.find { it.variante.id == variante.id }
        val cantidadEnCarrito = existente?.cantidad ?: 0
        if (cantidadEnCarrito >= stockDisponible) {
            AppFeedback.makeText(requireContext(), "No puedes agregar más unidades. Límite de stock alcanzado", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            return
        }

        if (existente != null) {
            existente.cantidad += 1
        } else {
            listaCarrito.add(ItemCarritoDto(variante, cantidad = 1))
        }

        // Actualizamos el carrito y el botón SIN cambiar la vista de productos
        carritoAdapter.updateList(listaCarrito.toList())
        actualizarTotal()
        actualizarBotonCarrito()
        AppFeedback.makeText(requireContext(), "Producto agregado al carrito", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.SUCCESS).show()
    }

    private fun limpiarCarrito() {
        btnCobrar.text = "COBRAR"
        listaCarrito.clear()
        cargarProductosIniciales()
        carritoAdapter.updateList(emptyList())
        etSearch.setText("")
        viendoCarrito = false
        rvCarrito.adapter = productoAdapter
        actualizarTotal()
        actualizarBotonCarrito()
    }

    private fun calcularTotal(): Double {
        return Money.total(listaCarrito).toDouble()
    }

    private fun actualizarTotal() {
        val total = calcularTotal()
        tvTotal.text = "$${String.format(Locale.US, "%.2f", total)}"
    }

    private fun actualizarBotonCarrito() {
        val totalItems = listaCarrito.sumOf { it.cantidad }
        if (viendoCarrito) {
            btnVerCarrito.text = "Seguir Comprando"
        } else {
            btnVerCarrito.text = "Ver Carrito ($totalItems)"
        }
    }

    private suspend fun getNegocioId(): Long? = BusinessSession.current()?.negocio_id

    private suspend fun searchProducts(query: String) {
        kotlinx.coroutines.coroutineScope {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    AppFeedback.makeText(requireContext(), "No se pudo identificar tu negocio. Vuelve a Inicio y comprueba tu acceso.", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                    return@coroutineScope
                }

                val variantes = CatalogRepository.load(negocioId, query, context = requireContext())

                productoAdapter.updateList(variantes)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("VentaFragment", "Error buscando productos" + ": " + e.userMessage())
                AppFeedback.makeText(requireContext(), e.userMessage("buscar los productos"), AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            }
        }
    }
}
