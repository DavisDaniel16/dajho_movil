package com.example.dajho

import android.content.res.Configuration
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLockTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Before fun resetGrant() {
        instrumentation.runOnMainSync {
            AppSessionLock.clear()
            AppLockPreferences.from(instrumentation.targetContext).setEnabled("lock-test-user", true)
        }
    }
    @After fun clearGrant() {
        instrumentation.runOnMainSync {
            AppSessionLock.clear()
            AppLockPreferences.from(instrumentation.targetContext).setEnabled("lock-test-user", false)
        }
    }

    @Test fun disabledLockAllowsRestoredAccountAndInputWithoutUnlock() {
        instrumentation.runOnMainSync {
            AppLockPreferences.from(instrumentation.targetContext).setEnabled("lock-test-user", false)
        }
        ActivityScenario.launch(LockTestHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.window.decorView.visibility)
                assertEquals(0, it.unlockRequests)
                // No fresh login grant is needed when this account opted out.
                assertTrue(AppSessionLock.state.isLocked)
                it.window.callback!!.dispatchKeyEvent(
                    android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_A)
                )
                assertEquals(View.VISIBLE, it.window.decorView.visibility)
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.window.decorView.visibility)
                assertEquals(0, it.unlockRequests)
            }
        }
    }

    @Test fun restoredAccountIsHiddenAndRequiresUnlock() {
        ActivityScenario.launch(LockTestHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.INVISIBLE, it.window.decorView.visibility)
                assertEquals(1, it.unlockRequests)
                assertTrue(it.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.INVISIBLE, it.window.decorView.visibility)
                assertEquals(1, it.unlockRequests)
                assertTrue(AppSessionLock.state.isLocked)
            }
        }
    }

    @Test fun freshLoginRemainsVisibleAcrossRecreationWithoutExtendingGrant() {
        instrumentation.runOnMainSync { AppSessionLock.onLogin("lock-test-user") }
        ActivityScenario.launch(LockTestHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.window.decorView.visibility)
                assertEquals(0, it.unlockRequests)
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.window.decorView.visibility)
                assertEquals(0, it.unlockRequests)
            }
        }
    }

    @Test fun dialogCannotExposeContentWhileLocked() {
        ActivityScenario.launch(LockTestHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                GestionNegocioBottomSheet(true).showNow(it.supportFragmentManager, "locked-dialog")
                val dialog = it.supportFragmentManager.findFragmentByTag("locked-dialog") as GestionNegocioBottomSheet
                assertEquals(View.INVISIBLE, dialog.requireDialog().window!!.decorView.visibility)
            }
        }
    }

    @Test fun lockedGrantConsumesInputAndHidesAnAlreadyOpenScreen() {
        instrumentation.runOnMainSync { AppSessionLock.onLogin("lock-test-user") }
        ActivityScenario.launch(LockTestHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(View.VISIBLE, it.window.decorView.visibility)
                // Revoking a grant exercises the same input barrier as an elapsed timeout.
                AppSessionLock.clear()
                val event = android.view.MotionEvent.obtain(0, 0, android.view.MotionEvent.ACTION_DOWN, 1f, 1f, 0)
                try {
                    assertTrue(it.window.callback!!.dispatchTouchEvent(event))
                } finally {
                    event.recycle()
                }
                assertEquals(View.INVISIBLE, it.window.decorView.visibility)
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                assertEquals(1, it.unlockRequests)
                assertTrue(AppSessionLock.state.isLocked)
            }
        }
    }

    @Test fun lockScreenInflatesInBothThemes() {
        instrumentation.runOnMainSync {
            for (mode in listOf(Configuration.UI_MODE_NIGHT_YES, Configuration.UI_MODE_NIGHT_NO)) {
                val context = instrumentation.targetContext
                val config = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(config), R.style.Theme_DAJHO)
                val root = LayoutInflater.from(themed).inflate(R.layout.activity_app_lock, null)
                assertEquals("Desbloquear", root.findViewById<TextView>(R.id.btnUnlock).text.toString())
                assertNotNull(root.findViewById<View>(R.id.btnLockSignOut))
            }
        }
    }
}
