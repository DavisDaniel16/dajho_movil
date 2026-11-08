package com.example.dajho

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

open class VentaFragment : Fragment() {
    private lateinit var etSearch: TextInputEditText
    private lateinit var rvCarrito: RecyclerView
    private lateinit var productoAdapter: ProductoAdapter
    private lateinit var carritoAdapter: CarritoAdapter
    private val listaCarrito = mutableListOf<ItemCarritoDto>()
    private var searchJob: Job? = null
    private var userNegocioId: Long? = null
    private var viendoCarrito = false
    private var loading = false
    private var catalogError: String? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_venta, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        etSearch = view.findViewById(R.id.etSearch)
        rvCarrito = view.findViewById(R.id.rvCarrito)
        savedInstanceState?.getString("carrito")?.let {
            listaCarrito.clear()
            listaCarrito.addAll(ScreenArgs.json.decodeFromString<List<ItemCarritoDto>>(it))
        }
        viendoCarrito = savedInstanceState?.getBoolean("viendo_carrito") ?: viendoCarrito
        productoAdapter = ProductoAdapter { agregarAlCarrito(it) }
        carritoAdapter = CarritoAdapter(
            onCantidadChanged = { item, quantity ->
                if (quantity > item.cantidad && quantity > (item.variante.stock_actual ?: 0)) {
                    AppFeedback.makeText(requireContext(), "Límite de stock alcanzado", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                } else {
                    item.cantidad = quantity
                    actualizarCarrito()
                }
            },
            onItemRemoved = { item ->
                listaCarrito.remove(item)
                actualizarCarrito()
            }
        )
        rvCarrito.layoutManager = LinearLayoutManager(requireContext())
        val tabs = view.findViewById<MaterialButtonToggleGroup>(R.id.saleTabs)
        tabs.check(if (viendoCarrito) R.id.btnVerCarrito else R.id.btnProductos)
        tabs.addOnButtonCheckedListener { _, checkedId, checked ->
            if (checked) {
                viendoCarrito = checkedId == R.id.btnVerCarrito
                if (viendoCarrito) {
                    etSearch.clearFocus()
                    (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
                        .hideSoftInputFromWindow(view.windowToken, 0)
                }
                render()
            }
        }
        view.findViewById<View>(R.id.btnVaciarCarrito).setOnClickListener {
            if (parentFragmentManager.findFragmentByTag(ClearCartDialog.TAG) == null) {
                ClearCartDialog().showNow(parentFragmentManager, ClearCartDialog.TAG)
            }
        }
        parentFragmentManager.setFragmentResultListener(ClearCartDialog.RESULT, viewLifecycleOwner) { _, _ ->
            listaCarrito.clear()
            actualizarCarrito()
        }
        view.findViewById<View>(R.id.btnEmptyAction).setOnClickListener {
            if (viendoCarrito) tabs.check(R.id.btnProductos) else cargarProductos()
        }
        etSearch.doAfterTextChanged { cargarProductos(debounce = true) }
        view.findViewById<View>(R.id.btnCobrar).setOnClickListener { abrirCobro() }
        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ ->
            cargarProductos()
        }
        parentFragmentManager.setFragmentResultListener("request_venta_exitosa", viewLifecycleOwner) { _, _ ->
            listaCarrito.clear()
            etSearch.setText("")
            tabs.check(R.id.btnProductos)
            actualizarCarrito()
            cargarProductos()
        }
        actualizarCarrito()
        cargarProductos()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("carrito", ScreenArgs.json.encodeToString(listaCarrito.toList()))
        outState.putBoolean("viendo_carrito", viendoCarrito)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        searchJob?.cancel()
        rvCarrito.adapter = null
        super.onDestroyView()
    }

