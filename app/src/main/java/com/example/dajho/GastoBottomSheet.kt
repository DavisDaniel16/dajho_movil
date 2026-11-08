package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class GastoInsertDto(
    val negocio_id: Long,
    val usuario_id: String,
    val categoria: String,
    val monto: Double,
    val descripcion: String? = null,
    val fecha: String
)

class GastoBottomSheet() : BottomSheetDialogFragment() {
    constructor(negocioId: Long) : this() {
        arguments = android.os.Bundle().apply {
            putString("negocioId", ScreenArgs.json.encodeToString(negocioId))
        }
    }
    private val negocioId: Long get() = ScreenArgs.json.decodeFromString(requireArguments().getString("negocioId")!!)


    private val requestId = java.util.UUID.randomUUID().toString()
    private lateinit var etCategoria: TextInputEditText
    private lateinit var etMonto: TextInputEditText
    private lateinit var etDescripcion: TextInputEditText
    private lateinit var btnGuardarGasto: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_gasto, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etCategoria = view.findViewById(R.id.etCategoria)
        etMonto = view.findViewById(R.id.etMonto)
        etDescripcion = view.findViewById(R.id.etDescripcion)
        btnGuardarGasto = view.findViewById(R.id.btnGuardarGasto)

        btnGuardarGasto.setOnClickListener {
            guardarGasto()
        }
    }

    private fun guardarGasto() {
        val categoria = etCategoria.text.toString().trim()
        val montoStr = etMonto.text.toString().trim()
        val descripcion = etDescripcion.text.toString().trim()

        if (categoria.isEmpty() || montoStr.isEmpty()) {
            AppFeedback.makeText(requireContext(), "Por favor completa la categoría y el monto", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            return
        }

        val monto = montoStr.toDoubleOrNull()
        if (monto == null || !monto.isFinite() || monto <= 0.0) {
            AppFeedback.makeText(requireContext(), "Ingresa un monto válido", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
            return
        }

        btnGuardarGasto.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val currentUser = SessionAccess.localUser()
                if (currentUser == null) {
                    btnGuardarGasto.isEnabled = true
                    AppFeedback.makeText(requireContext(), "Tu sesión no está disponible. Inicia sesión para continuar.", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                    return@launch
                }
                val nuevoGasto = GastoInsertDto(
                    negocio_id = negocioId,
                    usuario_id = currentUser.id,
                    categoria = categoria,
                    monto = monto,
                    descripcion = if (descripcion.isNotEmpty()) descripcion else null,
                    fecha = LocalDate.now().toString()
                )

                BusinessOperations.saveExpense(requireContext(), negocioId, buildJsonObject {
                    put("categoria", categoria)
                    put("monto", Money.json(Money.amount(montoStr)))
                    put("descripcion", descripcion.takeIf { it.isNotBlank() })
                }, requestId)

                AppFeedback.makeText(requireContext(), getString(R.string.sync_expense_saved), AppFeedback.LENGTH_SHORT, AppFeedback.Kind.PENDING).show()

                parentFragmentManager.setFragmentResult(
                    "request_balance_actualizado",
                    bundleOf("actualizado" to true)
                )

                dismiss()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("GastoBottomSheet", "Error al guardar gasto" + ": " + e.userMessage())
                btnGuardarGasto.isEnabled = true
                AppFeedback.makeText(requireContext(), e.userMessage("guardar el gasto"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
            }
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
