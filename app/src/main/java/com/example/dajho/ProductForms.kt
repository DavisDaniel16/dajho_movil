package com.example.dajho

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

fun Fragment.editProduct(productId: Long, saved: () -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            val business = BusinessSession.requireId()
            val product = SupabaseModule.client.from("producto").select { filter { eq("id", productId); eq("negocio_id", business) } }.decodeSingle<JsonObject>()
            suspend fun options(module: String): List<JsonObject> {
                val all = mutableListOf<JsonObject>()
                do {
                    val page = SupabaseModule.client.postgrest.rpc("listar_gestion", buildJsonObject { put("p_negocio", business); put("p_modulo", module); put("p_offset", all.size) }).decodeAs<JsonArray>().map { it.jsonObject }
                    all.addAll(page)
                } while (page.size == 200)
                return all
            }
            val categories = options("categoria")
            val seasons = options("temporada")
            MaterialAlertDialogBuilder(requireContext()).setTitle("Categoría")
                .setItems((listOf("Sin categoría") + categories.map { it.str("nombre") }).toTypedArray()) { _, category ->
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Temporada")
                        .setItems((listOf("Sin temporada") + seasons.map { it.str("nombre") }).toTypedArray()) { _, season ->
                            showForm("Editar producto", listOf(FormField("Nombre", product.str("nombre")), FormField("Marca", product.str("marca"))), saved) { values ->
                                require(values[0].isNotBlank())
                                SupabaseModule.client.postgrest.rpc("editar_producto", buildJsonObject {
                                    put("p_negocio", business); put("p_producto", productId)
                                    put("p_nombre", values[0]); put("p_marca", values[1])
                                    put("p_categoria", categories.getOrNull(category - 1)?.get("id") ?: JsonNull)
                                    put("p_temporada", seasons.getOrNull(season - 1)?.get("id") ?: JsonNull)
                                })
                            }
                        }.show()
                }.show()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.widget.Toast.makeText(context, e.userMessage(), android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
