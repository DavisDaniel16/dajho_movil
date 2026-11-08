package com.example.dajho

import android.content.res.Configuration
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeInflationTest {
    @Test fun screensInflateInDarkMode() = assertScreensInflate(Configuration.UI_MODE_NIGHT_YES)
    @Test fun screensInflateInLightMode() = assertScreensInflate(Configuration.UI_MODE_NIGHT_NO)

    private fun assertScreensInflate(nightMode: Int) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val configuration = Configuration(context.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            }
            val themedContext = ContextThemeWrapper(
                context.createConfigurationContext(configuration), R.style.Theme_DAJHO
            )
            val layouts = R.layout::class.java.fields.filter {
                it.name.startsWith("activity_") || it.name.startsWith("fragment_") ||
                    it.name.startsWith("bottom_sheet_") || it.name.startsWith("item_")
            }
            check(layouts.isNotEmpty())
            layouts.forEach { layout ->
                try {
                    LayoutInflater.from(themedContext).inflate(
                        layout.getInt(null), FrameLayout(themedContext), false
                    )
                } catch (error: Exception) {
                    throw AssertionError("Unable to inflate ${layout.name} in nightMode=$nightMode", error)
                }
            }
        }
    }
}
