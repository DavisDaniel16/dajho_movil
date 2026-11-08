package com.example.dajho

import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChangePasswordDialogTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun profileContainsPasswordOptionForTheSignedInUser() {
        instrumentation.runOnMainSync { AppSessionLock.onLogin("profile-theme-user") }
        try {
            ActivityScenario.launch(ProfileThemeHostActivity::class.java).use { scenario ->
                instrumentation.waitForIdleSync()
                scenario.onActivity {
                    it.findViewById<View>(R.id.menuCambiarContrasena).performClick()
                    it.supportFragmentManager.executePendingTransactions()
                    val form = it.supportFragmentManager.findFragmentByTag(ChangePasswordDialog.TAG) as ChangePasswordDialog
                    assertNotNull(form.requireDialog().findViewById<View>(R.id.passwordCurrent))
                    assertNotNull(form.requireDialog().findViewById<View>(R.id.passwordNew))
                    assertNotNull(form.requireDialog().findViewById<View>(R.id.passwordConfirm))
                }
            }
        } finally { instrumentation.runOnMainSync { AppSessionLock.clear() } }
    }

    @Test fun invalidFieldsStayOpenAndNeverStartARequest() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity {
                ChangePasswordDialog().showNow(it.supportFragmentManager, ChangePasswordDialog.TAG)
                val alert = dialog(it)
                alert.findViewById<TextInputEditText>(R.id.passwordNew)!!.setText("new-password")
                alert.findViewById<TextInputEditText>(R.id.passwordConfirm)!!.setText("new-password")
                alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val alert = dialog(it)
                assertEquals(it.getString(R.string.password_current_required),
                    alert.findViewById<TextInputLayout>(R.id.passwordCurrentLayout)!!.error.toString())
                assertTrue(alert.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled)
                alert.findViewById<TextInputEditText>(R.id.passwordCurrent)!!.setText("old-password")
                alert.findViewById<TextInputEditText>(R.id.passwordConfirm)!!.setText("does-not-match")
                alert.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                val alert = dialog(it)
                assertEquals(it.getString(R.string.password_mismatch),
                    alert.findViewById<TextInputLayout>(R.id.passwordConfirmLayout)!!.error.toString())
                assertTrue(alert.isShowing)
            }
        }
    }

    @Test fun passwordsAreMaskedSecureAndNotRestoredAfterRotation() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity {
                ChangePasswordDialog().showNow(it.supportFragmentManager, ChangePasswordDialog.TAG)
                val alert = dialog(it)
                assertTrue(alert.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE != 0)
                for (id in listOf(R.id.passwordCurrent, R.id.passwordNew, R.id.passwordConfirm)) {
                    val input = alert.findViewById<TextInputEditText>(id)!!
                    assertFalse(input.isSaveEnabled)
                    assertNotNull(input.transformationMethod)
                    input.setText("test-secret")
                }
            }
            scenario.recreate()
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                for (id in listOf(R.id.passwordCurrent, R.id.passwordNew, R.id.passwordConfirm)) {
                    assertEquals("", dialog(it).findViewById<TextInputEditText>(id)!!.text.toString())
                }
            }
        }
    }

    @Test fun cancellingClosesTheForm() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity {
                ChangePasswordDialog().showNow(it.supportFragmentManager, ChangePasswordDialog.TAG)
                dialog(it).getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                it.supportFragmentManager.executePendingTransactions()
                assertNull(it.supportFragmentManager.findFragmentByTag(ChangePasswordDialog.TAG))
            }
        }
    }

    private fun dialog(activity: RestorationHostActivity): AlertDialog =
        (activity.supportFragmentManager.findFragmentByTag(ChangePasswordDialog.TAG) as ChangePasswordDialog)
            .requireDialog() as AlertDialog
}
