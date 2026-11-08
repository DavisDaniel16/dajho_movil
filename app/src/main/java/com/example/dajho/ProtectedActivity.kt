package com.example.dajho

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** One gate for every authenticated screen, including restored dialogs and bottom sheets. */
abstract class ProtectedActivity : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var gateJob: Job? = null
    private var openingLock = false
    private var contentAuthorized = false
    private var activeAccountId: String? = null
    private val lockPreferences by lazy { AppLockPreferences.from(this) }
    private val unlockLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        openingLock = false
        checkAccess()
    }
    private val deadline = Runnable { checkAccess() }
    private val dialogs = object : FragmentManager.FragmentLifecycleCallbacks() {
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            (f as? DialogFragment)?.dialog?.window?.let { dialogWindow ->
                installInputGate(dialogWindow)
                dialogWindow.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                dialogWindow.decorView.visibility = if (contentAuthorized) View.VISIBLE else View.INVISIBLE
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        window.decorView.visibility = View.INVISIBLE
        supportFragmentManager.registerFragmentLifecycleCallbacks(dialogs, true)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                SupabaseModule.client.auth.sessionStatus.collect { checkAccess() }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        installInputGate(window)
    }

    override fun onPostResume() {
        super.onPostResume()
        handler.post(deadline)
    }

    override fun onPause() {
        setContentAuthorized(false)
        handler.removeCallbacks(deadline)
        gateJob?.cancel()
        super.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacks(deadline)
        supportFragmentManager.unregisterFragmentLifecycleCallbacks(dialogs)
        super.onDestroy()
    }

    private fun checkAccess() {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || isFinishing) return
        if (AppSessionLock.state.checkTimeout()) setContentAuthorized(false)
        gateJob?.cancel()
        gateJob = lifecycleScope.launch {
            val id = authenticatedUserId()
            AppSessionLock.state.bindSession(id)
            activeAccountId = id
            if (id == null) {
                BusinessSession.clear()
                setContentAuthorized(false)
                startActivity(Intent(this@ProtectedActivity, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            } else if (!lockPreferences.isEnabled(id)) {
                // A disabled preference allows local access, but still requires an account.
                handler.removeCallbacks(deadline)
                setContentAuthorized(true)
            } else if (AppSessionLock.state.checkTimeout()) {
                setContentAuthorized(false)
                if (!openingLock) {
                    openingLock = true
                    requestUnlock()
                }
            } else {
                setContentAuthorized(true)
                handler.removeCallbacks(deadline)
                handler.postDelayed(deadline, AppSessionLock.state.remainingMillis())
            }
        }
    }

    protected open suspend fun authenticatedUserId(): String? {
        return SessionAccess.localUser()?.id
    }

    protected open fun requestUnlock() {
        unlockLauncher.launch(Intent(this, AppLockActivity::class.java))
    }

    private fun setContentAuthorized(authorized: Boolean) {
        contentAuthorized = authorized
        window.decorView.visibility = if (authorized) View.VISIBLE else View.INVISIBLE
        fun update(fragments: List<Fragment>) {
            fragments.forEach { fragment ->
                (fragment as? DialogFragment)?.dialog?.window?.decorView?.visibility =
                    if (authorized) View.VISIBLE else View.INVISIBLE
                update(fragment.childFragmentManager.fragments)
            }
        }
        update(supportFragmentManager.fragments)
    }

    private fun acceptInteraction(): Boolean {
        val id = activeAccountId
        val allowsInput = contentAuthorized && id != null &&
            (!lockPreferences.isEnabled(id) || AppSessionLock.state.recordInteraction())
        if (!allowsInput) {
            setContentAuthorized(false)
            handler.post { checkAccess() }
            return false
        }
        return true
    }

    private fun installInputGate(target: Window) {
        val callback = target.callback ?: return
        if (callback is InputGate) return
        target.callback = InputGate(callback)
    }

    private inner class InputGate(private val callback: Window.Callback) : Window.Callback by callback {
        override fun dispatchTouchEvent(event: MotionEvent): Boolean =
            if (acceptInteraction()) callback.dispatchTouchEvent(event) else true

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            if (acceptInteraction()) callback.dispatchKeyEvent(event) else true

        override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
            if (acceptInteraction()) callback.dispatchGenericMotionEvent(event) else true

        override fun dispatchKeyShortcutEvent(event: KeyEvent): Boolean =
            if (acceptInteraction()) callback.dispatchKeyShortcutEvent(event) else true

        override fun dispatchTrackballEvent(event: MotionEvent): Boolean =
            if (acceptInteraction()) callback.dispatchTrackballEvent(event) else true

        // Java default methods are not forwarded by Kotlin interface delegation.
        override fun onSearchRequested(event: android.view.SearchEvent?): Boolean =
            callback.onSearchRequested(event)

        override fun onWindowStartingActionMode(action: android.view.ActionMode.Callback?, type: Int): android.view.ActionMode? =
            callback.onWindowStartingActionMode(action, type)

        override fun onProvideKeyboardShortcuts(data: MutableList<android.view.KeyboardShortcutGroup>?, menu: android.view.Menu?, deviceId: Int) =
            callback.onProvideKeyboardShortcuts(data, menu, deviceId)

        override fun onPointerCaptureChanged(hasCapture: Boolean) =
            callback.onPointerCaptureChanged(hasCapture)
    }
}
