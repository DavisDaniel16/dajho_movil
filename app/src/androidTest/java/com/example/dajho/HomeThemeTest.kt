package com.example.dajho

import android.content.res.Configuration
import android.view.LayoutInflater
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeThemeTest {
    @Test
    fun homeInflatesInDarkMode() {
        assertHomeInflates(Configuration.UI_MODE_NIGHT_YES)
    }

    @Test
    fun homeInflatesInLightMode() {
        assertHomeInflates(Configuration.UI_MODE_NIGHT_NO)
    }

    private fun assertHomeInflates(nightMode: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val themedContext = ContextThemeWrapper(
                context.createConfigurationContext(configuration),
                R.style.Theme_DAJHO
            )
            val home = LayoutInflater.from(themedContext).inflate(R.layout.activity_home, null)
            val navigation = home.findViewById<BottomNavigationView>(R.id.bottomNav)
            assertEquals(5, navigation.menu.size())
        }
    }
}
