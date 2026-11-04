package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class NegocioUsuarioDto(
    val negocio_id: Long? = null,
    val rol: String? = null,
    val usuario_id: String? = null
)

@Serializable
data class NegocioDto(
    val id: Long? = null,
    val nombre: String? = null
)

class HomeActivity : AppCompatActivity() {

    private lateinit var tvBusinessName: TextView
    private lateinit var tvRole: TextView
    private lateinit var ivSettings: ImageView
    private lateinit var bottomNav: BottomNavigationView
    private var rolActual: String = ""

    companion object {
        const val RESULTADO_ROL = "rol_actualizado"
        const val EXTRA_ES_PROPIETARIO = "es_propietario"
    }

    fun esPropietario(): Boolean =
        rolActual.equals("propietario", ignoreCase = true)

    private fun actualizarRol(rol: String?) {
        rolActual = rol?.trim().orEmpty()
        tvRole.text = rolActual.replaceFirstChar { it.uppercase() }.ifEmpty { "Sin Rol" }
        supportFragmentManager.setFragmentResult(RESULTADO_ROL, Bundle.EMPTY)
    }

    private val perfilLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            checkAuthAndLoadData()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvBusinessName = findViewById(R.id.tvBusinessName)
        tvRole = findViewById(R.id.tvRole)
        ivSettings = findViewById(R.id.ivSettings)
        bottomNav = findViewById(R.id.bottomNav)

        // Cargar datos del negocio en la cabecera compartida
        checkAuthAndLoadData()

        // Abrir PerfilActivity al tocar la rueda dentada
        ivSettings.setOnClickListener {
            val intent = Intent(this, PerfilActivity::class.java).apply {
                putExtra(EXTRA_ES_PROPIETARIO, esPropietario())
            }
            perfilLauncher.launch(intent)
        }

        // Cargar HomeFragment por defecto al iniciar
        if (savedInstanceState == null) {
            replaceFragment(HomeFragment())
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_inicio -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_balance -> {
                    replaceFragment(BalanceFragment())
                    true
                }
                R.id.nav_venta -> {
                    replaceFragment(VentaFragment())
                    true
                }
                R.id.nav_deudas -> {
                    replaceFragment(DeudasFragment())
                    true
                }
                R.id.nav_inventario -> {
                    replaceFragment(InventarioFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun checkAuthAndLoadData() {
        val session = SupabaseModule.client.auth.currentSessionOrNull()
        if (session == null || session.user == null) {
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
            finish()
            return
        }
        val userId = session.user!!.id
        actualizarRol(null)

        lifecycleScope.launch {
            try {
                val negocioUsuario = SupabaseModule.client.from("negocio_usuario")
                    .select {
                        filter {
                            eq("usuario_id", userId)
                        }
                    }
                    .decodeSingleOrNull<NegocioUsuarioDto>()

                if (negocioUsuario != null) {
                    actualizarRol(negocioUsuario.rol)

                    if (negocioUsuario.negocio_id != null) {
                        val negocio = SupabaseModule.client.from("negocio")
                            .select {
                                filter {
                                    eq("id", negocioUsuario.negocio_id)
                                }
                            }
                            .decodeSingleOrNull<NegocioDto>()

                        if (negocio?.nombre != null) {
                            tvBusinessName.text = "${negocio.nombre} ▾"
                        } else {
                            tvBusinessName.text = "Mi Negocio ▾"
                        }
                    }
                } else {
                    tvBusinessName.text = "Sin Negocio ▾"
                    actualizarRol(null)
                }
            } catch (e: Exception) {
                actualizarRol(null)
                Log.e("HomeActivity", "Error al consultar Supabase", e)
                Toast.makeText(this@HomeActivity, "Error de red: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                tvBusinessName.text = "Error al cargar ▾"
                tvRole.text = "Desconocido"
            }
        }
    }

    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
