package com.example.dajho

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.mfa.FactorType
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.util.Locale

class AdminFragment : Fragment() {

    private lateinit var btnRefrescarAdmin: ImageButton
    private lateinit var cardMfaBanner: MaterialCardView
    private lateinit var chipMfaStatus: Chip
    private lateinit var tvMfaBannerTitle: TextView
    private lateinit var tvMfaBannerSubtitle: TextView
    private lateinit var btnVerificarMfa: MaterialButton
    private lateinit var btnConfigurarMfa: MaterialButton

    private lateinit var tvCountNegocios: TextView
    private lateinit var tvDetailNegocios: TextView
    private lateinit var tvCountPagos: TextView
    private lateinit var tvDetailPagos: TextView
    private lateinit var tvCountSoporte: TextView
    private lateinit var tvDetailSoporte: TextView

    private lateinit var chipGroupSections: ChipGroup
    private lateinit var chipTabNegocios: Chip
    private lateinit var chipTabPagos: Chip
    private lateinit var chipTabSoporte: Chip
    private lateinit var chipTabSeguridad: Chip

    private lateinit var tilBuscarAdmin: TextInputLayout
    private lateinit var etBuscarAdmin: TextInputEditText
    private lateinit var containerAdminItems: LinearLayout
    private lateinit var tvEmptyAdmin: TextView
    private lateinit var progressAdmin: ProgressBar

    private var negociosList: List<JsonObject> = emptyList()
    private var pagosList: List<JsonObject> = emptyList()
    private var soporteList: List<JsonObject> = emptyList()
    private var isMfaActive: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_admin, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        btnRefrescarAdmin = view.findViewById(R.id.btnRefrescarAdmin)
        cardMfaBanner = view.findViewById(R.id.cardMfaBanner)
        chipMfaStatus = view.findViewById(R.id.chipMfaStatus)
        tvMfaBannerTitle = view.findViewById(R.id.tvMfaBannerTitle)
        tvMfaBannerSubtitle = view.findViewById(R.id.tvMfaBannerSubtitle)
        btnVerificarMfa = view.findViewById(R.id.btnVerificarMfa)
        btnConfigurarMfa = view.findViewById(R.id.btnConfigurarMfa)

        tvCountNegocios = view.findViewById(R.id.tvCountNegocios)
        tvDetailNegocios = view.findViewById(R.id.tvDetailNegocios)
        tvCountPagos = view.findViewById(R.id.tvCountPagos)
        tvDetailPagos = view.findViewById(R.id.tvDetailPagos)
        tvCountSoporte = view.findViewById(R.id.tvCountSoporte)
        tvDetailSoporte = view.findViewById(R.id.tvDetailSoporte)

        chipGroupSections = view.findViewById(R.id.chipGroupSections)
        chipTabNegocios = view.findViewById(R.id.chipTabNegocios)
        chipTabPagos = view.findViewById(R.id.chipTabPagos)
        chipTabSoporte = view.findViewById(R.id.chipTabSoporte)
        chipTabSeguridad = view.findViewById(R.id.chipTabSeguridad)

        tilBuscarAdmin = view.findViewById(R.id.tilBuscarAdmin)
        etBuscarAdmin = view.findViewById(R.id.etBuscarAdmin)
        containerAdminItems = view.findViewById(R.id.containerAdminItems)
        tvEmptyAdmin = view.findViewById(R.id.tvEmptyAdmin)
        progressAdmin = view.findViewById(R.id.progressAdmin)

        btnRefrescarAdmin.setOnClickListener { loadData() }
        btnVerificarMfa.setOnClickListener { verifyMfa() }
        btnConfigurarMfa.setOnClickListener { enrollMfa() }

        chipGroupSections.setOnCheckedStateChangeListener { _, _ ->
            renderCurrentTab()
        }

        etBuscarAdmin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (chipGroupSections.checkedChipId == R.id.chipTabNegocios) {
                    renderNegociosTab()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> loadData() }

