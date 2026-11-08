package com.example.dajho

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*

/** Business management screens share navigation and forms, while server RPCs enforce each operation. */
class ManagementFragment : Fragment() {
    companion object {
        fun newInstance(module: String) = ManagementFragment().apply {
            arguments = Bundle().apply { putString("module", module) }
        }
    }
    private val module get() = requireArguments().getString("module")!!
    private lateinit var content: LinearLayout
    private var business = 0L
    private var offset = 0
    private var loading = false
    private var rows = mutableListOf<JsonObject>()
    private val titles = mapOf(
        "cliente" to "Clientes", "proveedor" to "Proveedores", "categoria" to "Categorías",
        "temporada" to "Temporadas", "compra" to "Compras", "caja" to "Caja", "ventas" to "Historial de ventas",
        "movimiento_inventario" to "Movimientos de inventario", "planes" to "Planes y pagos",
        "acceso_soporte" to "Acceso de soporte", "catalogo" to "Catálogo"
    )

    private val moduleIcons = mapOf(
        "caja" to Pair(R.drawable.ic_balance, "#DCFCE7"),
        "cliente" to Pair(R.drawable.ic_clients, "#DBEAFE"),
        "proveedor" to Pair(R.drawable.ic_providers, "#E0E7FF"),
        "categoria" to Pair(R.drawable.ic_catalog, "#F3E8FF"),
        "temporada" to Pair(R.drawable.ic_inventory, "#FCE7F3"),
        "compra" to Pair(R.drawable.ic_cart, "#FFEDD5"),
        "ventas" to Pair(R.drawable.ic_sale, "#DCFCE7"),
        "movimiento_inventario" to Pair(R.drawable.ic_inventory, "#E0E7FF"),
        "planes" to Pair(R.drawable.ic_shopping_bag, "#E0F2FE"),
        "acceso_soporte" to Pair(R.drawable.ic_privacy, "#E0F2FE"),
        "catalogo" to Pair(R.drawable.ic_catalog, "#F3E8FF")
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 32.dp)
        }
        return ScrollView(requireContext()).apply {
            isFillViewport = true
            setBackgroundColor(Color.parseColor("#F8F9FA"))
            addView(content)
        }
    }

    override fun onViewCreated(view: View, state: Bundle?) {
        parentFragmentManager.setFragmentResultListener(HomeActivity.RESULTADO_CONEXION, viewLifecycleOwner) { _, _ -> load() }
        load()
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private fun load(more: Boolean = false, refresh: Boolean = false) {
        if (loading) return
        loading = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                business = BusinessSession.requireId()
                if (module == "catalogo") {
                    val variants = CatalogRepository.load(business, context = requireContext())
                    content.removeAllViews()
                    renderHeaderCard("Catálogo Virtual", "Muestra tus productos y comparte con tus clientes") {
                        addHeaderButton("Exportar catálogo (.xlsx)", R.drawable.ic_catalog, isPrimary = true) {
                            val businessName = (activity as? HomeActivity)?.findViewById<TextView>(R.id.tvBusinessName)?.text?.toString()?.replace(" ▾", "")?.trim() ?: "DAJHO"
                            val excelFile = ExcelReportExporter.generateCatalogReportXlsx(requireContext(), businessName, variants)
                            ExcelReportExporter.shareXlsxReport(requireContext(), excelFile, "Catálogo $businessName")
                        }
                        addHeaderButton("Imprimir catálogo", R.drawable.ic_printer) {
                            val lines = variants.map { "${it.producto?.nombre} · ${it.talla.orEmpty()} ${it.color.orEmpty()} · $${it.precio_venta}" }
                            ReceiptPrinter.print(requireActivity(), "Catálogo", lines.joinToString("\n"))
                        }
                    }
                    if (variants.isEmpty()) {
                        renderEmptyState("Sin productos en el catálogo", "Registra productos en el inventario para visualizarlos aquí.")
                    } else {
                        variants.forEach { variant -> renderCatalogCard(variant) }
                    }
                    return@launch
                }

                if (!more) { offset = 0; rows.clear() }
                val value = rpc("listar_gestion", buildJsonObject { put("p_modulo", module); put("p_offset", offset) }).decodeAs<JsonElement>()
                content.removeAllViews()

                if (module == "planes") { renderPlan(value.jsonObject); return@launch }

                val page = value.jsonArray.map { it.jsonObject }
                rows.addAll(page); offset += value.jsonArray.size

                val moduleTitle = titles[module] ?: module
                renderHeaderCard(moduleTitle, "Gestión y administración del módulo") {
                    addHeaderButton("Inicio", R.drawable.ic_home) {
                        (activity as? HomeActivity)?.replaceFragment(HomeFragment())
                    }

                    if (module in listOf("cliente", "proveedor", "categoria", "temporada") && (module == "cliente" || owner())) {
                        addHeaderButton("Agregar ${titles[module]?.lowercase()}", R.drawable.ic_plus, isPrimary = true) {
                            catalogForm()
                        }
                    }
                    if (module == "compra" && owner()) {
                        addHeaderButton("Registrar compra", R.drawable.ic_plus, isPrimary = true) { purchaseForm() }
                    }
                    if (module == "caja") {
                        val open = rows.firstOrNull { it.str("estado") == "abierta" }
                        if (open == null) {
                            addHeaderButton("Abrir caja", R.drawable.ic_balance, isPrimary = true) { cashForm(false) }
                        } else {
                            addHeaderButton("Cerrar caja #${open.str("id")}", R.drawable.ic_check, isPrimary = true) { cashForm(true) }
                        }
                    }
                }

                if (rows.isEmpty()) {
                    renderEmptyState("No hay registros", "Los datos de $moduleTitle aparecerán aquí al ser registrados.")
                } else {
                    rows.forEach { row -> renderRow(row) }
                }

                if (page.size == 200) {
                    content.addView(MaterialButton(requireContext()).apply {
                        text = "CARGAR MÁS REGISTROS"
                        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
                        setOnClickListener { load(true) }
                    })
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                content.removeAllViews()
                val moduleTitle = titles[module] ?: module
                renderHeaderCard(moduleTitle, "Gestión y administración del módulo") {
                    addHeaderButton("Inicio", R.drawable.ic_home) {
                        (activity as? HomeActivity)?.replaceFragment(HomeFragment())
                    }
                }
                if (rows.isEmpty()) {
                    renderEmptyState("No hay registros", "Los datos de $moduleTitle aparecerán aquí al ser registrados.")
                } else {
                    rows.forEach { row -> renderRow(row) }
                }
            } finally { loading = false }
        }
    }

    private fun owner() = (activity as? HomeActivity)?.esPropietario() == true

    private suspend fun rpc(name: String, data: JsonObject): io.github.jan.supabase.postgrest.result.PostgrestResult {
        val db = DajhoDatabase.getInstance(requireContext())
        val params = buildJsonObject {
            put("p_negocio", business)
            data.forEach { (key, value) -> put(key, value) }
        }

        if (name == "listar_gestion") {
            val modulo = data["p_modulo"]?.jsonPrimitive?.content ?: ""
            if (requireContext().hasInternetConnection()) {
                try {
                    val res = SupabaseModule.client.postgrest.rpc(name, params)
                    db.saveGestionItems(business, modulo, res.data)
                    return res
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    val localStr = db.getGestionItems(business, modulo)
                    if (localStr != null) {
                        return io.github.jan.supabase.postgrest.result.PostgrestResult(
                            data = localStr,
                            headers = io.ktor.http.Headers.Empty,
                            postgrest = SupabaseModule.client.postgrest
                        )
                    }
                    throw e
                }
            } else {
                val localStr = db.getGestionItems(business, modulo) ?: "[]"
                return io.github.jan.supabase.postgrest.result.PostgrestResult(
                    data = localStr,
                    headers = io.ktor.http.Headers.Empty,
                    postgrest = SupabaseModule.client.postgrest
                )
            }
        } else {
            if (requireContext().hasInternetConnection()) {
                try {
                    return SupabaseModule.client.postgrest.rpc(name, params)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    SyncManager.enqueueAndTrySync(requireContext(), business, name, params)
                    return io.github.jan.supabase.postgrest.result.PostgrestResult(
                        data = "{\"success\":true}",
                        headers = io.ktor.http.Headers.Empty,
                        postgrest = SupabaseModule.client.postgrest
                    )
                }
            } else {
                SyncManager.enqueueAndTrySync(requireContext(), business, name, params)
                return io.github.jan.supabase.postgrest.result.PostgrestResult(
                    data = "{\"success\":true}",
                    headers = io.ktor.http.Headers.Empty,
                    postgrest = SupabaseModule.client.postgrest
                )
            }
        }
    }

    // --- RENDERIZADO DE CABECERA Y TARJETAS ---

    private fun renderHeaderCard(title: String, subtitle: String, buildActions: HeaderActionsBuilder.() -> Unit) {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16.dp.toFloat()
            cardElevation = 2.dp.toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = 1.dp
            strokeColor = Color.parseColor("#E2E8F0")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp }
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)
        }

        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val iconData = moduleIcons[module] ?: Pair(R.drawable.ic_settings, "#E0F2FE")
        val iconContainer = MaterialCardView(requireContext()).apply {
            radius = 22.dp.toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor(iconData.second))
            strokeWidth = 0
            layoutParams = LinearLayout.LayoutParams(44.dp, 44.dp)
            addView(ImageView(requireContext()).apply {
                setImageResource(iconData.first)
                setPadding(10.dp, 10.dp, 10.dp, 10.dp)
                setColorFilter(Color.parseColor("#1E293B"))
            })
        }

        val titleLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 12.dp
            }
            addView(TextView(requireContext()).apply {
                text = title
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
            })
            addView(TextView(requireContext()).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
            })
        }

        topRow.addView(iconContainer)
        topRow.addView(titleLayout)
        layout.addView(topRow)

        val actionsLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 12.dp }
        }

        val scrollView = HorizontalScrollView(requireContext()).apply {
            isHorizontalScrollBarEnabled = false
            addView(actionsLayout)
        }

        val builder = HeaderActionsBuilder(actionsLayout)
        builder.buildActions()

        if (actionsLayout.childCount > 0) {
            layout.addView(scrollView)
        }

        card.addView(layout)
        content.addView(card)
    }

    inner class HeaderActionsBuilder(private val container: LinearLayout) {
        fun addHeaderButton(textLabel: String, iconRes: Int? = null, isPrimary: Boolean = false, action: () -> Unit) {
            val btn = if (isPrimary) {
                MaterialButton(requireContext()).apply {
                    text = textLabel
                    textSize = 12f
                    backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
                    setTextColor(Color.WHITE)
                    cornerRadius = 10.dp
                    iconRes?.let { setIconResource(it); iconTint = android.content.res.ColorStateList.valueOf(Color.WHITE) }
                }
            } else {
                MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = textLabel
                    textSize = 12f
                    setTextColor(Color.parseColor("#1E293B"))
                    strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
                    cornerRadius = 10.dp
                    iconRes?.let { setIconResource(it); iconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B")) }
                }
            }
            btn.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 8.dp }
            btn.setOnClickListener { action() }
            container.addView(btn)
        }
    }

    private fun renderEmptyState(title: String, subtitle: String) {
        val card = MaterialCardView(requireContext()).apply {
            radius = 16.dp.toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = 1.dp
            strokeColor = Color.parseColor("#E2E8F0")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 16.dp
                bottomMargin = 16.dp
            }
        }

        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24.dp, 32.dp, 24.dp, 32.dp)

            addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_search)
                setColorFilter(Color.parseColor("#94A3B8"))
                layoutParams = LinearLayout.LayoutParams(48.dp, 48.dp).apply { bottomMargin = 12.dp }
            })

            addView(TextView(requireContext()).apply {
                text = title
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
                gravity = Gravity.CENTER
            })

            addView(TextView(requireContext()).apply {
                text = subtitle
                textSize = 13f
                setTextColor(Color.parseColor("#64748B"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dp }
            })
        }

        card.addView(layout)
        content.addView(card)
    }

    private fun createBadge(textVal: String, bgHex: String, textHex: String): TextView {
        return TextView(requireContext()).apply {
            text = textVal.uppercase()
            textSize = 10f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor(textHex))
            setPadding(8.dp, 3.dp, 8.dp, 3.dp)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = 6.dp.toFloat()
            }
        }
    }

    private fun createCardContainer(): Pair<MaterialCardView, LinearLayout> {
        val card = MaterialCardView(requireContext()).apply {
            radius = 12.dp.toFloat()
            cardElevation = 1.dp.toFloat()
            setCardBackgroundColor(Color.WHITE)
            strokeWidth = 1.dp
            strokeColor = Color.parseColor("#E2E8F0")
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 10.dp }
        }
        val layout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(14.dp, 12.dp, 14.dp, 12.dp)
        }
        card.addView(layout)
        return Pair(card, layout)
    }

    // --- RENDERIZADO POR FILA Y MÓDULO ---

    private fun renderRow(row: JsonObject) {
        when (module) {
            "cliente", "proveedor", "categoria", "temporada" -> {
                val (card, layout) = createCardContainer()
                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val titleTv = TextView(requireContext()).apply {
                    text = row.str("nombre").ifEmpty { "Sin nombre" }
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                topRow.addView(titleTv)

                val contactInfo = listOf(row.str("telefono"), row.str("cedula"), row.str("identificacion"))
                    .filter { it.isNotBlank() }.joinToString(" · ")
                val dates = listOf(row.str("inicio"), row.str("fin"))
                    .filter { it.isNotBlank() }.joinToString(" a ")

                layout.addView(topRow)

                if (contactInfo.isNotEmpty()) {
                    layout.addView(TextView(requireContext()).apply {
                        text = "ID / Teléfono: $contactInfo"
                        textSize = 13f
                        setTextColor(Color.parseColor("#64748B"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 2.dp }
                    })
                }

                if (dates.isNotEmpty()) {
                    layout.addView(TextView(requireContext()).apply {
                        text = "Vigencia: $dates"
                        textSize = 12f
                        setTextColor(Color.parseColor("#94A3B8"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 2.dp }
                    })
                }

                if (module == "cliente" || owner()) {
                    val btnEdit = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Editar"
                        textSize = 11f
                        setTextColor(Color.parseColor("#1E293B"))
                        strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#CBD5E1"))
                        cornerRadius = 8.dp
                        setIconResource(R.drawable.ic_edit)
                        iconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 8.dp }
                        setOnClickListener { catalogForm(row) }
                    }
                    layout.addView(btnEdit)
                }

                content.addView(card)
            }

            "caja" -> {
                val (card, layout) = createCardContainer()
                val estado = row.str("estado")
                val isOpen = estado == "abierta"

                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val titleTv = TextView(requireContext()).apply {
                    text = "Caja #${row.str("id")}"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val badge = if (isOpen) {
                    createBadge("Abierta", "#DCFCE7", "#15803D")
                } else {
                    createBadge("Cerrada", "#F1F5F9", "#475569")
                }

                topRow.addView(titleTv)
                topRow.addView(badge)
                layout.addView(topRow)

                layout.addView(TextView(requireContext()).apply {
                    text = "Apertura: ${row.str("apertura")}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                })

                val details = cashRegisterSummary(row)
                layout.addView(TextView(requireContext()).apply {
                    text = details
                    textSize = 13f
                    setTextColor(Color.parseColor("#334155"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                })

                val dif = row.str("diferencia")
                if (dif.isNotEmpty() && dif != "0" && dif != "0.00") {
                    layout.addView(TextView(requireContext()).apply {
                        text = "Diferencia: $$dif"
                        textSize = 12f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        setTextColor(Color.parseColor("#EF4444"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 2.dp }
                    })
                }

                content.addView(card)
            }

            "compra" -> {
                val (card, layout) = createCardContainer()
                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                topRow.addView(TextView(requireContext()).apply {
                    text = "Compra #${row.str("id")}"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                topRow.addView(TextView(requireContext()).apply {
                    text = "$${row.str("total")}"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                })

                layout.addView(topRow)
                layout.addView(TextView(requireContext()).apply {
                    text = "Fecha: ${row.str("fecha")}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                })

                content.addView(card)
            }

            "ventas" -> {
                val (card, layout) = createCardContainer()
                val estado = row.str("estado")
                val isCompletada = estado == "completada"

                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                topRow.addView(TextView(requireContext()).apply {
                    text = "Venta #${row.str("id")}"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val badge = if (isCompletada) {
                    createBadge("Completada", "#DCFCE7", "#15803D")
                } else {
                    createBadge(estado.ifEmpty { "Anulada" }, "#FEE2E2", "#B91C1C")
                }
                topRow.addView(badge)
                layout.addView(topRow)

                val bottomRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                }

                bottomRow.addView(TextView(requireContext()).apply {
                    text = row.str("registrada")
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                bottomRow.addView(TextView(requireContext()).apply {
                    text = "$${row.str("total")}"
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(if (isCompletada) Color.parseColor("#10B981") else Color.parseColor("#94A3B8"))
                })

                layout.addView(bottomRow)

                if (owner() && isCompletada) {
                    val btnAnular = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                        text = "Anular Venta"
                        textSize = 11f
                        setTextColor(Color.parseColor("#EF4444"))
                        strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FCA5A5"))
                        cornerRadius = 8.dp
                        setIconResource(R.drawable.ic_trash)
                        iconTint = android.content.res.ColorStateList.valueOf(Color.parseColor("#EF4444"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 8.dp }
                        setOnClickListener {
                            showForm("Anular venta #${row.str("id")}", listOf(FormField("Motivo (mínimo 5 caracteres)")), { load() }) { values ->
                                require(values[0].length >= 5) { "Explica el motivo de anulación" }
                                rpc("anular_venta", buildJsonObject { put("p_venta", row["id"]!!); put("p_motivo", values[0]) })
                            }
                        }
                    }
                    layout.addView(btnAnular)
                }

                content.addView(card)
            }

            "movimiento_inventario" -> {
                val (card, layout) = createCardContainer()
                val tipo = row.str("tipo")

                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                topRow.addView(TextView(requireContext()).apply {
                    text = "Tipo: $tipo"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val badge = createBadge("Variante #${row.str("variante_id")}", "#DBEAFE", "#1D4ED8")
                topRow.addView(badge)
                layout.addView(topRow)

                val changeText = "Stock: ${row.str("stock_anterior")} → ${row.str("stock_nuevo")}"
                layout.addView(TextView(requireContext()).apply {
                    text = changeText
                    textSize = 13f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#2563EB"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                })

                val motive = row.str("motivo")
                if (motive.isNotEmpty()) {
                    layout.addView(TextView(requireContext()).apply {
                        text = "Motivo: $motive"
                        textSize = 12f
                        setTextColor(Color.parseColor("#64748B"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 2.dp }
                    })
                }

                layout.addView(TextView(requireContext()).apply {
                    text = row.str("fecha")
                    textSize = 11f
                    setTextColor(Color.parseColor("#94A3B8"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2.dp }
                })

                content.addView(card)
            }



            "acceso_soporte" -> {
                val (card, layout) = createCardContainer()
                val estado = row.str("estado")

                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                topRow.addView(TextView(requireContext()).apply {
                    text = "Solicitud #${row.str("id")}"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val badge = when (estado) {
                    "autorizado" -> createBadge(estado, "#DBEAFE", "#1D4ED8")
                    "solicitado" -> createBadge(estado, "#E0F2FE", "#00236F")
                    else -> createBadge(estado, "#F1F5F9", "#475569")
                }
                topRow.addView(badge)
                layout.addView(topRow)

                val motivo = row.str("motivo")
                if (motivo.isNotEmpty()) {
                    layout.addView(TextView(requireContext()).apply {
                        text = "Motivo: $motivo"
                        textSize = 13f
                        setTextColor(Color.parseColor("#334155"))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 4.dp }
                    })
                }

                layout.addView(TextView(requireContext()).apply {
                    text = "Vigente hasta: ${row.str("fin")}"
                    textSize = 12f
                    setTextColor(Color.parseColor("#64748B"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2.dp }
                })

                if (owner() && row["autorizado_propietario_id"] == JsonNull && estado in listOf("solicitado", "autorizado")) {
                    val btnAutorizar = MaterialButton(requireContext()).apply {
                        text = "Autorizar solicitud #${row.str("id")}"
                        textSize = 11f
                        backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
                        setTextColor(Color.WHITE)
                        cornerRadius = 8.dp
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply { topMargin = 8.dp }
                        setOnClickListener {
                            MaterialAlertDialogBuilder(requireContext()).setTitle("Autorizar acceso de soporte")
                                .setMessage("Permitirás el acceso temporal cuando también lo autorice un administrador de mayor jerarquía.")
                                .setNegativeButton("Cancelar", null).setPositiveButton("Autorizar") { _, _ ->
                                    viewLifecycleOwner.lifecycleScope.launch { try {
                                        SupabaseModule.client.postgrest.rpc("autorizar_soporte_propietario", buildJsonObject { put("p_acceso", row["id"]!!) }); load()
                                    } catch (e: Exception) { Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show() } }
                                }.show()
                        }
                    }
                    layout.addView(btnAutorizar)
                }

                content.addView(card)
            }
        }
    }

    private fun renderCatalogCard(variant: VarianteDto) {
        val (card, layout) = createCardContainer()

        val topRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        topRow.addView(TextView(requireContext()).apply {
            text = variant.producto?.nombre ?: "Producto"
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })

        topRow.addView(TextView(requireContext()).apply {
            text = "$${variant.precio_venta}"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#10B981"))
        })

        layout.addView(topRow)

        val attrText = listOfNotNull(variant.talla, variant.color).filter { it.isNotBlank() }.joinToString(" · ")
        val skuText = if (!variant.sku.isNullOrBlank()) "SKU: ${variant.sku}" else ""
        val subtitle = listOf(attrText, skuText).filter { it.isNotEmpty() }.joinToString("  |  ")

        if (subtitle.isNotEmpty()) {
            layout.addView(TextView(requireContext()).apply {
                text = subtitle
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dp }
            })
        }

        content.addView(card)
    }

    private fun renderPlan(data: JsonObject) {
        val subscription = data["suscripcion"]?.takeUnless { it == JsonNull }?.jsonObject
        val plans = data["planes"]!!.jsonArray
        val current = plans.firstOrNull { it.jsonObject["id"] == subscription?.get("plan_id") }?.jsonObject

        // Tarjeta de Plan Actual
        val currentPlanCard = MaterialCardView(requireContext()).apply {
            radius = 16.dp.toFloat()
            cardElevation = 2.dp.toFloat()
            setCardBackgroundColor(Color.parseColor("#1E293B"))
            strokeWidth = 0
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 16.dp }
        }

        val currentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dp, 16.dp, 16.dp, 16.dp)

            addView(TextView(requireContext()).apply {
                text = "TU PLAN ACTUAL"
                textSize = 11f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#94A3B8"))
            })

            val planName = (current?.str("nombre")).orEmpty().ifEmpty { "Sin suscripción" }
            addView(TextView(requireContext()).apply {
                text = planName
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 2.dp }
            })

            val estado = subscription?.str("estado").orEmpty()
            val fin = subscription?.str("fin").orEmpty()
            val statusLine = listOf(
                if (estado.isNotEmpty()) "Estado: $estado" else "",
                if (fin.isNotEmpty()) "Vigencia: $fin" else ""
            ).filter { it.isNotBlank() }.joinToString("  ·  ")

            if (statusLine.isNotEmpty()) {
                addView(TextView(requireContext()).apply {
                    text = statusLine
                    textSize = 13f
                    setTextColor(Color.parseColor("#E2E8F0"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                })
            }
        }
        currentPlanCard.addView(currentLayout)
        content.addView(currentPlanCard)

        // Título Planes Disponibles
        content.addView(TextView(requireContext()).apply {
            text = "Planes de suscripción"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#1E293B"))
            setPadding(0, 8.dp, 0, 8.dp)
        })

        plans.forEach { planElem ->
            val p = planElem.jsonObject
            val (card, layout) = createCardContainer()

            val topRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            topRow.addView(TextView(requireContext()).apply {
                text = p.str("nombre")
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })

            topRow.addView(TextView(requireContext()).apply {
                text = "$${p.str("precio_mensual_con_iva")} / mes"
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#2563EB"))
            })

            layout.addView(topRow)

            val users = p.str("max_usuarios").ifEmpty { "Sin límite" }
            val products = p.str("max_productos").ifEmpty { "Sin límite" }
            val clients = p.str("max_clientes").ifEmpty { "Sin límite" }

            layout.addView(TextView(requireContext()).apply {
                text = "Usuarios: $users  ·  Productos: $products  ·  Clientes: $clients"
                textSize = 12f
                setTextColor(Color.parseColor("#64748B"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 4.dp }
            })

            content.addView(card)
        }

        // Historial de Pagos
        val pagos = data["pagos"]!!.jsonArray
        if (pagos.isNotEmpty()) {
            content.addView(TextView(requireContext()).apply {
                text = "Historial de pagos"
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(Color.parseColor("#1E293B"))
                setPadding(0, 16.dp, 0, 8.dp)
            })

            pagos.forEach { pagoElem ->
                val p = pagoElem.jsonObject
                val (card, layout) = createCardContainer()

                val topRow = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                topRow.addView(TextView(requireContext()).apply {
                    text = "Pago #${p.str("id")}"
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(Color.parseColor("#1E293B"))
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })

                val estado = p.str("estado")
                val badge = if (estado == "completado" || estado == "aprobado") {
                    createBadge(estado, "#DCFCE7", "#15803D")
                } else {
                    createBadge(estado, "#E0F2FE", "#00236F")
                }
                topRow.addView(badge)
                layout.addView(topRow)

                val methodRef = listOf(p.str("metodo"), p.str("referencia")).filter { it.isNotBlank() }.joinToString(" · Ref: ")
                layout.addView(TextView(requireContext()).apply {
                    text = "$${p.str("total")}  ·  $methodRef"
                    textSize = 13f
                    setTextColor(Color.parseColor("#64748B"))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 4.dp }
                })

                content.addView(card)
            }
        }

        if (owner()) {
            val btnSubmit = MaterialButton(requireContext()).apply {
                text = "ENVIAR COMPROBANTE DE PAGO PRO"
                textSize = 13f
                backgroundTintList = android.content.res.ColorStateList.valueOf(Color.parseColor("#1E293B"))
                setTextColor(Color.WHITE)
                cornerRadius = 12.dp
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    52.dp
                ).apply { topMargin = 16.dp }
                setOnClickListener {
                    MaterialAlertDialogBuilder(requireContext()).setTitle("Método de pago")
                        .setItems(arrayOf("Transferencia", "QR")) { _, index ->
                            showForm("Referencia del pago realizado", listOf(FormField("Referencia")), { load() }) { values ->
                                require(values[0].isNotBlank())
                                rpc("solicitar_pago_pro", buildJsonObject { put("p_metodo", if (index == 0) "transferencia" else "qr"); put("p_referencia", values[0]) })
                            }
                        }.show()
                }
            }
            content.addView(btnSubmit)
        }
    }

    // --- FORMULARIOS Y DIÁLOGOS DE OPERACIÓN ---

    private fun catalogForm(row: JsonObject? = null) {
        val keys = when (module) {
            "cliente" -> listOf("nombre", "cedula", "telefono")
            "proveedor" -> listOf("nombre", "identificacion", "telefono")
            "temporada" -> listOf("nombre", "inicio", "fin")
            else -> listOf("nombre")
        }
        val formTitle = when (module) {
            "cliente" -> if (row == null) "Agregar Cliente" else "Editar Cliente"
            "proveedor" -> if (row == null) "Agregar Proveedor" else "Editar Proveedor"
            "categoria" -> if (row == null) "Agregar Categoría" else "Editar Categoría"
            "temporada" -> if (row == null) "Agregar Temporada" else "Editar Temporada"
            else -> if (row == null) "Agregar Registro" else "Editar Registro"
        }
        val fields = keys.map { key ->
            val label = when (key) {
                "nombre" -> "Nombre"
                "cedula" -> "Cédula / RUC"
                "identificacion" -> "Identificación / RUC"
                "telefono" -> "Teléfono de contacto"
                "inicio" -> "Fecha de inicio (AAAA-MM-DD, opcional)"
                "fin" -> "Fecha de fin (AAAA-MM-DD, opcional)"
                else -> key.replaceFirstChar(Char::uppercase)
            }
            FormField(label, row.str(key))
        }
        showForm(formTitle, fields, { load() }) { values ->
            require(values[0].isNotBlank()) { "Nombre requerido" }
            keys.forEachIndexed { i, key -> if (key in listOf("inicio", "fin") && values[i].isNotEmpty()) java.time.LocalDate.parse(values[i]) }
            rpc("guardar_catalogo", buildJsonObject {
                put("p_modulo", module); put("p_id", row?.get("id") ?: JsonNull)
                put("p_datos", buildJsonObject { keys.forEachIndexed { i, key -> put(key, values[i]) } })
            })
        }
    }

    private fun cashForm(close: Boolean) {
        showForm(if (close) "Cerrar caja" else "Abrir caja", listOf(FormField(if (close) "Efectivo contado" else "Efectivo inicial", numeric = true)), { load() }) {
            val amount = Money.amount(it[0]); require(amount.signum() >= 0) { "El monto no puede ser negativo" }
            DajhoDatabase.getInstance(requireContext()).setCajaEstado(business, !close)
            rpc(if (close) "cerrar_caja" else "abrir_caja", buildJsonObject {
                put(if (close) "p_monto_declarado" else "p_monto_inicial", Money.json(amount))
            })
        }
    }

    private fun purchaseForm() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val providers = rpc("listar_gestion", buildJsonObject { put("p_modulo", "proveedor") }).decodeAs<JsonArray>()
                require(providers.isNotEmpty()) { "Registra primero un proveedor" }
                val variants = CatalogRepository.load(business, context = requireContext())
                require(variants.isNotEmpty()) { "Registra primero un producto" }
                MaterialAlertDialogBuilder(requireContext()).setTitle("Proveedor")
                    .setItems(providers.map { it.jsonObject.str("nombre") }.toTypedArray()) { _, provider ->
                        choosePurchaseItem(providers[provider].jsonObject, variants, mutableListOf())
                    }.show()
            } catch (e: Exception) { Toast.makeText(context, e.userMessage(), Toast.LENGTH_LONG).show() }
        }
    }

    private fun choosePurchaseItem(provider: JsonObject, variants: List<VarianteDto>, items: MutableList<JsonObject>) {
        MaterialAlertDialogBuilder(requireContext()).setTitle("Seleccionar producto")
            .setItems(variants.map { "${it.producto?.nombre} · ${it.sku}" }.toTypedArray()) { _, index ->
                showForm("Entrada de ${variants[index].sku}", listOf(FormField("Cantidad", numeric = true), FormField("Costo unitario", numeric = true))) { values ->
                    val quantity = values[0].toInt(); require(quantity > 0)
                    val cost = Money.amount(values[1]); require(cost.signum() > 0)
                    items.add(buildJsonObject { put("variante_id", variants[index].id); put("cantidad", quantity); put("costo_unitario", Money.json(cost)) })
                    reviewPurchase(provider, variants, items)
                }
            }.show()
    }

    private fun reviewPurchase(provider: JsonObject, variants: List<VarianteDto>, items: MutableList<JsonObject>) {
        val summary = items.joinToString("\n") { item ->
            val variant = variants.firstOrNull { it.id == item["variante_id"]?.jsonPrimitive?.longOrNull }
            "${variant?.producto?.nombre} · ${variant?.sku}: ${item.str("cantidad")} × $${item.str("costo_unitario")}"
        }
        MaterialAlertDialogBuilder(requireContext()).setTitle("Compra a ${provider.str("nombre")}").setMessage(summary)
            .setNegativeButton("Agregar producto") { _, _ -> choosePurchaseItem(provider, variants, items) }
            .setPositiveButton("Confirmar compra") { _, _ ->
                val requestId = java.util.UUID.randomUUID().toString()
                showForm("Registrar compra revisada", emptyList(), { load() }) {
                    BusinessOperations.execute(requireContext(), business, "compra", buildJsonObject {
                        put("proveedor", provider["id"]!!); put("items", JsonArray(items))
                    }, requestId)
                }
            }.show()
    }
}

internal fun JsonObject?.str(key: String): String =
    this?.get(key)?.let { (it as? JsonPrimitive)?.contentOrNull } .orEmpty()
