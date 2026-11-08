package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ClientesBottomSheet() : BottomSheetDialogFragment() {
    constructor(negocioId: Long) : this() {
        arguments = android.os.Bundle().apply {
            putString("negocioId", ScreenArgs.json.encodeToString(negocioId))
        }
    }
    private val negocioId: Long get() = ScreenArgs.json.decodeFromString(requireArguments().getString("negocioId")!!)


    private lateinit var etSearch: TextInputEditText
    private lateinit var rvClientes: RecyclerView
    private lateinit var clienteAdapter: ClienteAdapter

    private var clientesCargados = false
    private var todosLosClientes = listOf<ClienteDto>()
    private var searchJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_clientes, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etSearch = view.findViewById(R.id.etSearch)
        rvClientes = view.findViewById(R.id.rvClientes)

        clienteAdapter = ClienteAdapter { cliente ->
            parentFragmentManager.setFragmentResult(
                "request_cliente_seleccionado",
                bundleOf(
                    "cliente_id" to (cliente.id ?: -1L),
                    "cliente_nombre" to (cliente.nombre ?: "Cliente")
                )
            )
            dismiss()
        }

        rvClientes.layoutManager = LinearLayoutManager(requireContext())
        rvClientes.adapter = clienteAdapter

        cargarClientes()

        // Filtrado en tiempo real
        etSearch.doAfterTextChanged { text ->
            val query = text.toString().trim()
            searchJob?.cancel()
            searchJob = viewLifecycleOwner.lifecycleScope.launch {
                delay(200)
                aplicarFiltro(query)
            }
        }
    }

    private fun aplicarFiltro(query: String) {
        val visibles = todosLosClientes.filter {
            it.nombre.orEmpty().contains(query, ignoreCase = true) ||
                it.telefono.orEmpty().contains(query, ignoreCase = true)
        }
        clienteAdapter.updateList(visibles)
        view?.findViewById<android.widget.TextView>(R.id.tvEstadoVacio)?.apply {
            text = if (query.isEmpty()) "No hay clientes registrados" else "No se encontraron clientes"
            visibility = if (clientesCargados && visibles.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun cargarClientes() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val clientes = CustomerRepository.clients(negocioId, context = requireContext())

                clientesCargados = true
                todosLosClientes = clientes
                aplicarFiltro(etSearch.text.toString().trim())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("ClientesBottomSheet", "Error al cargar clientes" + ": " + e.userMessage())
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
