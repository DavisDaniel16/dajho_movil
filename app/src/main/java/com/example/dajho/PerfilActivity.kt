package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.coroutines.launch

class PerfilActivity : AppCompatActivity() {

    private lateinit var toolbar: Toolbar
    private lateinit var tvUserEmail: TextView
    private lateinit var tvNombreUsuario: TextView
    private lateinit var ivEditPerfil: ImageView
    private lateinit var btnComprarPlan: MaterialButton

    private lateinit var menuImpresora: LinearLayout
    private lateinit var menuTerminos: LinearLayout
    private lateinit var menuPrivacidad: LinearLayout
    private lateinit var btnCerrarSesion: MaterialButton

    private var userNegocioId: Long? = null
    private var nombreUsuarioActual: String = ""
    private var nombreNegocioActual: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_perfil)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        toolbar = findViewById(R.id.toolbar)
        tvUserEmail = findViewById(R.id.tvUserEmail)
        tvNombreUsuario = findViewById(R.id.tvNombreUsuario)
        ivEditPerfil = findViewById(R.id.ivEditPerfil)
        btnComprarPlan = findViewById(R.id.btnComprarPlan)

        menuImpresora = findViewById(R.id.menuImpresora)
        menuTerminos = findViewById(R.id.menuTerminos)
        menuPrivacidad = findViewById(R.id.menuPrivacidad)
        btnCerrarSesion = findViewById(R.id.btnCerrarSesion)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        cargarDatosPerfil()

        val esPropietario = intent.getBooleanExtra(
            HomeActivity.EXTRA_ES_PROPIETARIO,
            false
        )
        ivEditPerfil.visibility = View.VISIBLE

        ivEditPerfil.setOnClickListener {

            EditarPerfilBottomSheet(
                nombreActualUsuario = nombreUsuarioActual,
                nombreActualNegocio = nombreNegocioActual,
                negocioId = if (esPropietario) userNegocioId else null
            ).show(supportFragmentManager, "EditarPerfilBottomSheet")
        }

        // Escuchar resultado de edición de perfil
        supportFragmentManager.setFragmentResultListener("request_perfil_actualizado", this) { _, bundle ->
            val nuevoUsuario = bundle.getString("nuevo_nombre_usuario")
            val nuevoNegocio = bundle.getString("nuevo_nombre_negocio")
            if (!nuevoUsuario.isNullOrEmpty()) {
                nombreUsuarioActual = nuevoUsuario
                tvNombreUsuario.text = nuevoUsuario
            }
            if (!nuevoNegocio.isNullOrEmpty()) {
                nombreNegocioActual = nuevoNegocio
            }
            setResult(RESULT_OK)
        }

        btnComprarPlan.setOnClickListener {
            setResult(RESULT_OK, Intent().putExtra("abrir_modulo", "planes")); finish()
        }

        menuImpresora.setOnClickListener {
            startActivity(Intent(android.provider.Settings.ACTION_PRINT_SETTINGS))
        }

        menuTerminos.setOnClickListener {
            openLegal(BuildConfig.TERMS_URL)
        }

        menuPrivacidad.setOnClickListener {
            openLegal(BuildConfig.PRIVACY_URL)
        }

        val switchModoOscuro = findViewById<com.google.android.material.switchmaterial.SwitchMaterial>(R.id.switchModoOscuro)
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val systemIsDark = resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isDark = prefs.getBoolean("dark_mode", systemIsDark)
        switchModoOscuro?.isChecked = isDark

        switchModoOscuro?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("dark_mode", isChecked).apply()
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                if (isChecked) androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        btnCerrarSesion.setOnClickListener {
            cerrarSesion()
        }
    }

    private fun openLegal(url: String) {
        if (url.isBlank()) {
            Toast.makeText(this, "El enlace oficial aún no ha sido configurado por DAJHO", Toast.LENGTH_LONG).show()
        } else startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
    }

    private fun cargarDatosPerfil() {
        val currentUser = SupabaseModule.client.auth.currentUserOrNull()
        if (currentUser != null) {
            tvUserEmail.text = currentUser.email ?: "Sin correo"
        }

        lifecycleScope.launch {
            try {
                if (currentUser == null) return@launch

                if (BusinessSession.current() != null) {
                    val planData = SupabaseModule.client.postgrest.rpc("listar_gestion", kotlinx.serialization.json.buildJsonObject {
                        put("p_negocio", kotlinx.serialization.json.JsonPrimitive(BusinessSession.requireId()))
                        put("p_modulo", kotlinx.serialization.json.JsonPrimitive("planes"))
                    }).decodeAs<kotlinx.serialization.json.JsonObject>()
                    val subscription = planData["suscripcion"] as? kotlinx.serialization.json.JsonObject
                    findViewById<TextView>(R.id.tvPlanActual).text = "Plan: ${subscription?.str("estado") ?: "Sin suscripción"}"
                }
                // 1. Obtener nombre del usuario de la tabla 'perfil'
                val usuario = SupabaseModule.client.from("perfil")
                    .select {
                        filter {
                            eq("id", currentUser.id)
                        }
                    }
                    .decodeSingleOrNull<UsuarioPerfilDto>()

                if (usuario?.nombre != null) {
                    nombreUsuarioActual = usuario.nombre
                    tvNombreUsuario.text = usuario.nombre
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
                        nombreNegocioActual = negocio.nombre
                    }
                }

            } catch (e: Exception) {
                Log.e("PerfilActivity", "Error al cargar perfil" + ": " + e.userMessage())
            }
        }
    }

    private fun cerrarSesion() {
        lifecycleScope.launch {
            try {
                SupabaseModule.client.auth.signOut()
                BusinessSession.clear()
                val intent = Intent(this@PerfilActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
                startActivity(intent)
                finish()
            } catch (e: Exception) {
                Log.e("PerfilActivity", "Error al cerrar sesión" + ": " + e.userMessage())
                Toast.makeText(this@PerfilActivity, "Error al cerrar sesión: ${e.userMessage()}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
