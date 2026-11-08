package com.example.dajho

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenRestorationTest {
    @Test fun managementMenuPreservesOwnerModulesAfterRecreation() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                GestionNegocioBottomSheet(true).showNow(activity.supportFragmentManager, "gestion_prueba")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val sheet = activity.supportFragmentManager.findFragmentByTag("gestion_prueba")!!
                val container = sheet.requireView().findViewById<android.widget.LinearLayout>(R.id.layoutModulosContainer)
                val titles = (0 until container.childCount).map {
                    container.getChildAt(it).findViewById<android.widget.TextView>(R.id.tvTituloModulo).text.toString()
                }
                assertTrue("Owner modules must survive a theme recreation", titles.contains("Proveedores"))
                var selectedModule: String? = null
                activity.supportFragmentManager.setFragmentResultListener(
                    GestionNegocioBottomSheet.RESULTADO_MODULO, activity
                ) { _, result -> selectedModule = result.getString(GestionNegocioBottomSheet.CLAVE_MODULO) }
                val configuration = (0 until container.childCount)
                    .map { container.getChildAt(it) }
                    .first {
                        it.findViewById<android.widget.TextView>(R.id.tvTituloModulo).text.toString() ==
                            "Configuración y perfil"
                    }
                configuration.findViewById<android.view.View>(R.id.cardModulo).performClick()
                assertEquals("configuracion", selectedModule)
            }
        }
    }

    @Test fun detailSheetRestoresArgumentsAfterActivityRecreation() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                DetalleVentaBottomSheet("Venta de prueba", listOf(DetalleVentaBalanceDto(cantidad = 2, precio_unitario = 19.99)))
                    .showNow(activity.supportFragmentManager, "detalle_prueba")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val restored = activity.supportFragmentManager.findFragmentByTag("detalle_prueba")
                assertTrue(restored is DetalleVentaBottomSheet)
                assertEquals("Venta de prueba", restored!!.requireView().findViewById<android.widget.TextView>(R.id.tvTituloDetalleVenta).text.toString())
            }
        }
    }

    @Test fun profileEditorRestoresValuesWithoutBusinessRenamePermission() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                EditarPerfilBottomSheet("Usuario prueba", "Negocio prueba", null).showNow(activity.supportFragmentManager, "perfil_prueba")
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val sheet = activity.supportFragmentManager.findFragmentByTag("perfil_prueba")!!
                assertEquals("Usuario prueba", sheet.requireView().findViewById<android.widget.EditText>(R.id.etNombreUsuario).text.toString())
                assertFalse(sheet.requireView().findViewById<android.widget.EditText>(R.id.etNombreNegocio).isEnabled)
            }
        }
    }
}
