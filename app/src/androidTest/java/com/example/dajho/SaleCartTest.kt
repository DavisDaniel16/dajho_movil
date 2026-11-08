package com.example.dajho

import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SaleCartTest {
    @Test fun quantitiesTotalStockAndRemovalStayInSync() {
        ActivityScenario.launch(SaleCartHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentByTag("sale")!!
                val root = fragment.requireView()
                val list = root.findViewById<RecyclerView>(R.id.rvCarrito)
                val product = list.adapter!!
                val row = product.onCreateViewHolder(list, 0)
                @Suppress("UNCHECKED_CAST")
                (product as RecyclerView.Adapter<RecyclerView.ViewHolder>).onBindViewHolder(row, 0)
                row.itemView.findViewById<View>(R.id.btnAgregar).performClick()
                root.findViewById<View>(R.id.btnVerCarrito).performClick()
                val cart = list.adapter as CarritoAdapter
                val cartRow = cart.onCreateViewHolder(list, 0)
                cart.onBindViewHolder(cartRow, 0)
                assertEquals("$19.99", root.findViewById<TextView>(R.id.tvTotal).text.toString())
                cartRow.btnMas.performClick()
                cart.onBindViewHolder(cartRow, 0)
                assertEquals("$39.98", root.findViewById<TextView>(R.id.tvTotal).text.toString())
                assertFalse("Cannot exceed available stock", cartRow.btnMas.isEnabled)
                cartRow.btnMenos.performClick()
                cart.onBindViewHolder(cartRow, 0)
                assertEquals("1", cartRow.tvCantidad.text.toString())
                cartRow.ivEliminar.performClick()
                assertEquals(0, cart.itemCount)
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.saleEmptyState).visibility)
                assertFalse(root.findViewById<View>(R.id.btnCobrar).isEnabled)
            }
        }
    }

    @Test fun cartTabAndItemsSurviveRecreation() {
        ActivityScenario.launch(SaleCartHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                val list = root.findViewById<RecyclerView>(R.id.rvCarrito)
                @Suppress("UNCHECKED_CAST")
                val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
                val row = adapter.onCreateViewHolder(list, 0)
                adapter.onBindViewHolder(row, 0)
                row.itemView.findViewById<View>(R.id.btnAgregar).performClick()
                root.findViewById<View>(R.id.btnVerCarrito).performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                assertTrue(root.findViewById<RecyclerView>(R.id.rvCarrito).adapter is CarritoAdapter)
                assertEquals("$19.99", root.findViewById<TextView>(R.id.tvTotal).text.toString())
                assertEquals(View.GONE, root.findViewById<View>(R.id.tilSearch).visibility)
                assertTrue(root.findViewById<View>(R.id.btnCobrar).isEnabled)
            }
        }
    }

    @Test fun emptyCartReturnsToCatalogAndClearRequiresConfirmation() {
        ActivityScenario.launch(SaleCartHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                root.findViewById<View>(R.id.btnVerCarrito).performClick()
                assertEquals(View.VISIBLE, root.findViewById<View>(R.id.saleEmptyState).visibility)
                root.findViewById<View>(R.id.btnEmptyAction).performClick()
                val list = root.findViewById<RecyclerView>(R.id.rvCarrito)
                assertTrue(list.adapter is ProductoAdapter)
                @Suppress("UNCHECKED_CAST")
                val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
                val row = adapter.onCreateViewHolder(list, 0)
                adapter.onBindViewHolder(row, 0)
                row.itemView.findViewById<View>(R.id.btnAgregar).performClick()
                root.findViewById<View>(R.id.btnVaciarCarrito).performClick()
                assertEquals("$19.99", root.findViewById<TextView>(R.id.tvTotal).text.toString())
            }
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val confirmation = activity.supportFragmentManager.findFragmentByTag(ClearCartDialog.TAG) as ClearCartDialog
                (confirmation.requireDialog() as androidx.appcompat.app.AlertDialog)
                    .getButton(android.content.DialogInterface.BUTTON_NEGATIVE).performClick()
            }
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                assertEquals("$19.99", root.findViewById<TextView>(R.id.tvTotal).text.toString())
                root.findViewById<View>(R.id.btnVaciarCarrito).performClick()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val confirmation = activity.supportFragmentManager.findFragmentByTag(ClearCartDialog.TAG) as ClearCartDialog
                (confirmation.requireDialog() as androidx.appcompat.app.AlertDialog)
                    .getButton(android.content.DialogInterface.BUTTON_POSITIVE).performClick()
            }
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                assertEquals("$0.00", root.findViewById<TextView>(R.id.tvTotal).text.toString())
                assertFalse(root.findViewById<View>(R.id.btnCobrar).isEnabled)
            }
        }
    }

    @Test fun lightCartRendersForReview() = captureCart(false)
    @Test fun darkCartRendersForReview() = captureCart(true)

    private fun captureCart(night: Boolean) {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val intent = android.content.Intent(instrumentation.targetContext, SaleCartHostActivity::class.java)
            .putExtra("night", night)
        ActivityScenario.launch<SaleCartHostActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                val list = root.findViewById<RecyclerView>(R.id.rvCarrito)
                @Suppress("UNCHECKED_CAST")
                val adapter = list.adapter as RecyclerView.Adapter<RecyclerView.ViewHolder>
                for (position in 0..1) {
                    val row = adapter.onCreateViewHolder(list, 0)
                    adapter.onBindViewHolder(row, position)
                    row.itemView.findViewById<View>(R.id.btnAgregar).performClick()
                }
                root.findViewById<View>(R.id.btnVerCarrito).performClick()
                assertEquals("$62.49", root.findViewById<TextView>(R.id.tvTotal).text.toString())
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                val root = activity.supportFragmentManager.findFragmentByTag("sale")!!.requireView()
                assertTrue(root.width > 0 && root.height > 0)
                val bitmap = android.graphics.Bitmap.createBitmap(root.width, root.height, android.graphics.Bitmap.Config.ARGB_8888)
                root.draw(android.graphics.Canvas(bitmap))
                val file = java.io.File(activity.getExternalFilesDir("cart-review"), if (night) "cart-dark.png" else "cart-light.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
            }
        }
    }
}
