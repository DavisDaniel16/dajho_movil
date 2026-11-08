package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

class DeudasFragment : Fragment() {

    private lateinit var rvDeudas: RecyclerView
    private lateinit var deudaAdapter: DeudaAdapter
    private var userNegocioId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_deudas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        rvDeudas = view.findViewById(R.id.rvDeudas)

        deudaAdapter = DeudaAdapter { deudaAgrupada ->
            val detalleBottomSheet = DeudasDetalleBottomSheet(
                clienteNombre = deudaAgrupada.clienteNombre,
                listaDeudas = deudaAgrupada.listaDeudasIndividuales
            )
            detalleBottomSheet.show(childFragmentManager, "DeudasDetalleBottomSheet")
        }

        rvDeudas.layoutManager = LinearLayoutManager(requireContext())
        rvDeudas.adapter = deudaAdapter

        childFragmentManager.setFragmentResultListener("deudas_actualizadas", viewLifecycleOwner) { _, _ -> cargarDeudas() }
        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> cargarDeudas() }
        cargarDeudas()
    }

    private suspend fun getNegocioId(): Long? = BusinessSession.current()?.negocio_id

    private fun cargarDeudas() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }

                val deudasRaw = CustomerRepository.debts(negocioId, context = requireContext())
                mostrarDeudas(deudasRaw)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("DeudasFragment", "Error al cargar deudas: " + e.userMessage())
            }
        }
    }

    private fun mostrarDeudas(deudasRaw: List<DeudaDto>) {
                // Agrupar por cliente_id
                val deudasAgrupadas = deudasRaw.groupBy { it.cliente_id }.map { (clienteId, fiadosCliente) ->
                    val primerCliente = fiadosCliente.firstOrNull()?.cliente
                    val saldoSumado = fiadosCliente.sumOf { it.saldo ?: it.monto_original ?: 0.0 }
                    DeudaClienteAgrupadaDto(
                        clienteId = clienteId,
                        clienteNombre = primerCliente?.nombre ?: "Cliente",
                        clienteTelefono = primerCliente?.telefono ?: "Sin número",
                        saldoTotal = saldoSumado,
                        cantidadDeudas = fiadosCliente.size,
                        listaDeudasIndividuales = fiadosCliente
                    )
                }

                deudaAdapter.updateList(deudasAgrupadas)

                view?.findViewById<View>(R.id.tvEstadoVacio)?.visibility =
                    if (deudasAgrupadas.isEmpty()) View.VISIBLE else View.GONE

    }
}
