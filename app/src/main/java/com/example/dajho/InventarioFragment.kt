package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.intOrNull

class InventarioFragment : Fragment() {

    private lateinit var etSearch: TextInputEditText
    private lateinit var rvInventario: RecyclerView
    private lateinit var fabAgregarProducto: FloatingActionButton
    private lateinit var inventarioAdapter: InventarioAdapter

    private var inventarioCargado = false
    private var todosLosProductos = listOf<VarianteDto>()
    private var searchJob: Job? = null
    private var cargaInventarioJob: Job? = null
    private var userNegocioId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_inventario, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.etSearch)
        rvInventario = view.findViewById(R.id.rvInventario)
        fabAgregarProducto = view.findViewById(R.id.fabAgregarProducto)

        fun actualizarAccionesAdministrativas() {
            fabAgregarProducto.visibility =
                if ((activity as? HomeActivity)?.esPropietario() == true) View.VISIBLE else View.GONE
        }

        actualizarAccionesAdministrativas()
        parentFragmentManager.setFragmentResultListener(
            HomeActivity.RESULTADO_ROL,
            viewLifecycleOwner
        ) { _, _ ->
            actualizarAccionesAdministrativas()
        }

        fabAgregarProducto.setOnClickListener {
            if ((activity as? HomeActivity)?.esPropietario() == true) {
                NuevoProductoBottomSheet()
                    .show(childFragmentManager, "NuevoProductoBottomSheet")
            }
        }

        // Escuchar cuando un producto sea editado o creado para recargar el inventario
        childFragmentManager.setFragmentResultListener("request_inventario_actualizado", viewLifecycleOwner) { _, _ ->
            cargarInventario(forceRefresh = true)
        }

        inventarioAdapter = InventarioAdapter { variante ->
            if ((activity as? HomeActivity)?.esPropietario() == true) {
                EditarProductoBottomSheet(variante)
                    .show(childFragmentManager, "EditarProductoBottomSheet")
            }
        }

        rvInventario.layoutManager = LinearLayoutManager(requireContext())
        rvInventario.adapter = inventarioAdapter

        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> cargarInventario() }
        cargarInventario()

        // Filtrado en tiempo real
        etSearch.doAfterTextChanged {
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(200)
                aplicarFiltroInventario()
            }
        }
    }

    private suspend fun getNegocioId(): Long? = BusinessSession.current()?.negocio_id

    private fun cargarInventario(forceRefresh: Boolean = false) {
        cargaInventarioJob?.cancel()
        cargaInventarioJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    AppFeedback.makeText(requireContext(), "No se pudo identificar tu negocio. Vuelve a Inicio y comprueba tu acceso.", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                    return@launch
                }
                userNegocioId = negocioId
                val variantes = CatalogRepository.load(negocioId, context = requireContext(), forceRefresh = forceRefresh)

                inventarioCargado = true
                todosLosProductos = variantes
                aplicarFiltroInventario()


            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("InventarioFragment", "Error al cargar inventario" + ": " + e.userMessage())
            }
        }
    }

    private fun aplicarFiltroInventario() {
        val query = etSearch.text.toString().trim()
        val visibles = if (query.isEmpty()) {
            todosLosProductos
        } else {
            todosLosProductos.filter {
                it.producto?.nombre.orEmpty().contains(query, ignoreCase = true) ||
                    it.sku.orEmpty().contains(query, ignoreCase = true) ||
                    it.codigo_barras.orEmpty().contains(query, ignoreCase = true) ||
                    it.talla.orEmpty().contains(query, ignoreCase = true) ||
                    it.color.orEmpty().contains(query, ignoreCase = true)
            }
        }
        inventarioAdapter.updateList(visibles)
        view?.findViewById<android.widget.TextView>(R.id.tvEstadoVacio)?.apply {
            text = if (query.isEmpty()) "No hay productos en el inventario" else "No se encontraron productos"
            visibility = if (inventarioCargado && visibles.isEmpty()) View.VISIBLE else View.GONE
        }
    }
}
