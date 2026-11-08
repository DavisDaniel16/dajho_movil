package com.example.dajho

import android.content.Context
import android.content.SharedPreferences

/** Optional protection, scoped to an account on this device and retained across logout. */
internal class AppLockPreferences(private val prefs: SharedPreferences) {
    fun isEnabled(userId: String): Boolean = prefs.getBoolean("enabled:$userId", false)

    fun setEnabled(userId: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled:$userId", enabled).apply()
    }

    companion object {
        fun from(context: Context) = AppLockPreferences(
            context.getSharedPreferences("app_lock_preferences", Context.MODE_PRIVATE)
        )
    }
}
