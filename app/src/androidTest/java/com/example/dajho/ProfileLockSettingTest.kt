package com.example.dajho

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.switchmaterial.SwitchMaterial
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileLockSettingTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val settings get() = AppLockPreferences.from(instrumentation.targetContext)

    @Before fun setup() {
        instrumentation.runOnMainSync {
            settings.setEnabled("profile-theme-user", false)
            AppSessionLock.onLogin("profile-theme-user")
        }
    }

    @After fun cleanup() {
        instrumentation.runOnMainSync {
            settings.setEnabled("profile-theme-user", false)
            AppSessionLock.clear()
        }
    }

    @Test fun activatingRequiresConfirmationAndCancellationKeepsItDisabled() {
        ActivityScenario.launch(ProfileThemeHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val toggle = it.findViewById<SwitchMaterial>(R.id.switchBloqueoInactividad)
                assertTrue(toggle.isEnabled)
                assertFalse(toggle.isChecked)
                toggle.performClick()
                assertEquals(true, it.requestedLockPreference)
                assertFalse(toggle.isChecked)
                assertFalse(settings.isEnabled("profile-theme-user"))
            }
        }
    }

    @Test fun cancellingDeactivationPreservesPreferenceAcrossRecreation() {
        instrumentation.runOnMainSync { settings.setEnabled("profile-theme-user", true) }
        ActivityScenario.launch(ProfileThemeHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val toggle = it.findViewById<SwitchMaterial>(R.id.switchBloqueoInactividad)
                assertTrue(toggle.isChecked)
                toggle.performClick()
                assertEquals(false, it.requestedLockPreference)
                assertTrue(toggle.isChecked)
                assertTrue(settings.isEnabled("profile-theme-user"))
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertTrue(it.findViewById<SwitchMaterial>(R.id.switchBloqueoInactividad).isChecked)
            }
        }
    }
}
