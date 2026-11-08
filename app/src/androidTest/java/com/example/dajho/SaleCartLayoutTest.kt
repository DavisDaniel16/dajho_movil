package com.example.dajho

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleCartLayoutTest {
    @Test fun narrowCartSupportsLargeTextInBothThemes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val base = instrumentation.targetContext
                val config = Configuration(base.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                    fontScale = 1.5f
                }
                val context = ContextThemeWrapper(base.createConfigurationContext(config), R.style.Theme_DAJHO)
                val density = context.resources.displayMetrics.density
                val width = (320 * density).toInt()
                val row = CarritoAdapter(onCantidadChanged = { _, _ -> }, onItemRemoved = {})
                    .let { adapter ->
                        val holder = adapter.onCreateViewHolder(RecyclerView(context).apply {
                            layoutManager = androidx.recyclerview.widget.LinearLayoutManager(context)
                        }, 0)
                        adapter.updateList(listOf(ItemCarritoDto(VarianteDto(
                            id = -1, precio_venta = 1234.56, stock_actual = 999,
                            talla = "Extragrande", color = "Azul marino",
                            producto = ProductoSimpleDto(nombre = "Camiseta de algodón con nombre muy largo para probar la lectura")
                        ), 12)))
                        adapter.onBindViewHolder(holder, 0)
                        holder.itemView
                    }
                row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                row.layout(0, 0, width, row.measuredHeight)
                val name = row.findViewById<TextView>(R.id.tvProductoNombre)
                assertTrue(name.lineCount > 1)
                assertEquals(ContextCompat.getColor(context, R.color.text_primary), name.currentTextColor)
                val plus = row.findViewById<View>(R.id.btnMas)
                assertTrue(plus.width >= 48 * density)
                assertTrue(plus.height >= 48 * density)
                val minus = row.findViewById<View>(R.id.btnMenos)
                assertTrue(minus.right <= row.findViewById<View>(R.id.tvCantidad).left)
                val screen = LayoutInflater.from(context).inflate(R.layout.fragment_venta, null)
                screen.findViewById<TextView>(R.id.tvTotal).text = "$14814.72"
                screen.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((640 * density).toInt(), View.MeasureSpec.EXACTLY))
                screen.layout(0, 0, width, screen.measuredHeight)
                val footer = screen.findViewById<View>(R.id.bottomCheckoutBar)
                assertEquals(screen.height, footer.bottom)
                assertTrue(screen.findViewById<View>(R.id.rvCarrito).height > 0)
            }
        }
    }
}
