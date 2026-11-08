package com.example.dajho

/** Deterministic UI fixtures, excluded from release; no authentication or server writes. */
class SaleCartHostActivity : androidx.fragment.app.FragmentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (android.os.Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        val container = android.widget.FrameLayout(this).apply { id = android.R.id.custom }
        setContentView(container)
        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(android.R.id.custom, SaleCartFixtureFragment().apply { arguments = intent.extras }, "sale").commitNow()
        }
    }
}

class SaleCartFixtureFragment : VentaFragment() {
    override fun onGetLayoutInflater(savedInstanceState: android.os.Bundle?): android.view.LayoutInflater {
        val inflater = super.onGetLayoutInflater(savedInstanceState)
        val config = android.content.res.Configuration(inflater.context.resources.configuration)
        if (arguments?.containsKey("night") == true) {
            config.uiMode = (config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (requireArguments().getBoolean("night")) android.content.res.Configuration.UI_MODE_NIGHT_YES
                else android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
        val context = androidx.appcompat.view.ContextThemeWrapper(
            inflater.context.createConfigurationContext(config), R.style.Theme_DAJHO
        )
        return inflater.cloneInContext(context)
    }

    override suspend fun getNegocioId() = -32001L
    override suspend fun loadCatalog(negocioId: Long, query: String): List<VarianteDto> =
        listOf(
            VarianteDto(id = -1, sku = "DEMO-01", talla = "M", color = "Azul",
                precio_venta = 19.99, stock_actual = 2,
                producto = ProductoSimpleDto(nombre = "Camiseta de algodón")),
            VarianteDto(id = -2, sku = "DEMO-02", talla = "38", color = "Blanco",
                precio_venta = 42.50, stock_actual = 10,
                producto = ProductoSimpleDto(nombre = "Zapatos deportivos")),
            VarianteDto(id = -3, precio_venta = 12.0, stock_actual = 0,
                producto = ProductoSimpleDto(nombre = "Bolso sin existencias"))
        ).filter { query.isBlank() || it.producto?.nombre.orEmpty().contains(query, ignoreCase = true) }
}