    private fun cargarProductos(debounce: Boolean = false) {
        searchJob?.cancel()
        searchJob = viewLifecycleOwner.lifecycleScope.launch {
            if (debounce) delay(300)
            loading = true
            catalogError = null
            render()
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                    ?: error("No se pudo identificar tu negocio. Vuelve a Inicio y comprueba tu acceso.")
                userNegocioId = negocioId
                productoAdapter.updateList(loadCatalog(negocioId, etSearch.text.toString().trim()))
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                catalogError = e.userMessage("cargar el catálogo")
                productoAdapter.updateList(emptyList())
            } finally {
                loading = false
            }
            render()
        }
    }

    private fun agregarAlCarrito(variante: VarianteDto) {
        val stock = variante.stock_actual ?: 0
        val existente = listaCarrito.find { it.variante.id == variante.id }
        if ((existente?.cantidad ?: 0) >= stock) {
            AppFeedback.makeText(requireContext(), "No puedes agregar más unidades. Límite de stock alcanzado",
                AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            return
        }
        if (existente != null) existente.cantidad++ else listaCarrito.add(ItemCarritoDto(variante))
        actualizarCarrito()
    }

    private fun actualizarCarrito() {
        carritoAdapter.updateList(listaCarrito.toList())
        productoAdapter.updateCart(listaCarrito)
        render()
    }

    private fun render() {
        val root = view ?: return
        val count = listaCarrito.sumOf { it.cantidad }
        root.findViewById<TextView>(R.id.tvTotal).text = "$" + Money.total(listaCarrito).toPlainString()
        root.findViewById<TextView>(R.id.tvSaleUnits).text = resources.getQuantityString(R.plurals.sale_units, count, count)
        root.findViewById<MaterialButton>(R.id.btnVerCarrito).text = getString(R.string.sale_cart_count, count)
        root.findViewById<View>(R.id.btnVaciarCarrito).isVisible = listaCarrito.isNotEmpty()
        root.findViewById<View>(R.id.btnCobrar).isEnabled = listaCarrito.isNotEmpty()
        root.findViewById<View>(R.id.tilSearch).isVisible = !viendoCarrito
        root.findViewById<TextView>(R.id.tvSaleHint).setText(
            if (viendoCarrito) R.string.sale_cart_hint else R.string.sale_catalog_hint
        )
        val adapter = if (viendoCarrito) carritoAdapter else productoAdapter
        if (rvCarrito.adapter !== adapter) rvCarrito.adapter = adapter
        val empty = if (viendoCarrito) listaCarrito.isEmpty()
            else loading || catalogError != null || productoAdapter.itemCount == 0
        rvCarrito.isVisible = !empty
        root.findViewById<View>(R.id.saleEmptyState).isVisible = empty
        root.findViewById<View>(R.id.saleLoading).isVisible = !viendoCarrito && loading
        val title = when {
            viendoCarrito -> R.string.sale_empty_title
            loading -> R.string.sale_loading
            catalogError != null -> R.string.sale_load_error
            !etSearch.text.isNullOrBlank() -> R.string.sale_no_results
            else -> R.string.sale_no_products
        }
        root.findViewById<TextView>(R.id.tvSaleEmptyTitle).setText(title)
        val body = root.findViewById<TextView>(R.id.tvSaleEmptyBody)
        body.isVisible = viendoCarrito || !loading
        body.text = when {
            viendoCarrito -> getString(R.string.sale_empty_body)
            catalogError != null -> catalogError
            !etSearch.text.isNullOrBlank() -> getString(R.string.sale_no_results_help)
            else -> getString(R.string.sale_no_products_help)
        }
        root.findViewById<MaterialButton>(R.id.btnEmptyAction).apply {
            isVisible = viendoCarrito || (!loading && (catalogError != null || etSearch.text.isNullOrBlank()))
            setText(if (viendoCarrito) R.string.sale_add_products else R.string.sale_retry)
        }
    }

    private fun abrirCobro() {
        if (listaCarrito.isEmpty()) return
        val negocioId = userNegocioId
        if (negocioId != null && !DajhoDatabase.getInstance(requireContext()).isCajaAbierta(negocioId)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Caja cerrada")
                .setMessage("Debes abrir la caja desde el módulo de Gestión para poder registrar ventas.")
                .setPositiveButton("Ir a Gestión") { _, _ ->
                    (activity as? HomeActivity)?.replaceFragment(ManagementFragment.newInstance("caja"))
                }
                .setNegativeButton(R.string.sale_cancel, null).show()
        } else if (parentFragmentManager.findFragmentByTag("CobroBottomSheet") == null) {
            CobroBottomSheet(Money.total(listaCarrito).toDouble(), listaCarrito.toList())
                .show(parentFragmentManager, "CobroBottomSheet")
        }
    }

    protected open suspend fun getNegocioId(): Long? = BusinessSession.current()?.negocio_id

    protected open suspend fun loadCatalog(negocioId: Long, query: String): List<VarianteDto> =
        CatalogRepository.load(negocioId, query, context = requireContext())
}
