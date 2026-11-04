package com.example.dajho

import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class EditarPerfilBottomSheet(
    private val nombreActualUsuario: String,
    private val nombreActualNegocio: String,
    private val negocioId: Long?
) : BottomSheetDialogFragment() {

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

        lifecycleScope.launch {
            try {
                // 1. Actualizar perfil
                if (nuevoNombreUsuario.isNotEmpty()) {
                    SupabaseModule.client.from("perfil").update({
                        set("nombre", nuevoNombreUsuario)
                    }) {
                        filter {
                            eq("id", currentUser.id)
                        }
                    }
                }

                // 2. Actualizar negocio
                if (nuevoNombreNegocio.isNotEmpty() && negocioId != null) {
                    SupabaseModule.client.from("negocio").update({
                        set("nombre", nuevoNombreNegocio)
                    }) {
                        filter {
                            eq("id", negocioId)
                        }
                    }
                }

                Toast.makeText(requireContext(), "¡Perfil actualizado con éxito!", Toast.LENGTH_SHORT).show()

                parentFragmentManager.setFragmentResult(
                    "request_perfil_actualizado",
                    bundleOf(
                        "nuevo_nombre_usuario" to nuevoNombreUsuario,
                        "nuevo_nombre_negocio" to nuevoNombreNegocio
                    )
                )

                dismiss()

            } catch (e: Exception) {
                Log.e("EditarPerfil", "Error al actualizar perfil", e)
                btnGuardarCambios.isEnabled = true
                Toast.makeText(requireContext(), "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
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
