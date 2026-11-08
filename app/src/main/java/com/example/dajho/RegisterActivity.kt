package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RegisterActivity : AppCompatActivity() {

    private lateinit var etName: TextInputEditText
    private lateinit var etBusinessName: TextInputEditText
    private lateinit var etBusinessIdentificacion: TextInputEditText
    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnRegister: Button
    private lateinit var tvLoginRedirect: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        etName = findViewById(R.id.etName)
        etBusinessName = findViewById(R.id.etBusinessName)
        etBusinessIdentificacion = findViewById(R.id.etBusinessIdentificacion)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnRegister = findViewById(R.id.btnRegister)
        tvLoginRedirect = findViewById(R.id.tvLoginRedirect)
        progressBar = findViewById(R.id.progressBar)

        btnRegister.setOnClickListener {
            val name = etName.text.toString().trim()
            val businessName = etBusinessName.text.toString().trim()
            val businessIdentificacion = etBusinessIdentificacion.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (name.isEmpty() || businessName.isEmpty() || businessIdentificacion.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                AppFeedback.makeText(this, "Por favor completa todos los campos", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                AppFeedback.makeText(this, "Las contraseñas no coinciden", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                return@setOnClickListener
            }

            if (password.length < 8) {
                AppFeedback.makeText(this, "La contraseña debe tener al menos 8 caracteres", AppFeedback.LENGTH_SHORT, AppFeedback.Kind.ERROR).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            lifecycleScope.launch {
                try {
                    // Registro guardando datos del negocio en los metadatos del usuario
                    SupabaseModule.client.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        this.data = buildJsonObject {
                            put("nombre", name)
                            put("negocio_nombre", businessName)
                            put("negocio_identificacion", businessIdentificacion)
                        }
                    }

                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true

                    AppFeedback.onNextScreen(LoginActivity::class.java, R.string.feedback_account_created)
                    finish()
                } catch (e: Exception) {
                    progressBar.visibility = View.GONE
                    btnRegister.isEnabled = true
                    AppFeedback.makeText(this@RegisterActivity, e.userMessage("crear la cuenta"), AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
                }
            }
        }

        tvLoginRedirect.setOnClickListener {
            finish()
        }
    }
}
