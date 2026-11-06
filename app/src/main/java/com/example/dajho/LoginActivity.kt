package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
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
    private lateinit var progressBar: ProgressBar

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
        progressBar = findViewById(R.id.progressBar)

        findViewById<TextView>(R.id.tvForgotPassword).setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                etEmail.error = "Ingresa tu correo para recuperar la contraseña"
            } else lifecycleScope.launch {
                try {
                    SupabaseModule.client.auth.resetPasswordForEmail(email, "dajho://confirm")
                    Toast.makeText(this@LoginActivity, "Si el correo está registrado recibirás un enlace de recuperación", Toast.LENGTH_LONG).show()
                } catch (e: Exception) { Toast.makeText(this@LoginActivity, e.userMessage(), Toast.LENGTH_LONG).show() }
            }
        }
        handleAuthLink(intent)
        if (intent.data == null) lifecycleScope.launch {
            SupabaseModule.client.auth.awaitInitialization()
            if (SupabaseModule.client.auth.currentUserOrNull() != null) {
                startActivity(Intent(this@LoginActivity, HomeActivity::class.java)); finish()
            }
        }

        // Manejar Deep Link de confirmación de correo electrónico
        intent.data?.let { uri ->
            if (uri.scheme == "dajho" && uri.host == "confirm") {
                val tokenHash = uri.getQueryParameter("token_hash")

                if (tokenHash != null) {
                    progressBar.visibility = View.VISIBLE
                    lifecycleScope.launch {
                        try {
                            SupabaseModule.client.auth.verifyEmailOtp(
                                type = OtpType.Email.EMAIL,
                                tokenHash = tokenHash
                            )
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this@LoginActivity,
                                "Correo confirmado. Ahora puedes iniciar sesión",
                                Toast.LENGTH_LONG
                            ).show()
                            intent.data = null // Limpiar el intent para evitar re-verificaciones
                        } catch (e: Exception) {
                            progressBar.visibility = View.GONE
                            Toast.makeText(
                                this@LoginActivity,
                                "Error al confirmar correo: ${e.userMessage()}",
                                Toast.LENGTH_LONG
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
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            lifecycleScope.launch {
                try {
                    SupabaseModule.client.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    val currentUser = SupabaseModule.client.auth.currentUserOrNull()
                    if (currentUser != null) {
                        // 1. Verificar si ya tiene negocio en negocio_usuario
                        val negocioUsuario = BusinessSession.current()

                        // 2. Si NO tiene negocio, crearlo usando los metadatos del registro
                        val profile = SupabaseModule.client.from("perfil").select {
                            filter { eq("id", currentUser.id) }
                        }.decodeSingle<UsuarioPerfilDto>()
                        if (negocioUsuario == null && !profile.es_administrador) {
                            val metadata = currentUser.userMetadata
                            val negocioNombre = metadata?.get("negocio_nombre")?.toString()?.replace("\"", "") ?: error("Falta el nombre del negocio en el registro")
                            val negocioIdentificacion = metadata?.get("negocio_identificacion")?.toString()?.replace("\"", "") ?: error("Falta la identificación del negocio en el registro")

                            try {
                                SupabaseModule.client.postgrest.rpc(
                                    "crear_negocio",
                                    buildJsonObject {
                                        put("p_nombre", negocioNombre)
                                        put("p_identificacion", negocioIdentificacion)
                                        put("p_direccion", "Sin dirección")
                                    }
                                )
                            } catch (rpcError: Exception) {
                                throw rpcError
                            }
                        }
                    }

                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true

                    Toast.makeText(this@LoginActivity, "¡Bienvenido!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@LoginActivity, HomeActivity::class.java)
                    startActivity(intent)
                    finish()
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    Toast.makeText(this@LoginActivity, "Error: ${e.userMessage()}", Toast.LENGTH_LONG).show()
                }
            }
        }

        tvRegister.setOnClickListener {
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
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
                                    Toast.makeText(this@LoginActivity, "Contraseña actualizada", Toast.LENGTH_LONG).show()
                                } catch (e: Exception) { input.error = e.userMessage() }
                            }
                        }
                    }
                    dialog.show()
                } else Toast.makeText(this, "Correo confirmado. Inicia sesión para continuar.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
