package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.OtpType
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.handleDeeplinks
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class LoginActivity : AppCompatActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var btnLogin: Button
    private lateinit var tvRegister: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnLogin = findViewById(R.id.btnLogin)
        tvRegister = findViewById(R.id.tvRegister)

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Ingresa tu correo para recuperar la contraseña"
            } else lifecycleScope.launch {
                try {
                    SupabaseModule.client.auth.resetPasswordForEmail(email, "dajho://confirm")
                    AppFeedback.makeText(this@LoginActivity, "Si el correo está registrado recibirás un enlace de recuperación", AppFeedback.LENGTH_LONG, AppFeedback.Kind.SUCCESS).show()
                } catch (e: Exception) { AppFeedback.makeText(this@LoginActivity, e.userMessage("solicitar el enlace de acceso"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show() }
            }
        }
        handleAuthLink(intent)
        if (intent.data == null) lifecycleScope.launch {
            if (SessionAccess.localUser() != null) {
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java)); finish()
            }
        }

        // Manejar Deep Link de confirmación de correo electrónico
        intent.data?.let { uri ->
            if (uri.scheme == "dajho" && uri.host == "confirm") {
                val tokenHash = uri.getQueryParameter("token_hash")

                if (tokenHash != null) {
                    lifecycleScope.launch {
                        try {
                            SupabaseModule.client.auth.verifyEmailOtp(
                                type = OtpType.Email.EMAIL,
                                tokenHash = tokenHash
                            )
                            AppFeedback.makeText(
                                this@LoginActivity,
                                "Correo confirmado. Ahora puedes iniciar sesión",
                                AppFeedback.LENGTH_LONG, AppFeedback.Kind.SUCCESS
                            ).show()
                            intent.data = null // Limpiar el intent para evitar re-verificaciones
                        } catch (e: Exception) {
                            AppFeedback.makeText(
                                this@LoginActivity,
                                e.userMessage("confirmar el correo"),
                                AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR
                            ).show()
                        }
                    }
                }
            }
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            if (email.isEmpty() || password.isEmpty()) {
                AppFeedback.makeText(this, "Por favor completa todos los campos", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                return@setOnClickListener
            }

            btnLogin.isEnabled = false

            lifecycleScope.launch {
                try {
                    retryLoginOnNetworkFailure {
                        SupabaseModule.client.auth.signInWith(Email) {
                            this.email = email
                            this.password = password
                        }
                    }

                    val currentUser = SupabaseModule.client.auth.currentUserOrNull()
                    if (currentUser != null) {
                        // 1. Verificar si ya tiene negocio en negocio_usuario
                        val negocioUsuario = BusinessSession.current()

                        // 2. Si NO tiene negocio, crearlo usando los metadatos del registro
                        val profile = runCatching {
                            SupabaseModule.client.from("perfil").select {
                                filter { eq("id", currentUser.id) }
                            }.decodeSingleOrNull<UsuarioPerfilDto>()
                        }.getOrNull()

                        if (negocioUsuario == null && profile?.es_administrador != true) {
                            val metadata = currentUser.userMetadata
                            val negocioNombre = metadata?.get("negocio_nombre")?.toString()?.replace("\"", "")
                            val negocioIdentificacion = metadata?.get("negocio_identificacion")?.toString()?.replace("\"", "")

                            if (!negocioNombre.isNullOrBlank() && !negocioIdentificacion.isNullOrBlank()) {
                                runCatching {
                                    SupabaseModule.client.postgrest.rpc(
                                        "crear_negocio",
                                        buildJsonObject {
                                            put("p_nombre", negocioNombre)
                                            put("p_identificacion", negocioIdentificacion)
                                            put("p_direccion", "Sin dirección")
                                        }
                                    )
                                }
                            }
                        }
                    }

                    btnLogin.isEnabled = true

                    SupabaseModule.client.auth.currentUserOrNull()?.id?.let(AppSessionLock::onLogin)
                    AppFeedback.onNextScreen(HomeActivity::class.java, R.string.feedback_welcome)
                    val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    btnLogin.isEnabled = true
                    AppFeedback.makeText(this@LoginActivity, e.userMessage("iniciar sesión"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
                }
            }
        }

        tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        window.decorView.post { AppFeedback.showPending(this) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthLink(intent)
    }

    private fun handleAuthLink(link: Intent) {
        val uri = link.data ?: return
        if (uri.scheme != "dajho" || uri.host != "confirm") return
        SupabaseModule.client.handleDeeplinks(link) {
            runOnUiThread {
                if (uri.fragment?.contains("type=recovery") == true) {
                    val input = TextInputEditText(this).apply { hint = "Nueva contraseña (mínimo 8 caracteres)"; inputType = 129 }
                    val dialog = com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                        .setTitle("Restablecer contraseña").setView(input).setNegativeButton("Cancelar", null).setPositiveButton("Guardar", null).create()
                    dialog.setOnShowListener {
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val password = input.text.toString()
                            if (password.length < 8) input.error = "Usa al menos 8 caracteres"
                            else lifecycleScope.launch {
                                try {
                                    SupabaseModule.client.auth.updateUser { this.password = password }
                                    dialog.dismiss()
                                    AppFeedback.makeText(this@LoginActivity, "Contraseña actualizada", AppFeedback.LENGTH_LONG, AppFeedback.Kind.SUCCESS).show()
                                } catch (e: Exception) { input.error = e.userMessage("restablecer la contraseña") }
                            }
                        }
                    }
                    dialog.show()
                } else AppFeedback.makeText(this, "Correo confirmado. Inicia sesión para continuar.", AppFeedback.LENGTH_LONG, AppFeedback.Kind.SUCCESS).show()
            }
        }
    }
}
