package com.example.dajho

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.Job
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Serializable
data class VentaResumenDto(
    val id: Long? = null,
    val total: Double? = null,
    val estado: String? = null,
    val fecha_dispositivo: String? = null
)

@Serializable
data class GastoResumenDto(
    val id: Long? = null,
    val monto: Double? = null,
    val fecha: String? = null,
    val categoria: String? = null
)

class HomeFragment : Fragment() {

    private lateinit var layoutFechas: LinearLayout
    private lateinit var tvSalesAmount: TextView
    private lateinit var tvBalanceAmount: TextView
    private lateinit var tvExpensesAmount: TextView
    private lateinit var btnRegistrarVenta: Button
    private var fechaSeleccionada: LocalDate = LocalDate.now(java.time.ZoneId.of("America/Guayaquil"))
    private var metricasJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        layoutFechas = view.findViewById(R.id.layoutFechas)
        tvSalesAmount = view.findViewById(R.id.tvSalesAmount)
        tvBalanceAmount = view.findViewById(R.id.tvBalanceAmount)
        tvExpensesAmount = view.findViewById(R.id.tvExpensesAmount)
        btnRegistrarVenta = view.findViewById(R.id.btnRegistrarVenta)

        val cardCatalog = view.findViewById<View>(R.id.cardCatalog)
        val cardEmployees = view.findViewById<View>(R.id.cardEmployees)

        fun actualizarAccesosAdministrativos() {
            val propietario = (activity as? HomeActivity)?.esPropietario() == true
            cardCatalog.visibility = if (propietario) View.VISIBLE else View.GONE
            cardEmployees.visibility = if (propietario) View.VISIBLE else View.GONE
        }

        actualizarAccesosAdministrativos()
        parentFragmentManager.setFragmentResultListener(
            HomeActivity.RESULTADO_ROL,
            viewLifecycleOwner
        ) { _, _ ->
            actualizarAccesosAdministrativos()
        }

        cardCatalog.setOnClickListener {
            (activity as? HomeActivity)?.replaceFragment(ManagementFragment.newInstance("catalogo"))
        }

        view.findViewById<MaterialCardView>(R.id.cardClients).setOnClickListener {
            (activity as? HomeActivity)?.replaceFragment(ManagementFragment.newInstance("cliente"))
        }
        view.findViewById<MaterialCardView>(R.id.cardProviders).setOnClickListener {
            (activity as? HomeActivity)?.replaceFragment(ManagementFragment.newInstance("proveedor"))
        }
        cardEmployees.setOnClickListener {
            val homeActivity = activity as? HomeActivity
            if (homeActivity?.esPropietario() == true) {
                homeActivity.replaceFragment(EmpleadosFragment())
            }
        }
        view.findViewById<MaterialCardView>(R.id.cardDebts).setOnClickListener {
            (activity as? HomeActivity)?.replaceFragment(DeudasFragment())
        }

        generateDynamicDates()

        btnRegistrarVenta.setOnClickListener {
            (activity as? HomeActivity)?.replaceFragment(VentaFragment())
        }

