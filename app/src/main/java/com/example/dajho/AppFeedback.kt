package com.example.dajho

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.snackbar.Snackbar
import java.lang.ref.WeakReference

/** In-app feedback; error banners use the active form window and saved results survive dialog dismissal. */
object AppFeedback {
    const val LENGTH_SHORT = 0
    const val LENGTH_LONG = 1

    enum class Kind(val background: Int, val foreground: Int, val icon: Int) {
        SUCCESS(R.color.feedback_success_bg, R.color.feedback_success_fg, R.drawable.ic_check),
        ERROR(R.color.feedback_error_bg, R.color.feedback_error_fg, R.drawable.ic_feedback_error),
        INFO(R.color.feedback_info_bg, R.color.feedback_info_fg, R.drawable.ic_feedback_info),
        PENDING(R.color.feedback_pending_bg, R.color.feedback_pending_fg, R.drawable.ic_refresh)
    }

    class Message internal constructor(
        private val context: Context?,
        private val anchor: View?,
        private val text: CharSequence,
        private val duration: Int,
        private val kind: Kind
    ) {
        fun show(): Snackbar? = display(context, anchor, text, duration, kind)
    }

    private data class Pending(val messageRes: Int, val kind: Kind)
    private val pending = mutableMapOf<Class<out Activity>, Pending>()
    private var active: WeakReference<Snackbar>? = null

    fun makeText(context: Context?, text: CharSequence, duration: Int, kind: Kind = Kind.INFO) =
        Message(context, null, text, duration, kind)

    fun makeText(context: Context?, messageRes: Int, duration: Int, kind: Kind = Kind.INFO) =
        Message(context, null, context?.getText(messageRes) ?: "", duration, kind)

    fun makeText(anchor: View, text: CharSequence, duration: Int, kind: Kind = Kind.INFO) =
        Message(anchor.context, anchor, text, duration, kind)

    /** Process-local notices cannot be supplied through an exported activity's Intent extras. */
    fun onNextScreen(target: Class<out Activity>, messageRes: Int, kind: Kind = Kind.SUCCESS) {
        pending[target] = Pending(messageRes, kind)
    }

    fun showPending(activity: Activity): Snackbar? {
        val root = activity.window.decorView
        if (!root.isAttachedToWindow || root.visibility != View.VISIBLE) return null
        val notice = pending.remove(activity.javaClass) ?: return null
        return makeText(activity, notice.messageRes, LENGTH_LONG, notice.kind).show()
    }

    fun dismiss() {
        active?.get()?.dismiss()
        active = null
    }

    private fun display(context: Context?, explicit: View?, text: CharSequence, duration: Int, kind: Kind): Snackbar? {
        val activity = context.activityOrNull() ?: return null
        if (activity.isFinishing || activity.isDestroyed || text.isBlank()) return null
        val activityRoot = activity.window.decorView
        val anchor = if (kind == Kind.SUCCESS || kind == Kind.PENDING) activityRoot else {
            explicit
                ?: (activity as? FragmentActivity)?.supportFragmentManager?.dialogRoot()
                ?: activityRoot
        }
        // A just-shown dialog may not be attached until the current UI callback finishes.
        // Keep its own parent hierarchy instead of falling back behind it to the activity.
        dismiss()
        val banner = Snackbar.make(anchor, text, if (duration == LENGTH_LONG) 7_000 else 4_000)
        val foreground = ContextCompat.getColor(anchor.context, kind.foreground)
        banner.view.setBackgroundResource(R.drawable.bg_feedback)
        banner.setBackgroundTint(ContextCompat.getColor(anchor.context, kind.background))
        banner.setTextColor(foreground)
        banner.setActionTextColor(foreground)
        banner.setAction(R.string.feedback_close) { banner.dismiss() }
        banner.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            maxLines = 6
            compoundDrawablePadding = (12 * resources.displayMetrics.density).toInt()
            val icon = ContextCompat.getDrawable(anchor.context, kind.icon)?.mutate()
            icon?.let { DrawableCompat.setTint(it, foreground) }
            setCompoundDrawablesRelativeWithIntrinsicBounds(icon, null, null, null)
        }
        (banner.view.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
            val margin = (16 * anchor.resources.displayMetrics.density).toInt()
            leftMargin = margin
            rightMargin = margin
        }
        activityRoot.findViewById<View>(R.id.bottomNav)
            ?.takeIf { anchor === activityRoot && it.visibility == View.VISIBLE && it.isAttachedToWindow }
            ?.let { banner.anchorView = it }
        active = WeakReference(banner)
        banner.show()
        return banner
    }

    private fun Context?.activityOrNull(): Activity? {
        var current = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            val base = current.baseContext
            if (base === current) return null
            current = base
        }
        return null
    }

    private fun FragmentManager.dialogRoot(): View? {
        fragments.asReversed().forEach { fragment ->
            if (fragment.isAdded) {
                fragment.childFragmentManager.dialogRoot()?.let { return it }
                (fragment as? DialogFragment)?.dialog?.takeIf { it.isShowing }?.window?.decorView
                    ?.takeIf { it.visibility == View.VISIBLE }?.let { return it }
            }
        }
        return null
    }
}
