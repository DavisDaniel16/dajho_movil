package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import java.util.Locale
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class DeudasDetalleBottomSheet() : BottomSheetDialogFragment() {
    constructor(clienteNombre: String, listaDeudas: List<DeudaDto>) : this() {
        arguments = android.os.Bundle().apply {
            putString("clienteNombre", ScreenArgs.json.encodeToString(clienteNombre))
            putString("listaDeudas", ScreenArgs.json.encodeToString(listaDeudas))
        }
    }
    private val clienteNombre: String get() = ScreenArgs.json.decodeFromString(requireArguments().getString("clienteNombre")!!)
    private val listaDeudas: List<DeudaDto> get() = ScreenArgs.json.decodeFromString(requireArguments().getString("listaDeudas")!!)


    private lateinit var tvClienteTitulo: TextView
    private lateinit var rvDeudasDetalle: RecyclerView
    private lateinit var tvTotalDeuda: TextView
    private lateinit var btnRegistrarAbono: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_detalle_deudas, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvClienteTitulo = view.findViewById(R.id.tvClienteTitulo)
        rvDeudasDetalle = view.findViewById(R.id.rvDeudasDetalle)
        tvTotalDeuda = view.findViewById(R.id.tvTotalDeuda)
        btnRegistrarAbono = view.findViewById(R.id.btnRegistrarAbono)

        tvClienteTitulo.text = "Historial de $clienteNombre"

        val adapter = DeudaDetalleAdapter(listaDeudas)
        rvDeudasDetalle.layoutManager = LinearLayoutManager(requireContext())
        rvDeudasDetalle.adapter = adapter

        val totalSaldo = listaDeudas.sumOf { it.saldo ?: it.monto_original ?: 0.0 }
        tvTotalDeuda.text = "$${String.format(Locale.US, "%.2f", totalSaldo)}"

        btnRegistrarAbono.setOnClickListener {
            com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Método de pago")
                .setItems(arrayOf("Efectivo", "Transferencia", "QR")) { _, method ->
                    val metodoStr = listOf("efectivo", "transferencia", "qr")[method]
                    val fields = mutableListOf(FormField("Monto del abono ($)", String.format(Locale.US, "%.2f", totalSaldo), numeric = true))
                    if (method != 0) fields.add(FormField("Referencia de pago"))

                    showForm("Registrar Abono a $clienteNombre", fields, {
                        parentFragmentManager.setFragmentResult("deudas_actualizadas", Bundle.EMPTY)
                        dismiss()
                    }) { values ->
                        val amount = Money.amount(values[0])
                        val maxDecimal = Money.decimal(totalSaldo)
                        require(amount.signum() > 0 && amount <= maxDecimal) {
                            "El monto del abono debe ser mayor que cero y no superar el saldo total de $${String.format(Locale.US, "%.2f", totalSaldo)}"
                        }
                        val ref = if (method != 0) values.getOrNull(1).orEmpty().trim() else ""
                        if (method != 0) require(ref.isNotBlank()) { "Referencia requerida" }

                        var restante = amount
                        val deudasOrdenadas = listaDeudas.sortedBy { it.id }

                        for (debt in deudasOrdenadas) {
                            if (restante.signum() <= 0) break
                            val debtSaldo = Money.decimal(requireNotNull(debt.saldo))
                            val paymentForThisDebt = if (restante < debtSaldo) restante else debtSaldo

                            BusinessOperations.execute(
                                requireContext(),
                                BusinessSession.requireId(),
                                "abono",
                                buildJsonObject {
                                    put("fiado", debt.id)
                                    put("monto", Money.json(paymentForThisDebt))
                                    put("metodo", metodoStr)
                                    put("referencia", if (ref.isNotBlank()) ref else null)
                                }
                            )

                            restante -= paymentForThisDebt
                        }
                    }
                }.show()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheetDialog = dialogInterface as BottomSheetDialog
            val bottomSheet = bottomSheetDialog.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }
}