        // Cargar las métricas reales del día desde Supabase
        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> cargarMetricasDelDia(fechaSeleccionada) }
        cargarMetricasDelDia(fechaSeleccionada)
    }

    private suspend fun getNegocioId(): Long? {
        val ctx = context ?: return BusinessSession.selectedId
        return runCatching { BusinessSession.current(ctx)?.negocio_id }.getOrNull()
            ?: BusinessSession.selectedId
    }

    private fun cargarMetricasDelDia(fechaSeleccionada: LocalDate) {
        metricasJob?.cancel()
        val ctx = context ?: return
        val db = DajhoDatabase.getInstance(ctx)
        val fechaStr = fechaSeleccionada.toString()

        // Carga inmediata síncrona de caché local para evitar parpadeos o valores en blanco
        val idRapido = BusinessSession.selectedId
        if (idRapido != null) {
            val localRapido = db.getResumenDia(idRapido, fechaStr) ?: ResumenDiaDto(0.0, 0.0, 0.0)
            mostrarResumen(localRapido)
        }

        metricasJob = viewLifecycleOwner.lifecycleScope.launch {
            val negocioId = getNegocioId() ?: return@launch
            val localResumen = db.getResumenDia(negocioId, fechaStr) ?: ResumenDiaDto(0.0, 0.0, 0.0)
            mostrarResumen(localResumen)

            if (ctx.hasInternetConnection()) {
                try {
                    val resumen = SupabaseModule.client.postgrest.rpc("resumen_dia", buildJsonObject {
                        put("p_negocio", negocioId)
                        put("p_fecha", fechaStr)
                    }).decodeAs<ResumenDiaDto>()

                    db.saveResumenDia(negocioId, fechaStr, resumen.ventas, resumen.ayer, resumen.gastos)
                    mostrarResumen(resumen)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.e("HomeFragment", "Error al cargar métricas de red: " + e.userMessage())
                }
            }
        }
    }

    private fun mostrarResumen(resumen: ResumenDiaDto) {
    val totalVentasHoy = resumen.ventas
    val totalVentasAyer = resumen.ayer
    val totalGastos = resumen.gastos
    val balance = totalVentasHoy - totalGastos

    // 4. Calcular porcentaje vs ayer
    val tvVsAyer = view?.findViewById<TextView>(R.id.tvVsAyer)
    val cardVsAyer = tvVsAyer?.parent as? MaterialCardView

    if (totalVentasAyer > 0.0) {
        val diff = totalVentasHoy - totalVentasAyer
        val percent = (diff / totalVentasAyer) * 100.0
        val absPercent = String.format(Locale.US, "%.1f", abs(percent))
        if (percent >= 0) {
            tvVsAyer?.text = "vs ayer ↑ $absPercent%"
            cardVsAyer?.setCardBackgroundColor(Color.parseColor("#DCFCE7")) // Verde
            tvVsAyer?.setTextColor(Color.parseColor("#059669"))
        } else {
            tvVsAyer?.text = "vs ayer ↓ $absPercent%"
            cardVsAyer?.setCardBackgroundColor(Color.parseColor("#FEE2E2")) // Rojo claro
            tvVsAyer?.setTextColor(Color.parseColor("#DC2626"))
        }
    } else if (totalVentasHoy > 0.0) {
        tvVsAyer?.text = "vs ayer ↑ 100%"
        cardVsAyer?.setCardBackgroundColor(Color.parseColor("#DCFCE7"))
        tvVsAyer?.setTextColor(Color.parseColor("#059669"))
    } else {
        tvVsAyer?.text = "vs ayer 0.0%"
        cardVsAyer?.setCardBackgroundColor(Color.parseColor("#F1F5F9"))
        tvVsAyer?.setTextColor(Color.parseColor("#64748B"))
    }

    // 5. Actualizar tarjetas
    tvSalesAmount.text = "$${String.format(Locale.US, "%.2f", totalVentasHoy)}"
    tvExpensesAmount.text = "$${String.format(Locale.US, "%.2f", totalGastos)}"
    tvBalanceAmount.text = "$${String.format(Locale.US, "%.2f", balance)}"
    }

    private fun generateDynamicDates() {
        layoutFechas.removeAllViews()
        val today = LocalDate.now(java.time.ZoneId.of("America/Guayaquil"))
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))

        for (i in -4..0) {
            val date = today.plusDays(i.toLong())
            val textView = TextView(requireContext()).apply {
                text = date.format(formatter)
                textSize = 13f
                setPadding(32, 12, 32, 12)
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
                tag = date
                setOnClickListener {
                    fechaSeleccionada = date
                    actualizarSeleccionFechas()
                    cargarMetricasDelDia(date)
                }
            }
            layoutFechas.addView(textView)
        }

        actualizarSeleccionFechas()
    }

    private fun actualizarSeleccionFechas() {
        for (i in 0 until layoutFechas.childCount) {
            val pill = layoutFechas.getChildAt(i) as TextView
            if (pill.tag == fechaSeleccionada) {
                pill.setBackgroundColor(Color.parseColor("#E0F2FE"))
                pill.setTextColor(Color.BLACK)
                pill.setTypeface(null, android.graphics.Typeface.BOLD)
            } else {
                pill.setBackgroundColor(Color.TRANSPARENT)
                pill.setTextColor(Color.WHITE)
                pill.setTypeface(null, android.graphics.Typeface.NORMAL)
            }
        }
    }
}
