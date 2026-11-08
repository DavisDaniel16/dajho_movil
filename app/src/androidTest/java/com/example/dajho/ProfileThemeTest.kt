package com.example.dajho

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileThemeTest {
    @Test fun themeSwitchSurvivesRecreationInBothDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val prefs = instrumentation.targetContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val hadPreference = prefs.contains("dark_mode")
        val savedPreference = prefs.getBoolean("dark_mode", false)
        val previousMode = AppCompatDelegate.getDefaultNightMode()
        try {
            prefs.edit().putBoolean("dark_mode", false).commit()
            instrumentation.runOnMainSync {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            ActivityScenario.launch(PerfilActivity::class.java).use { scenario ->
                for (dark in listOf(true, false)) {
                    scenario.onActivity { activity ->
                        activity.findViewById<SwitchMaterial>(R.id.switchModoOscuro).isChecked = dark
                    }
                    instrumentation.waitForIdleSync()
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        assertEquals(dark, activity.findViewById<SwitchMaterial>(R.id.switchModoOscuro).isChecked)
                        assertEquals(dark, prefs.getBoolean("dark_mode", !dark))
                        assertEquals(
                            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO,
                            activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        )
                    }
                }
            }
        } finally {
            prefs.edit().apply {
                if (hadPreference) putBoolean("dark_mode", savedPreference) else remove("dark_mode")
            }.commit()
            instrumentation.runOnMainSync { AppCompatDelegate.setDefaultNightMode(previousMode) }
        }
    }
}
