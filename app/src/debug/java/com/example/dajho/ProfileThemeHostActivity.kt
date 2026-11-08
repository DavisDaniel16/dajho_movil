package com.example.dajho

/** Theme tests exercise the real profile screen without a network account. Debug only. */
class ProfileThemeHostActivity : PerfilActivity() {
    var requestedLockPreference: Boolean? = null
        private set

    override suspend fun authenticatedUserId(): String = "profile-theme-user"

    // Simulate a cancelled system prompt without changing the persisted preference.
    override fun requestLockPreferenceChange(enabled: Boolean) {
        requestedLockPreference = enabled
    }
}
