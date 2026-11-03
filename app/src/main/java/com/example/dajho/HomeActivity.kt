package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
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
    private lateinit var bottomNav: BottomNavigationView

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
        bottomNav = findViewById(R.id.bottomNav)

        // Cargar datos del negocio en la cabecera compartida
        checkAuthAndLoadData()

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
                    // TODO: BalanceFragment
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
                    tvRole.text = negocioUsuario.rol?.replaceFirstChar { it.uppercase() } ?: "Propietario"

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
                    tvRole.text = "Sin Rol"
                }
            } catch (e: Exception) {
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
