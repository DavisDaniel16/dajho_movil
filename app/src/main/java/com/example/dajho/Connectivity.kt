package com.example.dajho

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun Context.hasInternetConnection(): Boolean {
    return runCatching {
        val manager = getSystemService(ConnectivityManager::class.java) ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }.getOrDefault(false)
}

/** Legacy check - no longer throws in offline-first mode, allowing local offline operations. */
fun Context.requireInternetConnection() {
    // Operations proceed offline and are queued for background sync when connection returns
}
