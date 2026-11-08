package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.Locale

class DetalleVentaBottomSheet() : BottomSheetDialogFragment() {
    constructor(tituloVenta: String, detalles: List<DetalleVentaBalanceDto>) : this() {
        arguments = Bundle().apply {
            putString("tituloVenta", ScreenArgs.json.encodeToString(tituloVenta))
            putString("detalles", ScreenArgs.json.encodeToString(detalles))
        }
    }

    private val tituloVenta: String get() = ScreenArgs.json.decodeFromString(requireArguments().getString("tituloVenta")!!)
    private val detalles: List<DetalleVentaBalanceDto> get() = ScreenArgs.json.decodeFromString(requireArguments().getString("detalles")!!)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.bottom_sheet_detalle_venta, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tvTituloDetalleVenta).text = tituloVenta
        val lista = view.findViewById<LinearLayout>(R.id.layoutDetallesVenta)

        val negocioNombre = (activity as? HomeActivity)?.findViewById<TextView>(R.id.tvBusinessName)?.text?.toString()?.replace(" ▾", "")?.trim() ?: "DAJHO"
        val comprobanteTexto = ReceiptFormatter.formatTextReceipt(negocioNombre, tituloVenta, detalles = detalles)

        // Botones de Acción
        val layoutBotones = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 0, 0, 16) }
        }

        val btnImprimir = MaterialButton(requireContext()).apply {
            text = "Imprimir"
            setIconResource(R.drawable.ic_printer)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 }
            setOnClickListener { ReceiptPrinter.print(requireActivity(), tituloVenta, comprobanteTexto) }
        }

        val btnCompartir = MaterialButton(requireContext()).apply {
            text = "Reporte Excel (.xlsx)"
            setIconResource(R.drawable.ic_catalog)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 8 }
            setOnClickListener {
                val excelFile = ExcelReportExporter.generateSalesReportXlsx(
                    requireContext(),
                    tituloVenta,
                    negocioNombre,
                    null,
                    detalles
                )
                ExcelReportExporter.shareXlsxReport(requireContext(), excelFile, "Reporte $tituloVenta")
            }
        }

        layoutBotones.addView(btnImprimir)
        layoutBotones.addView(btnCompartir)
        lista.addView(layoutBotones)

        if (detalles.isEmpty()) {
            lista.addView(TextView(requireContext()).apply {
                text = "Esta venta no tiene productos registrados"
                textSize = 14f
                setPadding(0, 16, 0, 16)
            })
            return
        }

        var totalCalculado = 0.0

        detalles.forEach { detalle ->
            val nombre = detalle.variante?.producto?.nombre ?: "Producto"
            val cantidad = detalle.cantidad ?: 1
            val precio = detalle.precio_unitario ?: 0.0
            val subtotal = cantidad * precio
            totalCalculado += subtotal

            val talla = detalle.variante?.talla?.takeIf { it.isNotBlank() }
            val color = detalle.variante?.color?.takeIf { it.isNotBlank() }
            val sku = detalle.variante?.sku?.takeIf { it.isNotBlank() }
            val specs = listOfNotNull(
                talla?.let { "Talla: $it" },
                color?.let { "Color: $it" },
                sku?.let { "SKU: $it" }
            ).joinToString(" | ")

            val itemCard = MaterialCardView(requireContext()).apply {
                radius = 16f
                strokeWidth = 1
                strokeColor = android.graphics.Color.parseColor("#E2E8F0")
                cardElevation = 0f
                setCardBackgroundColor(android.graphics.Color.parseColor("#F8F9FA"))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { setMargins(0, 0, 0, 12) }

                val container = LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(16, 14, 16, 14)

                    val topRow = LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL

                        val tvProd = TextView(requireContext()).apply {
                            text = "${cantidad}x  $nombre"
                            textSize = 15f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.parseColor("#1E293B"))
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        }

                        val tvSub = TextView(requireContext()).apply {
                            text = "$${String.format(Locale.US, "%.2f", subtotal)}"
                            textSize = 15f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.parseColor("#10B981"))
                        }

                        addView(tvProd)
                        addView(tvSub)
                    }
                    addView(topRow)

                    if (specs.isNotEmpty()) {
                        val tvSpecs = TextView(requireContext()).apply {
                            text = specs
                            textSize = 12f
                            setTextColor(android.graphics.Color.parseColor("#64748B"))
                            setPadding(0, 4, 0, 0)
                        }
                        addView(tvSpecs)
                    }

                    val tvUnitPrice = TextView(requireContext()).apply {
                        text = "Precio unitario: $${String.format(Locale.US, "%.2f", precio)}"
                        textSize = 12f
                        setTextColor(android.graphics.Color.parseColor("#94A3B8"))
                        setPadding(0, 2, 0, 0)
                    }
                    addView(tvUnitPrice)
                }

                addView(container)
            }

            lista.addView(itemCard)
        }

        // Card Total
        val totalCard = MaterialCardView(requireContext()).apply {
            radius = 16f
            setCardBackgroundColor(android.graphics.Color.parseColor("#1E293B"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, 8, 0, 0) }

            val totalContainer = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(20, 16, 20, 16)

                val tvTotalLabel = TextView(requireContext()).apply {
                    text = "TOTAL VENTA"
                    textSize = 15f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.WHITE)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }

                val tvTotalVal = TextView(requireContext()).apply {
                    text = "$${String.format(Locale.US, "%.2f", totalCalculado)}"
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    setTextColor(android.graphics.Color.parseColor("#34D399"))
                }

                addView(tvTotalLabel)
                addView(tvTotalVal)
            }

            addView(totalContainer)
        }

        lista.addView(totalCard)
    }
}
