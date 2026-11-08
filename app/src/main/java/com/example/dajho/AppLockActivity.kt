package com.example.dajho

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.gotrue.auth
import kotlinx.coroutines.launch

class AppLockActivity : AppCompatActivity() {
    private lateinit var prompt: BiometricPrompt
    private lateinit var message: TextView
    private var authenticating = false
    private var accountId: String? = null
    private val keyguard by lazy { getSystemService(KeyguardManager::class.java) }
    private val credentialLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        authenticating = false
        if (it.resultCode == RESULT_OK) unlock() else showRetry()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContentView(R.layout.activity_app_lock)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lockRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        message = findViewById(R.id.lockMessage)
        accountId = savedInstanceState?.getString("lock_account")
        authenticating = savedInstanceState?.getBoolean("authenticating") ?: false
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { moveTaskToBack(true) }
        })
        prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticating = false
                    unlock()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    authenticating = false
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON && Build.VERSION.SDK_INT < 30) {
                        requestDeviceCredential()
                    } else showRetry()
                }

                override fun onAuthenticationFailed() {
                    message.setText(R.string.lock_failed)
                }
            })
        findViewById<Button>(R.id.btnUnlock).setOnClickListener { requestAuthentication() }
        findViewById<Button>(R.id.btnSetupLock).setOnClickListener {
            runCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
                .onFailure { message.setText(R.string.lock_settings_unavailable) }
        }
        findViewById<Button>(R.id.btnLockSignOut).setOnClickListener {
            prompt.cancelAuthentication()
            lifecycleScope.launch {
                // Local logout must work offline; no cached account may auto-open the app.
                SupabaseModule.client.auth.clearSession()
                AppSessionLock.clear()
                BusinessSession.clear()
                openLogin()
            }
        }
        lifecycleScope.launch {
            SupabaseModule.client.auth.awaitInitialization()
            val id = SupabaseModule.client.auth.currentUserOrNull()?.id
            if (id == null || (accountId != null && accountId != id)) {
                openLogin()
                return@launch
            }
            accountId = id
            AppSessionLock.state.bindSession(id)
            if (savedInstanceState == null) requestAuthentication()
        }
    }

    override fun onResume() {
        super.onResume()
        val secure = keyguard?.isDeviceSecure == true
        findViewById<View>(R.id.btnSetupLock).visibility = if (secure) View.GONE else View.VISIBLE
        if (!secure) message.setText(R.string.lock_no_credential)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("lock_account", accountId)
        outState.putBoolean("authenticating", authenticating)
        super.onSaveInstanceState(outState)
    }

    private fun requestAuthentication() {
        if (authenticating || accountId == null) return
        if (keyguard?.isDeviceSecure != true) {
            message.setText(R.string.lock_no_credential)
            return
        }
        if (Build.VERSION.SDK_INT >= 30) {
            authenticating = true
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_unlock))
                .setSubtitle(getString(R.string.lock_message))
                .setAllowedAuthenticators(BIOMETRIC_STRONG or DEVICE_CREDENTIAL)
                .build())
        } else if (BiometricManager.from(this).canAuthenticate(BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS) {
            authenticating = true
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.lock_unlock))
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .setNegativeButtonText(getString(R.string.lock_device_credential))
                .build())
        } else requestDeviceCredential()
    }

    @Suppress("DEPRECATION")
    private fun requestDeviceCredential() {
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            getString(R.string.lock_unlock), getString(R.string.lock_message)
        )
        if (intent == null) {
            message.setText(R.string.lock_no_credential)
            return
        }
        authenticating = true
        credentialLauncher.launch(intent)
    }

    private fun unlock() {
        val id = accountId ?: return
        if (SupabaseModule.client.auth.currentUserOrNull()?.id != id ||
            !AppSessionLock.state.authenticate(id)) {
            openLogin()
            return
        }
        setResult(RESULT_OK)
        finish()
    }

    private fun showRetry() { message.setText(R.string.lock_retry) }

    private fun openLogin() {
        AppSessionLock.clear()
        startActivity(Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }
}
