package com.example.dajho

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
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
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioPerfilDto(
    val id: String? = null,
    val nombre: String? = null
)

class PerfilBottomSheet : BottomSheetDialogFragment() {

    private lateinit var tvUserEmail: TextView
    private lateinit var etNombreUsuario: TextInputEditText
    private lateinit var etNombreNegocio: TextInputEditText
    private lateinit var btnGuardarPerfil: MaterialButton
    private lateinit var btnCerrarSesion: MaterialButton

    private var userNegocioId: Long? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_perfil, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvUserEmail = view.findViewById(R.id.tvUserEmail)
        etNombreUsuario = view.findViewById(R.id.etNombreUsuario)
        etNombreNegocio = view.findViewById(R.id.etNombreNegocio)
        btnGuardarPerfil = view.findViewById(R.id.btnGuardarPerfil)
        btnCerrarSesion = view.findViewById(R.id.btnCerrarSesion)

        cargarDatosPerfil()

        btnGuardarPerfil.setOnClickListener {
            guardarCambios()
        }

        btnCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun cargarDatosPerfil() {
        val currentUser = SupabaseModule.client.auth.currentUserOrNull()
        if (currentUser != null) {
            tvUserEmail.text = currentUser.email ?: "Sin correo"
        }

        lifecycleScope.launch {
            try {
                if (currentUser == null) return@launch

                // 1. Obtener nombre del usuario de la tabla 'perfil'
                val usuario = SupabaseModule.client.from("perfil")
                    .select {
                        filter {
                            eq("id", currentUser.id)
                        }
                    }
                    .decodeSingleOrNull<UsuarioPerfilDto>()

                if (usuario?.nombre != null) {
                    etNombreUsuario.setText(usuario.nombre)
                }

                // 2. Obtener negocio_id y nombre del negocio
                val negocioUsuario = SupabaseModule.client.from("negocio_usuario")
                    .select {
                        filter {
                            eq("usuario_id", currentUser.id)
                        }
                    }
                    .decodeSingleOrNull<NegocioUsuarioDto>()

                userNegocioId = negocioUsuario?.negocio_id

                if (userNegocioId != null) {
                    val negocio = SupabaseModule.client.from("negocio")
                        .select {
                            filter {
                                eq("id", userNegocioId!!)
                            }
                        }
                        .decodeSingleOrNull<NegocioDto>()

                    if (negocio?.nombre != null) {
                        etNombreNegocio.setText(negocio.nombre)
                    }
                }

            } catch (e: Exception) {
                Log.e("PerfilBottomSheet", "Error al cargar perfil", e)
            }
        }
    }

    private fun guardarCambios() {
        val nuevoNombreUsuario = etNombreUsuario.text.toString().trim()
        val nuevoNombreNegocio = etNombreNegocio.text.toString().trim()

        val currentUser = SupabaseModule.client.auth.currentUserOrNull()
        if (currentUser == null) return

        btnGuardarPerfil.isEnabled = false

        lifecycleScope.launch {
            try {
                // 1. Actualizar tabla perfil
                if (nuevoNombreUsuario.isNotEmpty()) {
                    SupabaseModule.client.from("perfil").update({
                        set("nombre", nuevoNombreUsuario)
                    }) {
                        filter {
                            eq("id", currentUser.id)
                        }
                    }
                }

                // 2. Actualizar tabla negocio
                if (nuevoNombreNegocio.isNotEmpty() && userNegocioId != null) {
                    SupabaseModule.client.from("negocio").update({
                        set("nombre", nuevoNombreNegocio)
                    }) {
                        filter {
                            eq("id", userNegocioId!!)
                        }
                    }
                }

                Toast.makeText(requireContext(), "¡Perfil actualizado con éxito!", Toast.LENGTH_SHORT).show()

                // Notificar a HomeActivity para refrescar la cabecera
                parentFragmentManager.setFragmentResult(
                    "request_perfil_actualizado",
                    bundleOf("actualizado" to true)
                )

                dismiss()

            } catch (e: Exception) {
                Log.e("PerfilBottomSheet", "Error al guardar perfil", e)
                btnGuardarPerfil.isEnabled = true
                Toast.makeText(requireContext(), "Error al guardar: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            try {
                SupabaseModule.client.auth.signOut()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                Log.e("PerfilBottomSheet", "Error al cerrar sesión", e)
                Toast.makeText(requireContext(), "Error al cerrar sesión: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
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
