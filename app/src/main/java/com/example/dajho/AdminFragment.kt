package com.example.dajho

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.mfa.FactorType
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

class AdminFragment : Fragment() {
    private lateinit var content: LinearLayout
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 24, 32, 32) }
        return ScrollView(requireContext()).apply { addView(content) }
    }
    override fun onViewCreated(view: View, state: Bundle?) { load() }
    private fun text(value: String) { content.addView(TextView(requireContext()).apply { text = value; textSize = 17f; setPadding(0, 16, 0, 16) }) }
    private fun button(label: String, click: () -> Unit) { content.addView(MaterialButton(requireContext()).apply { text = label; setOnClickListener { click() } }) }
    private fun load() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                content.removeAllViews(); text("Administración DAJHO")
                if (!SupabaseModule.client.auth.mfa.status.active) {
                    text("Verifica tu segundo factor para acceder a la administración.")
                    button("Verificar autenticador") { verify() }
                    button("Configurar autenticador") { enroll() }
                    return@launch
                }
                val panel = SupabaseModule.client.postgrest.rpc("panel_administracion").decodeAs<JsonObject>()
                button("Actualizar") { load() }
                text("Negocios")
                panel["negocios"]!!.jsonArray.forEach { value ->
                    val n = value.jsonObject
                    text("${n.str("nombre")} · ${n.str("estado")}")
                    button(if (n.str("estado") == "activo") "Suspender ${n.str("nombre")}" else "Activar ${n.str("nombre")}") {
                        showForm("Confirmar cambio de estado", listOf(FormField("Escribe CONFIRMAR")), { load() }) { values ->
                            require(values[0] == "CONFIRMAR")
                            SupabaseModule.client.postgrest.rpc("admin_cambiar_estado_negocio", buildJsonObject { put("p_negocio", n["id"]!!); put("p_estado", if (n.str("estado") == "activo") "suspendido" else "activo") })
                        }
                    }
                    button("Solicitar soporte para ${n.str("nombre")}") {
                        showForm("Solicitud de soporte", listOf(FormField("Motivo")), { load() }) {
                            require(it[0].isNotBlank())
                            SupabaseModule.client.postgrest.rpc("admin_solicitar_soporte", buildJsonObject { put("p_negocio", n["id"]!!); put("p_motivo", it[0]) })
                        }
                    }
                }
                text("Pagos pendientes")
                panel["pagos"]!!.jsonArray.forEach { value ->
                    val p = value.jsonObject
                    text("Pago #${p.str("id")} · $${p.str("total")} · ${p.str("metodo")}\nReferencia: ${p.str("referencia")}")
                    button("Validar pago #${p.str("id")}") {
                        showForm("Validar tras comprobar el pago recibido", listOf(FormField("Escribe CONFIRMAR")), { load() }) {
                            require(it[0] == "CONFIRMAR")
                            SupabaseModule.client.postgrest.rpc("admin_validar_pago", buildJsonObject { put("p_pago", p["id"]!!) })
                        }
                    }
                }
                text("Solicitudes de soporte")
                panel["soporte"]!!.jsonArray.forEach { value ->
                    val s = value.jsonObject
                    text("Solicitud #${s.str("id")} · Negocio #${s.str("negocio_id")}\n${s.str("motivo")} · ${s.str("estado")}")
                    if (s["autorizado_jerarquia_id"] == JsonNull) button("Autorizar por jerarquía #${s.str("id")}") {
                        showForm("Autorizar acceso temporal", listOf(FormField("Fin (AAAA-MM-DDTHH:MM:SS-05:00)")), { load() }) {
                            val end = java.time.OffsetDateTime.parse(it[0]); require(end.isAfter(java.time.OffsetDateTime.now()))
                            SupabaseModule.client.postgrest.rpc("autorizar_soporte_jerarquia", buildJsonObject { put("p_acceso", s["id"]!!); put("p_fin", end.toString()) })
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                text(e.userMessage()); button("Reintentar") { load() }
            }
        }
    }
    private fun verify(factorId: String? = null) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val id = factorId ?: SupabaseModule.client.auth.mfa.retrieveFactorsForCurrentUser().firstOrNull { it.isVerified }?.id
                    ?: error("Configura primero un autenticador")
                showForm("Código del autenticador", listOf(FormField("Código de 6 dígitos", numeric = true)), { load() }) {
                    SupabaseModule.client.auth.mfa.createChallengeAndVerify(id, it[0])
                }
            } catch (e: Exception) { Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show() }
        }
    }
    private fun enroll() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val factor = SupabaseModule.client.auth.mfa.enroll(FactorType.TOTP, "DAJHO ${System.currentTimeMillis()}") { issuer = "DAJHO" }
                content.removeAllViews()
                text("Añade esta clave a tu aplicación de autenticación. No la compartas.")
                content.addView(TextView(requireContext()).apply { text = factor.data.secret; setTextIsSelectable(true) })
                button("Ya añadí la clave: verificar código") { verify(factor.id) }
            } catch (e: Exception) { Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show() }
        }
    }
}
