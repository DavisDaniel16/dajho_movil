package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
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
                    Toast.makeText(requireContext(), "Límite de stock alcanzado", Toast.LENGTH_SHORT).show()
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
            userNegocioId?.let { if (PendingSales.get(requireContext(), it) != null) {
                btnCobrar.text = "RECUPERAR COBRO PENDIENTE"
            } }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show()
            }
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
            val pending = userNegocioId?.let { PendingSales.get(requireContext(), it) }
            if (pending != null) {
                CobroBottomSheet(0.0, emptyList()).show(parentFragmentManager, "CobroBottomSheet")
            } else if (listaCarrito.isEmpty()) {
                Toast.makeText(requireContext(), "El carrito está vacío", Toast.LENGTH_SHORT).show()
            } else {
                val cobroBottomSheet = CobroBottomSheet(total, listaCarrito.toList())
                cobroBottomSheet.show(parentFragmentManager, "CobroBottomSheet")
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

    private fun cargarProductosIniciales() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId() ?: return@launch

                val variantes = CatalogRepository.load(negocioId)

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
            Toast.makeText(requireContext(), "No hay stock disponible de este producto", Toast.LENGTH_SHORT).show()
            return
        }

        val existente = listaCarrito.find { it.variante.id == variante.id }
        val cantidadEnCarrito = existente?.cantidad ?: 0
        if (cantidadEnCarrito >= stockDisponible) {
            Toast.makeText(requireContext(), "No puedes agregar más unidades. Límite de stock alcanzado", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(requireContext(), "Producto agregado al carrito", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                    return@coroutineScope
                }

                val variantes = CatalogRepository.load(negocioId, query)

                productoAdapter.updateList(variantes)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("VentaFragment", "Error buscando productos" + ": " + e.userMessage())
                Toast.makeText(requireContext(), "Error al buscar: ${e.userMessage()}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
