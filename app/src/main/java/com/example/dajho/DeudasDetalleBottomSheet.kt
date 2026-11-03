package com.example.dajho

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

class DeudasDetalleBottomSheet(
    private val clienteNombre: String,
    private val listaDeudas: List<DeudaDto>
) : BottomSheetDialogFragment() {

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
            Toast.makeText(
                requireContext(),
                "Registrar Abono a $clienteNombre (Próximamente)",
                Toast.LENGTH_SHORT
            ).show()
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
