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

                // Consulta con JOIN anidado: fiado -> cliente, venta -> detalle_venta -> variante -> producto
                val deudasRaw = CustomerRepository.debts(negocioId)

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

                if (deudasAgrupadas.isEmpty()) {
                    Toast.makeText(requireContext(), "No tienes deudas pendientes de cobro", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("DeudasFragment", "Error al cargar deudas" + ": " + e.userMessage())
                Toast.makeText(requireContext(), "Error al cargar deudas: ${e.userMessage()}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
