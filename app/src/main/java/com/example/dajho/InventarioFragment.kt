package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class InventarioFragment : Fragment() {

    private lateinit var etSearch: TextInputEditText
    private lateinit var rvInventario: RecyclerView
    private lateinit var inventarioAdapter: InventarioAdapter

    private var todosLosProductos = listOf<VarianteDto>()
    private var searchJob: Job? = null
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

        // Escuchar cuando un producto sea editado para recargar el inventario
        childFragmentManager.setFragmentResultListener("request_inventario_actualizado", viewLifecycleOwner) { _, _ ->
            cargarInventario()
        }

        inventarioAdapter = InventarioAdapter { variante ->
            val editarBottomSheet = EditarProductoBottomSheet(variante)
            editarBottomSheet.show(childFragmentManager, "EditarProductoBottomSheet")
        }

        rvInventario.layoutManager = LinearLayoutManager(requireContext())
        rvInventario.adapter = inventarioAdapter

        cargarInventario()

        // Filtrado en tiempo real
        etSearch.doAfterTextChanged { text ->
            val query = text.toString().trim()
            searchJob?.cancel()
            searchJob = lifecycleScope.launch {
                delay(200)
                if (query.isEmpty()) {
                    inventarioAdapter.updateList(todosLosProductos)
                } else {
                    val filtrados = todosLosProductos.filter {
                        (it.producto?.nombre ?: "").contains(query, ignoreCase = true) ||
                        (it.sku ?: "").contains(query, ignoreCase = true) ||
                        (it.talla ?: "").contains(query, ignoreCase = true) ||
                        (it.color ?: "").contains(query, ignoreCase = true)
                    }
                    inventarioAdapter.updateList(filtrados)
                }
            }
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
            Log.e("InventarioFragment", "Error al obtener negocio_id", e)
            null
        }
    }

    private fun cargarInventario() {
        lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                // Consulta a la tabla variante con el JOIN del producto
                val variantes = SupabaseModule.client.from("variante")
                    .select(Columns.raw("*, producto:producto_id(id, nombre)")) {
                        filter {
                            eq("negocio_id", negocioId)
                        }
                    }
                    .decodeList<VarianteDto>()

                todosLosProductos = variantes
                inventarioAdapter.updateList(variantes)

                if (variantes.isEmpty()) {
                    Toast.makeText(requireContext(), "No tienes productos en el inventario", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Log.e("InventarioFragment", "Error al cargar inventario", e)
                Toast.makeText(requireContext(), "Error al cargar inventario: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
