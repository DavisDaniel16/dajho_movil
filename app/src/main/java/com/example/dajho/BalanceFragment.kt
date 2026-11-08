package com.example.dajho

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class DetalleVentaBalanceDto(
    val cantidad: Int? = null,
    val precio_unitario: Double? = null,
    val variante: VarianteSimpleDto? = null
)

@Serializable
data class VentaBalanceDto(
    val id: Long? = null,
    val total: Double? = null,
    val fecha_dispositivo: String? = null,
    val detalle_venta: List<DetalleVentaBalanceDto>? = null
)

class BalanceFragment : Fragment() {

    private lateinit var tvBalanceNeto: TextView
    private lateinit var tvTotalIngresos: TextView
    private lateinit var tvTotalEgresos: TextView
    private lateinit var rvMovimientos: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAgregarGasto: FloatingActionButton

    private lateinit var balanceAdapter: BalanceAdapter
    private var userNegocioId: Long? = null
    private var balanceJob: Job? = null

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
        progressBar = view.findViewById(R.id.progressBar)
        fabAgregarGasto = view.findViewById(R.id.fabAgregarGasto)

        fun actualizarAccionesAdministrativas() {
            fabAgregarGasto.visibility =
                if ((activity as? HomeActivity)?.esPropietario() == true) View.VISIBLE else View.GONE
        }

        actualizarAccionesAdministrativas()
        parentFragmentManager.setFragmentResultListener(
            HomeActivity.RESULTADO_ROL,
            viewLifecycleOwner
        ) { _, _ ->
            actualizarAccionesAdministrativas()
        }

        balanceAdapter = BalanceAdapter { movimiento ->
            DetalleVentaBottomSheet(movimiento.titulo, movimiento.detallesVenta)
                .show(childFragmentManager, "DetalleVentaBottomSheet")
        }
        rvMovimientos.layoutManager = LinearLayoutManager(requireContext())
        rvMovimientos.adapter = balanceAdapter

        // Escuchar cuando se guarde un nuevo gasto para recargar
        childFragmentManager.setFragmentResultListener("request_balance_actualizado", viewLifecycleOwner) { _, _ ->
            cargarDatosBalance()
        }

        fabAgregarGasto.setOnClickListener {
            if ((activity as? HomeActivity)?.esPropietario() != true) return@setOnClickListener

            val negocioId = userNegocioId
            if (negocioId != null) {
                GastoBottomSheet(negocioId)
                    .show(childFragmentManager, "GastoBottomSheet")
            } else {
                Toast.makeText(requireContext(), "Cargando negocio...", Toast.LENGTH_SHORT).show()
            }
        }

        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> cargarDatosBalance() }
        cargarDatosBalance()
    }

    private suspend fun getNegocioId(): Long? = BusinessSession.current()?.negocio_id

    private fun mostrarBalance(balance: BalanceNegocioDto, negocioId: Long) {
        balanceAdapter.updateList(balance.movimientos)
        tvTotalIngresos.text = "$${String.format(Locale.US, "%.2f", balance.ingresos)}"
        tvTotalEgresos.text = "$${String.format(Locale.US, "%.2f", balance.egresos)}"
        tvBalanceNeto.text = "$${String.format(Locale.US, "%.2f", balance.ingresos - balance.egresos)}"
    }

    private fun cargarDatosBalance() {
        balanceJob?.cancel()
        balanceJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                val negocioId = userNegocioId ?: getNegocioId() ?: return@launch
                userNegocioId = negocioId
                val db = DajhoDatabase.getInstance(requireContext())
                progressBar.visibility = View.VISIBLE

                if (requireContext().hasInternetConnection()) {
                    try {
                        val balance = SupabaseModule.client.postgrest.rpc("balance_negocio", buildJsonObject {
                            put("p_negocio", negocioId)
                        }).decodeAs<BalanceNegocioDto>()
                        db.saveGestionItems(negocioId, "balance", ScreenArgs.json.encodeToString(balance))
                        mostrarBalance(balance, negocioId)
                        return@launch
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        Log.e("BalanceFragment", "Error cargando balance remoto: " + e.userMessage())
                    }
                }

                val cachedStr = db.getGestionItems(negocioId, "balance")
                if (cachedStr != null) {
                    val balance = ScreenArgs.json.decodeFromString<BalanceNegocioDto>(cachedStr)
                    mostrarBalance(balance, negocioId)
                } else {
                    Toast.makeText(requireContext(), "Sin datos de balance guardados localmente", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("BalanceFragment", "Error al cargar balance: " + e.userMessage())
            } finally {
                progressBar.visibility = View.GONE
            }
        }
    }
}
