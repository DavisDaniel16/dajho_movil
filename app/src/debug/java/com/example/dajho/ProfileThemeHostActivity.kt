package com.example.dajho

/** Theme tests exercise the real profile screen without a network account. Debug only. */
class ProfileThemeHostActivity : PerfilActivity() {
    override suspend fun authenticatedUserId(): String = "profile-theme-user"
}
