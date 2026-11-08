package com.example.dajho

import com.google.android.material.snackbar.Snackbar
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppFeedbackTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @After fun dismiss() { instrumentation.runOnMainSync { AppFeedback.dismiss() } }

    @Test fun errorBannerHasReadableTextIconAndDismissAction() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity {
                val banner = AppFeedback.makeText(it, "No hay conexión. Revisa internet.", AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()!!
                val text = banner.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                assertEquals("No hay conexión. Revisa internet.", text.text.toString())
                assertTrue(text.maxLines > 2)
                assertNotNull(text.compoundDrawablesRelative[0])
                assertEquals(it.getString(R.string.feedback_close),
                    banner.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_action).text.toString())
            }
        }
    }

    @Test fun errorAppearsInTheDialogWindowWhilePendingSaveUsesTheActivity() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            var error: Snackbar? = null
            var saved: Snackbar? = null
            scenario.onActivity {
                GestionNegocioBottomSheet(true).showNow(it.supportFragmentManager, "feedback-dialog")
                error = AppFeedback.makeText(it, "Revisa los datos", AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
            }
            awaitAttached(error!!)
            scenario.onActivity {
                val sheet = it.supportFragmentManager.findFragmentByTag("feedback-dialog") as GestionNegocioBottomSheet
                assertSame(sheet.requireDialog().window!!.decorView, error!!.view.rootView)
                AppFeedback.dismiss()
                saved = AppFeedback.makeText(it, "Guardado para sincronizar", AppFeedback.LENGTH_LONG, AppFeedback.Kind.PENDING).show()
                sheet.dismiss()
            }
            awaitAttached(saved!!)
            scenario.onActivity { assertSame(it.window.decorView, saved!!.view.rootView) }
        }
    }

    @Test fun navigationNoticeIsConsumedOnlyByItsTargetAndOnlyOnce() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            instrumentation.waitForIdleSync()
            scenario.onActivity {
                AppFeedback.onNextScreen(RestorationHostActivity::class.java, R.string.password_changed)
                assertNotNull(AppFeedback.showPending(it))
                assertNull(AppFeedback.showPending(it))
            }
        }
    }

    @Test fun bannerColorsFollowLightAndDarkMode() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val foregrounds = mutableSetOf<Int>()
                for (mode in listOf(android.content.res.Configuration.UI_MODE_NIGHT_NO,
                    android.content.res.Configuration.UI_MODE_NIGHT_YES)) {
                    val config = android.content.res.Configuration(activity.resources.configuration).apply {
                        uiMode = (uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
                    }
                    val themed = androidx.appcompat.view.ContextThemeWrapper(activity, R.style.Theme_DAJHO).apply {
                        applyOverrideConfiguration(config)
                    }
                    val anchor = android.widget.FrameLayout(themed)
                    activity.setContentView(anchor)
                    val banner = AppFeedback.makeText(anchor, "Revisa la conexión", AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()!!
                    val text = banner.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
                    val expected = androidx.core.content.ContextCompat.getColor(themed, R.color.feedback_error_fg)
                    assertEquals(expected, text.currentTextColor)
                    foregrounds += text.currentTextColor
                    AppFeedback.dismiss()
                }
                assertEquals(2, foregrounds.size)
            }
        }
    }

    private fun awaitAttached(banner: Snackbar) {
        val ready = java.util.concurrent.CountDownLatch(1)
        instrumentation.runOnMainSync {
            if (banner.view.isAttachedToWindow) ready.countDown()
            else banner.view.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: android.view.View) {
                    view.removeOnAttachStateChangeListener(this)
                    ready.countDown()
                }
                override fun onViewDetachedFromWindow(view: android.view.View) = Unit
            })
        }
        assertTrue("The banner must attach to its window", ready.await(3, java.util.concurrent.TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    @Test fun explicitFormAnchorKeepsErrorsAboveRawDialogs() {
        ActivityScenario.launch(RestorationHostActivity::class.java).use { scenario ->
            var dialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
            var banner: Snackbar? = null
            scenario.onActivity {
                val form = android.widget.LinearLayout(it)
                dialog = com.google.android.material.bottomsheet.BottomSheetDialog(it).apply {
                    setContentView(form)
                    show()
                }
                banner = AppFeedback.makeText(form, "Completa el nombre", AppFeedback.LENGTH_LONG, AppFeedback.Kind.ERROR).show()
            }
            awaitAttached(banner!!)
            scenario.onActivity {
                assertSame(dialog!!.window!!.decorView, banner!!.view.rootView)
                dialog!!.dismiss()
            }
        }
    }
}
