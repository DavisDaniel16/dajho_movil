package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import java.util.Locale

class BalanceFragment : Fragment() {

    private lateinit var tvBalanceNeto: TextView
    private lateinit var tvTotalIngresos: TextView
    private lateinit var tvTotalEgresos: TextView
    private lateinit var rvMovimientos: RecyclerView
    private lateinit var fabAgregarGasto: FloatingActionButton

    private lateinit var balanceAdapter: BalanceAdapter
    private var userNegocioId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_balance, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvBalanceNeto = view.findViewById(R.id.tvBalanceNeto)
        tvTotalIngresos = view.findViewById(R.id.tvTotalIngresos)
        tvTotalEgresos = view.findViewById(R.id.tvTotalEgresos)
        rvMovimientos = view.findViewById(R.id.rvMovimientos)
        fabAgregarGasto = view.findViewById(R.id.fabAgregarGasto)

        balanceAdapter = BalanceAdapter()
        rvMovimientos.layoutManager = LinearLayoutManager(requireContext())
        rvMovimientos.adapter = balanceAdapter

        // Escuchar cuando se guarde un nuevo gasto para recargar
        childFragmentManager.setFragmentResultListener("request_balance_actualizado", viewLifecycleOwner) { _, _ ->
            cargarDatosBalance()
        }

        fabAgregarGasto.setOnClickListener {
            val negocioId = userNegocioId
            if (negocioId != null) {
                val gastoBottomSheet = GastoBottomSheet(negocioId)
                gastoBottomSheet.show(childFragmentManager, "GastoBottomSheet")
            } else {
                Toast.makeText(requireContext(), "Cargando negocio...", Toast.LENGTH_SHORT).show()
            }
        }

        cargarDatosBalance()
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
            Log.e("BalanceFragment", "Error al obtener negocio_id", e)
            null
        }
    }

    private fun cargarDatosBalance() {
        lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId()
                if (negocioId == null) {
                    Toast.makeText(requireContext(), "No se encontró el negocio del usuario", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                userNegocioId = negocioId

                // 1. Consultar Ventas (Ingresos) completadas
                val ventas = SupabaseModule.client.from("venta")
                    .select {
                        filter {
                            eq("negocio_id", negocioId)
                            eq("estado", "completada")
                        }
                    }
                    .decodeList<VentaResumenDto>()

                // 2. Consultar Gastos (Egresos)
                val gastos = SupabaseModule.client.from("gasto")
                    .select {
                        filter {
                            eq("negocio_id", negocioId)
                        }
                    }
                    .decodeList<GastoResumenDto>()

                // 3. Combinar y mapear movimientos
                val listaMovimientos = mutableListOf<MovimientoDto>()

                ventas.forEach { venta ->
                    val fechaStr = venta.fecha_dispositivo?.take(10) ?: "Fecha desconocida"
                    listaMovimientos.add(
                        MovimientoDto(
                            titulo = "Venta #${venta.id ?: ""}",
                            tipo = "ingreso",
                            monto = venta.total ?: 0.0,
                            fecha = fechaStr
                        )
                    )
                }

                gastos.forEach { gasto ->
                    val fechaStr = gasto.fecha?.take(10) ?: "Fecha desconocida"
                    val tituloGasto = if (!gasto.categoria.isNullOrEmpty()) gasto.categoria else "Gasto"
                    listaMovimientos.add(
                        MovimientoDto(
                            titulo = tituloGasto,
                            tipo = "egreso",
                            monto = gasto.monto ?: 0.0,
                            fecha = fechaStr
                        )
                    )
                }

                // Ordenar por fecha descendente
                listaMovimientos.sortByDescending { it.fecha }

                balanceAdapter.updateList(listaMovimientos)

                // 4. Calcular Totales y Balance Neto
                val totalIngresos = ventas.sumOf { it.total ?: 0.0 }
                val totalEgresos = gastos.sumOf { it.monto ?: 0.0 }
                val balanceNeto = totalIngresos - totalEgresos

                tvTotalIngresos.text = "$${String.format(Locale.US, "%.2f", totalIngresos)}"
                tvTotalEgresos.text = "$${String.format(Locale.US, "%.2f", totalEgresos)}"
                tvBalanceNeto.text = "$${String.format(Locale.US, "%.2f", balanceNeto)}"

            } catch (e: Exception) {
                Log.e("BalanceFragment", "Error al cargar balance", e)
                Toast.makeText(requireContext(), "Error al cargar balance: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
