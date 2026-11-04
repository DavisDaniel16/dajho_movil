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
        cardCatalog.setOnClickListener {
            Toast.makeText(requireContext(), "Catálogo Virtual próximamente", Toast.LENGTH_SHORT).show()
        }

        val ivSearch = requireActivity().findViewById<View>(R.id.ivSearch)
        ivSearch?.setOnClickListener {
            Toast.makeText(requireContext(), "Buscador global próximamente", Toast.LENGTH_SHORT).show()
        }

        generateDynamicDates()

        btnRegistrarVenta.setOnClickListener {
            (activity as? HomeActivity)?.replaceFragment(VentaFragment())
        }

        // Cargar las métricas reales del día desde Supabase
        cargarMetricasDelDia()
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
            Log.e("HomeFragment", "Error al obtener negocio_id", e)
            null
        }
    }

    private fun cargarMetricasDelDia() {
        lifecycleScope.launch {
            try {
                val negocioId = getNegocioId() ?: return@launch
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)
                val todayStr = today.toString()
                val yesterdayStr = yesterday.toString()

                // 1. Consultar Ventas de hoy
                val ventasHoyList = SupabaseModule.client.from("venta")
                    .select {
                        filter {
                            eq("negocio_id", negocioId)
                            eq("estado", "completada")
                            gte("fecha_dispositivo", "${todayStr}T00:00:00")
                            lte("fecha_dispositivo", "${todayStr}T23:59:59")
                        }
                    }
                    .decodeList<VentaResumenDto>()

                val totalVentasHoy = ventasHoyList.sumOf { it.total ?: 0.0 }

                // 2. Consultar Ventas de ayer
                val ventasAyerList = SupabaseModule.client.from("venta")
                    .select {
                        filter {
                            eq("negocio_id", negocioId)
                            eq("estado", "completada")
                            gte("fecha_dispositivo", "${yesterdayStr}T00:00:00")
                            lte("fecha_dispositivo", "${yesterdayStr}T23:59:59")
                        }
                    }
                    .decodeList<VentaResumenDto>()

                val totalVentasAyer = ventasAyerList.sumOf { it.total ?: 0.0 }

                // 3. Consultar Gastos de hoy
                val gastosList = SupabaseModule.client.from("gasto")
                    .select {
                        filter {
                            eq("negocio_id", negocioId)
                            eq("fecha", todayStr)
                        }
                    }
                    .decodeList<GastoResumenDto>()

                val totalGastos = gastosList.sumOf { it.monto ?: 0.0 }
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

            } catch (e: Exception) {
                Log.e("HomeFragment", "Error al cargar métricas del día", e)
                tvSalesAmount.text = "$0.00"
                tvExpensesAmount.text = "$0.00"
                tvBalanceAmount.text = "$0.00"
            }
        }
    }

    private fun generateDynamicDates() {
        layoutFechas.removeAllViews()
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("d MMM", Locale("es", "ES"))

        for (i in -4..0) {
            val date = today.plusDays(i.toLong())
            val dateText = date.format(formatter)

            val textView = TextView(requireContext()).apply {
                text = dateText
                textSize = 13f
                setPadding(32, 12, 32, 12)
                gravity = Gravity.CENTER
                if (i == 0) {
                    setBackgroundColor(Color.parseColor("#FEF08A"))
                    setTextColor(Color.BLACK)
                    setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    setTextColor(Color.parseColor("#334155"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 12
                }
            }
            layoutFechas.addView(textView)
        }
    }
}
