package com.example.dajho

import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

fun Fragment.editProduct(productId: Long, saved: () -> Unit) {
    viewLifecycleOwner.lifecycleScope.launch {
        try {
            val business = BusinessSession.requireId(requireContext())
            val db = DajhoDatabase.getInstance(requireContext())

            suspend fun options(module: String): List<JsonObject> {
                val params = buildJsonObject { put("p_negocio", business); put("p_modulo", module); put("p_offset", 0) }
                if (requireContext().hasInternetConnection()) {
                    try {
                        val page = SupabaseModule.client.postgrest.rpc("listar_gestion", params).decodeAs<JsonArray>().map { it.jsonObject }
                        db.saveGestionItems(business, module, ScreenArgs.json.encodeToString(page))
                        return page
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                }
                val localStr = db.getGestionItems(business, module) ?: "[]"
                return ScreenArgs.json.parseToJsonElement(localStr).jsonArray.map { it.jsonObject }
            }

            val categories = options("categoria")
            val seasons = options("temporada")

            MaterialAlertDialogBuilder(requireContext()).setTitle("Categoría")
                .setItems((listOf("Sin categoría") + categories.map { it.str("nombre") }).toTypedArray()) { _, category ->
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Temporada")
                        .setItems((listOf("Sin temporada") + seasons.map { it.str("nombre") }).toTypedArray()) { _, season ->
                            showForm("Editar producto", listOf(FormField("Nombre"), FormField("Marca")), saved) { values ->
                                require(values[0].isNotBlank())
                                val payload = buildJsonObject {
                                    put("p_negocio", business)
                                    put("p_producto", productId)
                                    put("p_nombre", values[0])
                                    put("p_marca", values[1])
                                    put("p_categoria", categories.getOrNull(category - 1)?.get("id") ?: JsonNull)
                                    put("p_temporada", seasons.getOrNull(season - 1)?.get("id") ?: JsonNull)
                                }
                                SyncManager.enqueueAndTrySync(requireContext(), business, "editar_producto", payload)
                            }
                        }.show()
                }.show()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.widget.Toast.makeText(context, e.userMessage(), android.widget.Toast.LENGTH_LONG).show()
        }
    }
}
