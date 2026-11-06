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
    private var contextoListo = false
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
            result.data?.getStringExtra("abrir_modulo")?.let { replaceFragment(ManagementFragment.newInstance(it)) }
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
        savedInstanceState?.getLong("negocio_seleccionado")?.takeIf { it > 0 }?.let(BusinessSession::select)
        bottomNav.isEnabled = false
        checkAuthAndLoadData()
        tvBusinessName.setOnClickListener { seleccionarNegocio() }
        findViewById<android.view.View>(R.id.ivUser).setOnClickListener { abrirModulos() }
        findViewById<android.view.View>(R.id.ivSearch).setOnClickListener { replaceFragment(VentaFragment()) }

        // Abrir PerfilActivity al tocar la rueda dentada
        ivSettings.setOnClickListener {
            val intent = Intent(this, PerfilActivity::class.java).apply {
                putExtra(EXTRA_ES_PROPIETARIO, esPropietario())
            }
            perfilLauncher.launch(intent)
        }

        // Cargar HomeFragment por defecto al iniciar


        bottomNav.setOnItemSelectedListener { item ->
            if (!contextoListo) return@setOnItemSelectedListener false
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

    override fun onSaveInstanceState(outState: Bundle) {
        BusinessSession.selectedId?.let { outState.putLong("negocio_seleccionado", it) }
        super.onSaveInstanceState(outState)
    }

    private fun checkAuthAndLoadData() {
        lifecycleScope.launch {
            try {
                SupabaseModule.client.auth.awaitInitialization()
                if (SupabaseModule.client.auth.currentUserOrNull() == null) {
                    startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }
                val profile = SupabaseModule.client.from("perfil").select {
                    filter { eq("id", requireNotNull(SupabaseModule.client.auth.currentUserOrNull()).id) }
                }.decodeSingle<UsuarioPerfilDto>()
                if (profile.es_administrador) {
                    tvBusinessName.text = "DAJHO"
                    tvRole.text = "Administración"
                    bottomNav.visibility = android.view.View.GONE
                    replaceFragment(AdminFragment())
                    return@launch
                }
                val membership = BusinessSession.current() ?: error("No tienes negocios activos. Contacta al propietario.")
                actualizarRol(membership.rol)
                val negocio = SupabaseModule.client.from("negocio").select {
                    filter { eq("id", requireNotNull(membership.negocio_id)) }
                }.decodeSingle<NegocioDto>()
                tvBusinessName.text = "${negocio.nombre} ▾"
                contextoListo = true
                bottomNav.isEnabled = true
                if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) replaceFragment(HomeFragment())
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                contextoListo = false
                actualizarRol(null)
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("No se pudo cargar tu negocio").setMessage(e.userMessage())
                    .setPositiveButton("Reintentar") { _, _ -> checkAuthAndLoadData() }
                    .setNegativeButton("Cerrar sesión") { _, _ -> lifecycleScope.launch {
                        SupabaseModule.client.auth.signOut(); BusinessSession.clear()
                        startActivity(Intent(this@HomeActivity, LoginActivity::class.java)); finish()
                    }}.show()
            }
        }
    }

    private fun seleccionarNegocio() {
        if (!contextoListo) return
        lifecycleScope.launch {
            try {
                val memberships = BusinessSession.memberships()
                val names = SupabaseModule.client.from("negocio").select().decodeList<NegocioDto>().associateBy { it.id }
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("Selecciona tu negocio")
                    .setItems(memberships.map { names[it.negocio_id]?.nombre ?: "Negocio ${it.negocio_id}" }.toTypedArray()) { _, index ->
                        BusinessSession.select(requireNotNull(memberships[index].negocio_id))
                        contextoListo = false
                        // Remove old business screens before resolving the new context.
                        supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
                            supportFragmentManager.beginTransaction().remove(it).commitNow()
                        }
                        checkAuthAndLoadData()
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@HomeActivity, e.userMessage(), Toast.LENGTH_LONG).show() }
        }
    }

    private fun abrirModulos() {
        if (!contextoListo) return
        GestionNegocioBottomSheet(esPropietario()) { moduleKey ->
            replaceFragment(ManagementFragment.newInstance(moduleKey))
        }.show(supportFragmentManager, "GestionNegocioBottomSheet")
    }

    fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
