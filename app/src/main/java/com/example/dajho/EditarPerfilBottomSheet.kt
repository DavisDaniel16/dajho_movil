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
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch

class EditarPerfilBottomSheet() : BottomSheetDialogFragment() {
    constructor(nombreActualUsuario: String, nombreActualNegocio: String, negocioId: Long?) : this() {
        arguments = android.os.Bundle().apply {
            putString("nombreActualUsuario", ScreenArgs.json.encodeToString(nombreActualUsuario))
            putString("nombreActualNegocio", ScreenArgs.json.encodeToString(nombreActualNegocio))
            putString("negocioId", ScreenArgs.json.encodeToString(negocioId))
        }
    }
    private val nombreActualUsuario: String get() = ScreenArgs.json.decodeFromString(requireArguments().getString("nombreActualUsuario")!!)
    private val nombreActualNegocio: String get() = ScreenArgs.json.decodeFromString(requireArguments().getString("nombreActualNegocio")!!)
    private val negocioId: Long? get() = ScreenArgs.json.decodeFromString(requireArguments().getString("negocioId")!!)


    private lateinit var etNombreUsuario: TextInputEditText
    private lateinit var etNombreNegocio: TextInputEditText
    private lateinit var btnGuardarCambios: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_editar_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etNombreUsuario = view.findViewById(R.id.etNombreUsuario)
        etNombreNegocio = view.findViewById(R.id.etNombreNegocio)
        btnGuardarCambios = view.findViewById(R.id.btnGuardarCambios)

        etNombreUsuario.setText(nombreActualUsuario)
        etNombreNegocio.setText(nombreActualNegocio)
        etNombreNegocio.isEnabled = negocioId != null

        btnGuardarCambios.setOnClickListener {
            guardarCambios()
        }
    }

    private fun guardarCambios() {
        val nuevoNombreUsuario = etNombreUsuario.text.toString().trim()
        val nuevoNombreNegocio = etNombreNegocio.text.toString().trim()

        val currentUser = SupabaseModule.client.auth.currentUserOrNull()
        if (currentUser == null) return

        btnGuardarCambios.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val payload = buildJsonObject {
                    put("p_nombre", nuevoNombreUsuario)
                    put("p_negocio", negocioId)
                    put("p_nombre_negocio", nuevoNombreNegocio)
                }

                SyncManager.enqueueAndTrySync(requireContext(), negocioId ?: 0L, "actualizar_perfil", payload)

                AppFeedback.makeText(requireContext(), getString(R.string.sync_profile_saved), AppFeedback.LENGTH_SHORT, AppFeedback.Kind.PENDING).show()

                parentFragmentManager.setFragmentResult(
                    "request_perfil_actualizado",
                    bundleOf(
                        "nuevo_nombre_usuario" to nuevoNombreUsuario,
                        "nuevo_nombre_negocio" to nuevoNombreNegocio
                    )
                )

                dismiss()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("EditarPerfil", "Error al actualizar perfil" + ": " + e.userMessage())
                btnGuardarCambios.isEnabled = true
                AppFeedback.makeText(requireContext(), e.userMessage("guardar los cambios de perfil"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
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