        loadData()
    }

    private fun loadData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                progressAdmin.visibility = View.VISIBLE
                tvEmptyAdmin.visibility = View.GONE

                isMfaActive = SupabaseModule.client.auth.mfa.status.active
                updateMfaBanner(isMfaActive)

                val panel = SupabaseModule.client.postgrest.rpc("panel_administracion").decodeAs<JsonObject>()
                negociosList = panel["negocios"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
                pagosList = panel["pagos"]?.jsonArray?.map { it.jsonObject } ?: emptyList()
                soporteList = panel["soporte"]?.jsonArray?.map { it.jsonObject } ?: emptyList()

                updateMetrics()
                renderCurrentTab()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                tvEmptyAdmin.text = e.userMessage()
                tvEmptyAdmin.visibility = View.VISIBLE
                containerAdminItems.removeAllViews()
                Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show()
            } finally {
                progressAdmin.visibility = View.GONE
            }
        }
    }

    private fun updateMfaBanner(active: Boolean) {
        if (active) {
            cardMfaBanner.setCardBackgroundColor(Color.parseColor("#F0FDF4"))
            cardMfaBanner.strokeColor = Color.parseColor("#BBF7D0")

            chipMfaStatus.text = "Verificado"
            chipMfaStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
            chipMfaStatus.setTextColor(Color.parseColor("#15803D"))

            tvMfaBannerTitle.text = "Seguridad 2FA Activa"
            tvMfaBannerTitle.setTextColor(Color.parseColor("#166534"))

            tvMfaBannerSubtitle.text = "Tu sesión administrativa cuenta con verificación de segundo factor activa."
            tvMfaBannerSubtitle.setTextColor(Color.parseColor("#166534"))

            btnVerificarMfa.text = "Verificar Código"
            btnVerificarMfa.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#16A34A"))
            btnConfigurarMfa.text = "Reconfigurar TOTP"
            btnConfigurarMfa.setTextColor(Color.parseColor("#166534"))
            btnConfigurarMfa.strokeColor = ColorStateList.valueOf(Color.parseColor("#86EFAC"))
        } else {
            cardMfaBanner.setCardBackgroundColor(Color.parseColor("#FFF7ED"))
            cardMfaBanner.strokeColor = Color.parseColor("#FED7AA")

            chipMfaStatus.text = "Pendiente"
            chipMfaStatus.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FFEDD5"))
            chipMfaStatus.setTextColor(Color.parseColor("#C2410C"))

            tvMfaBannerTitle.text = "Seguridad 2FA Pendiente"
            tvMfaBannerTitle.setTextColor(Color.parseColor("#9A3412"))

            tvMfaBannerSubtitle.text = "Se requiere verificar tu autenticador TOTP para realizar operaciones avanzadas."
            tvMfaBannerSubtitle.setTextColor(Color.parseColor("#C2410C"))

            btnVerificarMfa.text = "Verificar Código"
            btnVerificarMfa.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EA580C"))
            btnConfigurarMfa.text = "Configurar 2FA"
            btnConfigurarMfa.setTextColor(Color.parseColor("#9A3412"))
            btnConfigurarMfa.strokeColor = ColorStateList.valueOf(Color.parseColor("#FDBA74"))
        }
    }

    private fun updateMetrics() {
        val totalNegocios = negociosList.size
        val activosNegocios = negociosList.count { it.str("estado") == "activo" }
        tvCountNegocios.text = totalNegocios.toString()
        tvDetailNegocios.text = "$activosNegocios activos"

        val totalPagos = pagosList.size
        val montoTotalPagos = pagosList.sumOf { it.str("total").toDoubleOrNull() ?: 0.0 }
        tvCountPagos.text = totalPagos.toString()
        tvDetailPagos.text = String.format(Locale.US, "$%.2f total", montoTotalPagos)

        val totalSoporte = soporteList.size
        val pendientesSoporte = soporteList.count { (it["autorizado_jerarquia_id"] == JsonNull) || (it.str("estado") == "solicitado") }
        tvCountSoporte.text = totalSoporte.toString()
        tvDetailSoporte.text = "$pendientesSoporte por autorizar"

        chipTabNegocios.text = "Negocios ($totalNegocios)"
        chipTabPagos.text = "Pagos ($totalPagos)"
        chipTabSoporte.text = "Soporte ($totalSoporte)"
    }

    private fun renderCurrentTab() {
        containerAdminItems.removeAllViews()
        tvEmptyAdmin.visibility = View.GONE

        when (chipGroupSections.checkedChipId) {
            R.id.chipTabNegocios -> {
                tilBuscarAdmin.visibility = View.VISIBLE
                renderNegociosTab()
            }
            R.id.chipTabPagos -> {
                tilBuscarAdmin.visibility = View.GONE
                renderPagosTab()
            }
            R.id.chipTabSoporte -> {
                tilBuscarAdmin.visibility = View.GONE
                renderSoporteTab()
            }
            R.id.chipTabSeguridad -> {
                tilBuscarAdmin.visibility = View.GONE
                renderSeguridadTab()
            }
            else -> {
                tilBuscarAdmin.visibility = View.VISIBLE
                renderNegociosTab()
            }
        }
    }

    private fun renderNegociosTab() {
        containerAdminItems.removeAllViews()
        val query = etBuscarAdmin.text?.toString()?.trim().orEmpty().lowercase()

        val filtered = negociosList.filter { n ->
            query.isEmpty() || n.str("nombre").lowercase().contains(query) || n.str("id").contains(query)
        }

        if (filtered.isEmpty()) {
            tvEmptyAdmin.text = if (query.isEmpty()) "No hay negocios registrados en el sistema." else "No se encontraron negocios con '$query'."
            tvEmptyAdmin.visibility = View.VISIBLE
            return
        }

        tvEmptyAdmin.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())

        filtered.forEach { n ->
            val itemView = inflater.inflate(R.layout.item_admin_negocio, containerAdminItems, false)

            val tvNombre = itemView.findViewById<TextView>(R.id.tvAdminNombreNegocio)
            val tvId = itemView.findViewById<TextView>(R.id.tvAdminIdNegocio)
            val chipEstado = itemView.findViewById<Chip>(R.id.chipAdminEstadoNegocio)
            val btnEstado = itemView.findViewById<MaterialButton>(R.id.btnAdminCambiarEstado)
            val btnSoporte = itemView.findViewById<MaterialButton>(R.id.btnAdminSolicitarSoporte)

            val id = n.str("id")
            val nombre = n.str("nombre")
            val estado = n.str("estado")

            tvNombre.text = nombre
            tvId.text = "ID: #$id"

            if (estado == "activo") {
                chipEstado.text = "Activo"
                chipEstado.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                chipEstado.setTextColor(Color.parseColor("#15803D"))

                btnEstado.text = "Suspender"
                btnEstado.setTextColor(Color.parseColor("#DC2626"))
                btnEstado.strokeColor = ColorStateList.valueOf(Color.parseColor("#FECACA"))
            } else {
                chipEstado.text = "Suspendido"
                chipEstado.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FEE2E2"))
                chipEstado.setTextColor(Color.parseColor("#DC2626"))

                btnEstado.text = "Activar"
                btnEstado.setTextColor(Color.parseColor("#15803D"))
                btnEstado.strokeColor = ColorStateList.valueOf(Color.parseColor("#BBF7D0"))
            }

            btnEstado.setOnClickListener {
                val targetEstado = if (estado == "activo") "suspendido" else "activo"
                showForm("Confirmar cambio de estado", listOf(FormField("Escribe CONFIRMAR para cambiar a $targetEstado"))) { values ->
                    require(values[0].uppercase() == "CONFIRMAR") { "Debes escribir CONFIRMAR para proceder" }
                    SupabaseModule.client.postgrest.rpc("admin_cambiar_estado_negocio", buildJsonObject {
                        put("p_negocio", n["id"]!!)
                        put("p_estado", targetEstado)
                    })
                    Toast.makeText(context, "Estado de negocio actualizado a $targetEstado", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }

            btnSoporte.setOnClickListener {
                showForm("Solicitar Soporte Técnico", listOf(FormField("Motivo de la solicitud para $nombre"))) { values ->
                    require(values[0].isNotBlank()) { "Ingresa el motivo de la solicitud" }
                    SupabaseModule.client.postgrest.rpc("admin_solicitar_soporte", buildJsonObject {
                        put("p_negocio", n["id"]!!)
                        put("p_motivo", values[0])
                    })
                    Toast.makeText(context, "Solicitud de soporte creada", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }

            containerAdminItems.addView(itemView)
        }
    }

    private fun renderPagosTab() {
        containerAdminItems.removeAllViews()

        if (pagosList.isEmpty()) {
            tvEmptyAdmin.text = "No hay pagos de suscripción pendientes de validación."
            tvEmptyAdmin.visibility = View.VISIBLE
            return
        }

        tvEmptyAdmin.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())

        pagosList.forEach { p ->
            val itemView = inflater.inflate(R.layout.item_admin_pago, containerAdminItems, false)

            val tvTitle = itemView.findViewById<TextView>(R.id.tvAdminPagoTitle)
            val tvSub = itemView.findViewById<TextView>(R.id.tvAdminPagoSub)
            val tvMonto = itemView.findViewById<TextView>(R.id.tvAdminPagoMonto)
            val btnValidar = itemView.findViewById<MaterialButton>(R.id.btnAdminValidarPago)

            val id = p.str("id")
            val negocioId = p.str("negocio_id")
            val metodo = p.str("metodo").uppercase()
            val referencia = p.str("referencia").ifEmpty { "Sin ref" }
            val total = p.str("total")

            tvTitle.text = "Pago #$id · Negocio #$negocioId"
            tvSub.text = "$metodo · Ref: $referencia"
            tvMonto.text = "$$total"

            btnValidar.setOnClickListener {
                showForm("Validar Pago #$id", listOf(FormField("Escribe CONFIRMAR tras verificar los $total en cuenta"))) { values ->
                    require(values[0].uppercase() == "CONFIRMAR") { "Debes escribir CONFIRMAR para validar" }
                    SupabaseModule.client.postgrest.rpc("admin_validar_pago", buildJsonObject {
                        put("p_pago", p["id"]!!)
                    })
                    Toast.makeText(context, "Pago #$id validado correctamente", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }

            containerAdminItems.addView(itemView)
        }
    }

    private fun renderSoporteTab() {
        containerAdminItems.removeAllViews()

        if (soporteList.isEmpty()) {
            tvEmptyAdmin.text = "No hay solicitudes de acceso de soporte técnico activas."
            tvEmptyAdmin.visibility = View.VISIBLE
            return
        }

        tvEmptyAdmin.visibility = View.GONE
        val inflater = LayoutInflater.from(requireContext())

        soporteList.forEach { s ->
            val itemView = inflater.inflate(R.layout.item_admin_soporte, containerAdminItems, false)

            val tvTitle = itemView.findViewById<TextView>(R.id.tvAdminSoporteTitle)
            val tvNegocio = itemView.findViewById<TextView>(R.id.tvAdminSoporteNegocio)
            val chipEstado = itemView.findViewById<Chip>(R.id.chipAdminSoporteEstado)
            val tvMotivo = itemView.findViewById<TextView>(R.id.tvAdminSoporteMotivo)
            val btnAutorizar = itemView.findViewById<MaterialButton>(R.id.btnAdminAutorizarSoporte)

            val id = s.str("id")
            val negocioId = s.str("negocio_id")
            val motivo = s.str("motivo")
            val estado = s.str("estado")
            val esJerarquiaAutorizada = s["autorizado_jerarquia_id"] != JsonNull

            tvTitle.text = "Solicitud #$id"
            tvNegocio.text = "Negocio #$negocioId"
            tvMotivo.text = "Motivo: $motivo"

            if (esJerarquiaAutorizada) {
                chipEstado.text = "Autorizada"
                chipEstado.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#DCFCE7"))
                chipEstado.setTextColor(Color.parseColor("#15803D"))

                btnAutorizar.isEnabled = false
                btnAutorizar.text = "Ya Autorizada por Jerarquía"
            } else {
                chipEstado.text = estado.replaceFirstChar { it.uppercase() }
                chipEstado.chipBackgroundColor = ColorStateList.valueOf(Color.parseColor("#FEF3C7"))
                chipEstado.setTextColor(Color.parseColor("#B45309"))

                btnAutorizar.isEnabled = true
                btnAutorizar.text = "Autorizar Acceso por Jerarquía"
            }

            btnAutorizar.setOnClickListener {
                val defaultEnd = OffsetDateTime.now().plusDays(1).toString()
                showForm("Autorizar Acceso de Soporte #$id", listOf(
                    FormField("Fin de autorización (AAAA-MM-DDTHH:MM:SS-05:00)", value = defaultEnd)
                )) { values ->
                    val end = OffsetDateTime.parse(values[0])
                    require(end.isAfter(OffsetDateTime.now())) { "La fecha de fin debe ser futura" }
                    SupabaseModule.client.postgrest.rpc("autorizar_soporte_jerarquia", buildJsonObject {
                        put("p_acceso", s["id"]!!)
                        put("p_fin", end.toString())
                    })
                    Toast.makeText(context, "Solicitud #$id autorizada por jerarquía", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            }

            containerAdminItems.addView(itemView)
        }
    }

    private fun renderSeguridadTab() {
        containerAdminItems.removeAllViews()

        val card = MaterialCardView(requireContext()).apply {
            radius = (14 * resources.displayMetrics.density)
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            setStrokeColor(ColorStateList.valueOf(Color.parseColor("#CBD5E1")))
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        }

        val tvTitle = TextView(requireContext()).apply {
            text = "Autenticación de Segundo Factor (MFA / TOTP)"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
        }

        val tvDesc = TextView(requireContext()).apply {
            text = if (isMfaActive) {
                "El segundo factor de autenticación TOTP está ACTIVO para tu cuenta de administrador.\n\nPuedes generar un nuevo desafío para re-verificar el acceso o enrolar un nuevo dispositivo autenticador si lo necesitas."
            } else {
                "El segundo factor de autenticación TOTP está PENDIENTE o inactivo.\n\nEs altamente recomendado enrolar una app como Google Authenticator o Authy para proteger la administración del sistema DAJHO."
            }
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            setPadding(0, 8.dp, 0, 16.dp)
        }

        val btnVerify = MaterialButton(requireContext()).apply {
            text = "Verificar Código de Autenticador"
            textSize = 13f
            backgroundTintList = ColorStateList.valueOf(Color.parseColor("#00236F"))
            setTextColor(Color.WHITE)
            cornerRadius = (10 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp }
            setOnClickListener { verifyMfa() }
        }

        val btnEnroll = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Configurar / Enrolar Nuevo Autenticador"
            textSize = 13f
            setTextColor(Color.parseColor("#00236F"))
            strokeColor = ColorStateList.valueOf(Color.parseColor("#BFDBFE"))
            cornerRadius = (10 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { enrollMfa() }
        }

        layout.addView(tvTitle)
        layout.addView(tvDesc)
        layout.addView(btnVerify)
        layout.addView(btnEnroll)
        card.addView(layout)

        containerAdminItems.addView(card)
    }

    private fun verifyMfa(factorId: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = factorId ?: SupabaseModule.client.auth.mfa.retrieveFactorsForCurrentUser().firstOrNull { it.isVerified }?.id
                    ?: error("Configura primero un autenticador TOTP")

                showForm("Código del Autenticador", listOf(FormField("Código de 6 dígitos", numeric = true))) { values ->
                    SupabaseModule.client.auth.mfa.createChallengeAndVerify(id, values[0])
                    Toast.makeText(context, "Autenticación 2FA verificada con éxito", Toast.LENGTH_SHORT).show()
                    loadData()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun enrollMfa() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val factor = SupabaseModule.client.auth.mfa.enroll(FactorType.TOTP, "DAJHO ${System.currentTimeMillis()}") { issuer = "DAJHO" }

                showForm("Clave Secreta TOTP", listOf(FormField("Clave Secreta", value = factor.data.secret))) { _ ->
                    verifyMfa(factor.id)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show()
            }
        }
    }

    private val Int.dp: Int get() = (this * android.content.res.Resources.getSystem().displayMetrics.density).toInt()
}
