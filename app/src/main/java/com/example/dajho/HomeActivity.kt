package com.example.dajho

import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
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
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

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
    private var contextoListo = false
    private var rolActual: String = ""
    private var conectado = false
    private var syncJob: kotlinx.coroutines.Job? = null
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            runOnUiThread { comprobarConexion() }
        }
        override fun onLost(network: Network) {
            runOnUiThread { comprobarConexion() }
        }
    }

    private fun comprobarConexion() {
        runCatching {
            val manager = connectivity ?: return
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
            val disponible = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            if (disponible && !conectado) sincronizar()
            conectado = disponible
        }
    }

    fun sincronizar() {
        if (syncJob?.isActive == true) return
        syncJob = lifecycleScope.launch {
            val negocio = BusinessSession.selectedId
            if (contextoListo && negocio != null) {
                try {
                    CatalogRepository.load(negocio, context = applicationContext, forceRefresh = true)
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Log.w("HomeActivity", "No se pudo sincronizar el catálogo", e)
                }
            }
            if (!isFinishing && !isDestroyed && !supportFragmentManager.isStateSaved) {
                supportFragmentManager.setFragmentResult(RESULTADO_CONEXION, Bundle.EMPTY)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        runCatching { connectivity?.registerDefaultNetworkCallback(networkCallback) }
        comprobarConexion()
    }

    override fun onStop() {
        runCatching { connectivity?.unregisterNetworkCallback(networkCallback) }
        conectado = false
        super.onStop()
    }

    companion object {
        const val RESULTADO_CONEXION = "conexion_recuperada"
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
        bottomNav = findViewById(R.id.bottomNav)

        supportFragmentManager.setFragmentResultListener(
            GestionNegocioBottomSheet.RESULTADO_MODULO, this
        ) { _, result ->
            result.getString(GestionNegocioBottomSheet.CLAVE_MODULO)?.let { moduleKey ->
                if (moduleKey == "configuracion") {
                    val intent = Intent(this, PerfilActivity::class.java).apply {
                        putExtra(EXTRA_ES_PROPIETARIO, esPropietario())
                    }
                    perfilLauncher.launch(intent)
                } else {
                    replaceFragment(ManagementFragment.newInstance(moduleKey))
                }
            }
        }

        // Cargar datos del negocio en la cabecera compartida
        savedInstanceState?.getLong("negocio_seleccionado")?.takeIf { it > 0 }?.let(BusinessSession::select)
        bottomNav.isEnabled = false
        checkAuthAndLoadData()
        tvBusinessName.setOnClickListener { seleccionarNegocio() }
        findViewById<android.view.View>(R.id.ivUser).setOnClickListener { abrirModulos() }

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
                runCatching { SupabaseModule.client.auth.awaitInitialization() }
                val currentUser = SupabaseModule.client.auth.currentUserOrNull()

                if (currentUser == null && !applicationContext.hasInternetConnection()) {
                    val cachedNegocioId = BusinessSession.selectedId
                    if (cachedNegocioId != null) {
                        contextoListo = true
                        bottomNav.isEnabled = true
                        if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                            replaceFragment(HomeFragment())
                        }
                        return@launch
                    }
                }

                if (currentUser == null) {
                    startActivity(Intent(this@HomeActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }

                if (applicationContext.hasInternetConnection()) {
                    runCatching {
                        val user = SupabaseModule.client.auth.currentUserOrNull()
                        if (user != null) {
                            val profile = SupabaseModule.client.from("perfil").select {
                                filter { eq("id", user.id) }
                            }.decodeSingleOrNull<UsuarioPerfilDto>()
                            if (profile?.es_administrador == true) {
                                tvBusinessName.text = "DAJHO"
                                tvRole.text = "Administración"
                                bottomNav.visibility = android.view.View.GONE
                                replaceFragment(AdminFragment())
                                return@launch
                            }
                        }
                    }
                }

                val membership = BusinessSession.current(applicationContext)
                if (membership != null) {
                    actualizarRol(membership.rol)
                    if (applicationContext.hasInternetConnection()) {
                        runCatching {
                            val negocio = SupabaseModule.client.from("negocio").select {
                                filter { eq("id", requireNotNull(membership.negocio_id)) }
                            }.decodeSingle<NegocioDto>()
                            tvBusinessName.text = "${negocio.nombre} ▾"
                        }.onFailure {
                            tvBusinessName.text = "Mi Negocio ▾"
                        }
                    } else {
                        tvBusinessName.text = "Mi Negocio ▾"
                    }
                } else {
                    actualizarRol("Propietario")
                    tvBusinessName.text = "Mi Negocio ▾"
                }

                contextoListo = true
                bottomNav.isEnabled = true
                if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    replaceFragment(HomeFragment())
                }
                
                // Trigger background sync if online
                if (applicationContext.hasInternetConnection()) {
                    SyncManager.triggerSync(applicationContext)
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@launch
                contextoListo = true
                bottomNav.isEnabled = true
                if (!supportFragmentManager.isStateSaved && supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
                    replaceFragment(HomeFragment())
                }
            }
        }
    }

    private fun seleccionarNegocio() {
        if (!contextoListo || isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return
        lifecycleScope.launch {
            try {
                val memberships = BusinessSession.memberships()
                val names = SupabaseModule.client.from("negocio").select().decodeList<NegocioDto>().associateBy { it.id }
                if (isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return@launch
                com.google.android.material.dialog.MaterialAlertDialogBuilder(this@HomeActivity)
                    .setTitle("Selecciona tu negocio")
                    .setItems(memberships.map { names[it.negocio_id]?.nombre ?: "Negocio ${it.negocio_id}" }.toTypedArray()) { _, index ->
                        BusinessSession.select(requireNotNull(memberships[index].negocio_id))
                        contextoListo = false
                        supportFragmentManager.findFragmentById(R.id.fragmentContainer)?.let {
                            supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
                        }
                        checkAuthAndLoadData()
                    }.show()
            } catch (e: Exception) { Toast.makeText(this@HomeActivity, e.userMessage(), Toast.LENGTH_LONG).show() }
        }
    }

    private fun abrirModulos() {
        if (!contextoListo || isFinishing || isDestroyed || supportFragmentManager.isStateSaved) return
        GestionNegocioBottomSheet(esPropietario())
            .show(supportFragmentManager, "GestionNegocioBottomSheet")
    }

    fun replaceFragment(fragment: Fragment) {
        if (isFinishing || isDestroyed) return
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commitAllowingStateLoss()
    }
}
