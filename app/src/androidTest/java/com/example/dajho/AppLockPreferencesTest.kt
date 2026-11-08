package com.example.dajho

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockPreferencesTest {
    private val prefs = InstrumentationRegistry.getInstrumentation().targetContext
        .getSharedPreferences("lock_preferences_test", Context.MODE_PRIVATE)

    @Before fun reset() { prefs.edit().clear().commit() }
    @After fun clean() { prefs.edit().clear().commit() }

    @Test fun newAccountsHaveOptionalLockDisabled() {
        assertFalse(AppLockPreferences(prefs).isEnabled("first"))
    }

    @Test fun preferencePersistsAndCanBeDisabled() {
        AppLockPreferences(prefs).setEnabled("first", true)
        assertTrue(AppLockPreferences(prefs).isEnabled("first"))
        AppLockPreferences(prefs).setEnabled("first", false)
        assertFalse(AppLockPreferences(prefs).isEnabled("first"))
    }

    @Test fun changingOneAccountDoesNotChangeAnother() {
        val settings = AppLockPreferences(prefs)
        settings.setEnabled("first", true)
        assertFalse(settings.isEnabled("second"))
        settings.setEnabled("second", true)
        settings.setEnabled("first", false)
        assertTrue(settings.isEnabled("second"))
    }
}
