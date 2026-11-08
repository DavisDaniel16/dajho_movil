package com.example.dajho

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChangePasswordDialog : DialogFragment() {
    companion object { const val TAG = "change_password" }
    private val model by lazy { ViewModelProvider(this)[ChangePasswordViewModel::class.java] }
    private var form: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { render(it) }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val root = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_change_password, null)
        form = root
        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.password_change_title)
            .setView(root)
            .setPositiveButton(R.string.password_save, null)
            .setNegativeButton(R.string.lock_setting_cancel, null)
            .create()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val root = form ?: return@setOnClickListener
            model.submit(
                root.findViewById<TextInputEditText>(R.id.passwordCurrent).text.toString(),
                root.findViewById<TextInputEditText>(R.id.passwordNew).text.toString(),
                root.findViewById<TextInputEditText>(R.id.passwordConfirm).text.toString()
            )
        }
        render(model.state.value)
    }

    private fun render(state: PasswordChangeState) {
        val root = form ?: return
        val busy = state is PasswordChangeState.Submitting
        isCancelable = !busy
        val alert = dialog as? AlertDialog ?: return
        alert.getButton(AlertDialog.BUTTON_POSITIVE)?.apply {
            isEnabled = !busy
            setText(if (busy) R.string.password_saving else R.string.password_save)
        }
        alert.getButton(AlertDialog.BUTTON_NEGATIVE)?.isEnabled = !busy
        listOf(R.id.passwordCurrentLayout, R.id.passwordNewLayout, R.id.passwordConfirmLayout).forEach {
            root.findViewById<TextInputLayout>(it).apply { isEnabled = !busy; error = null }
        }
        val errorView = root.findViewById<TextView>(R.id.passwordChangeError)
        errorView.visibility = View.GONE
        if (state is PasswordChangeState.Failure) {
            val message = getString(state.reason.messageResource())
            val target = when (state.reason) {
                PasswordChangeError.CURRENT_REQUIRED, PasswordChangeError.INVALID_CURRENT -> R.id.passwordCurrentLayout
                PasswordChangeError.NEW_TOO_SHORT, PasswordChangeError.NEW_TOO_LONG,
                PasswordChangeError.SAME_PASSWORD, PasswordChangeError.WEAK_NEW -> R.id.passwordNewLayout
                PasswordChangeError.CONFIRM_MISMATCH -> R.id.passwordConfirmLayout
                else -> null
            }
            if (target != null) root.findViewById<TextInputLayout>(target).error = message
            errorView.text = message
            errorView.visibility = View.VISIBLE
        }
        if (state is PasswordChangeState.Success) finishChange(state.accountId)
    }

    private fun finishChange(accountId: String) {
        model.consumeSuccess()
        val activity = requireActivity()
        clearFields()
        lifecycleScope.launch {
            withContext(NonCancellable) {
                // Do not sign out a different account if it changed while the request was in flight.
                if (SessionAccess.localUser()?.id == accountId) {
                    AppFeedback.onNextScreen(LoginActivity::class.java, R.string.password_changed)
                    SupabaseModule.client.auth.clearSession()
                    AppSessionLock.clear()
                    BusinessSession.clear()
                    activity.startActivity(Intent(activity, LoginActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    })
                    activity.finish()
                }
            }
            dismissAllowingStateLoss()
        }
    }

    private fun clearFields() {
        listOf(R.id.passwordCurrent, R.id.passwordNew, R.id.passwordConfirm).forEach {
            form?.findViewById<TextInputEditText>(it)?.text?.clear()
        }
    }

    override fun onDestroyView() {
        clearFields()
        form = null
        super.onDestroyView()
    }
}

private fun PasswordChangeError.messageResource(): Int = when (this) {
    PasswordChangeError.CURRENT_REQUIRED -> R.string.password_current_required
    PasswordChangeError.NEW_TOO_SHORT -> R.string.password_too_short
    PasswordChangeError.NEW_TOO_LONG -> R.string.password_too_long
    PasswordChangeError.CONFIRM_MISMATCH -> R.string.password_mismatch
    PasswordChangeError.SAME_PASSWORD -> R.string.password_same
    PasswordChangeError.INVALID_CURRENT -> R.string.password_invalid_current
    PasswordChangeError.NETWORK -> R.string.password_network
    PasswordChangeError.RATE_LIMIT -> R.string.password_rate_limit
    PasswordChangeError.WEAK_NEW -> R.string.password_weak
    PasswordChangeError.REAUTH_REQUIRED -> R.string.password_reauth
    PasswordChangeError.ACCOUNT_CHANGED -> R.string.password_account_changed
    PasswordChangeError.SESSION_REQUIRED -> R.string.password_session_required
    PasswordChangeError.UNEXPECTED -> R.string.password_unexpected
}
