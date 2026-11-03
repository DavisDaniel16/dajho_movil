package com.example.dajho

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

class ClientesBottomSheet(
    private val negocioId: Long
) : BottomSheetDialogFragment() {

    private lateinit var etSearch: TextInputEditText
    private lateinit var rvClientes: RecyclerView
    private lateinit var clienteAdapter: ClienteAdapter

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
            searchJob = lifecycleScope.launch {
                delay(200)
                if (query.isEmpty()) {
                    clienteAdapter.updateList(todosLosClientes)
                } else {
                    val filtrados = todosLosClientes.filter {
                        (it.nombre ?: "").contains(query, ignoreCase = true) ||
                        (it.telefono ?: "").contains(query, ignoreCase = true)
                    }
                    clienteAdapter.updateList(filtrados)
                }
            }
        }
    }

    private fun cargarClientes() {
        lifecycleScope.launch {
            try {
                val clientes = SupabaseModule.client.from("cliente")
                    .select {
                        filter {
                            eq("negocio_id", negocioId)
                        }
                    }
                    .decodeList<ClienteDto>()

                todosLosClientes = clientes
                clienteAdapter.updateList(clientes)

                if (clientes.isEmpty()) {
                    Toast.makeText(requireContext(), "No tienes clientes registrados", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("ClientesBottomSheet", "Error al cargar clientes", e)
                Toast.makeText(requireContext(), "Error al cargar clientes: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
