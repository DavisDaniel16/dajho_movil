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
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class UsuarioPerfilDto(
    val es_administrador: Boolean = false,
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

        viewLifecycleOwner.lifecycleScope.launch {
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
                val negocioUsuario = BusinessSession.current()

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
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("PerfilBottomSheet", "Error al cargar perfil" + ": " + e.userMessage())
            }
        }
    }

    private fun guardarCambios() {
        val nuevoNombreUsuario = etNombreUsuario.text.toString().trim()
        val nuevoNombreNegocio = etNombreNegocio.text.toString().trim()

        val currentUser = SupabaseModule.client.auth.currentUserOrNull()
        if (currentUser == null) return

        btnGuardarPerfil.isEnabled = false

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val payload = buildJsonObject {
                    put("p_nombre", nuevoNombreUsuario)
                    put("p_negocio", userNegocioId)
                    put("p_nombre_negocio", nuevoNombreNegocio)
                }

                SyncManager.enqueueAndTrySync(requireContext(), userNegocioId ?: 0L, "actualizar_perfil", payload)

                Toast.makeText(requireContext(), "¡Perfil actualizado con éxito!", Toast.LENGTH_SHORT).show()

                // Notificar a HomeActivity para refrescar la cabecera
                parentFragmentManager.setFragmentResult(
                    "request_perfil_actualizado",
                    bundleOf("actualizado" to true)
                )

                dismiss()

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("PerfilBottomSheet", "Error al guardar perfil" + ": " + e.userMessage())
                btnGuardarPerfil.isEnabled = true
                Toast.makeText(requireContext(), "Error al guardar: ${e.userMessage()}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun cerrarSesion() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                SupabaseModule.client.auth.signOut()
                BusinessSession.clear()
                val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                requireActivity().finish()
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("PerfilBottomSheet", "Error al cerrar sesión" + ": " + e.userMessage())
                Toast.makeText(requireContext(), "Error al cerrar sesión: ${e.userMessage()}", Toast.LENGTH_SHORT).show()
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
