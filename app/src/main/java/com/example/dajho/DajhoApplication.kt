package com.example.dajho

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DajhoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        if (prefs.contains("dark_mode")) {
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                if (prefs.getBoolean("dark_mode", false))
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                else androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
            )
        }
        CoroutineScope(Dispatchers.IO).launch {
            DajhoDatabase.getInstance(applicationContext)
            SyncManager.triggerSync(applicationContext)
        }

        registerNetworkCallback()
    }

    private fun registerNetworkCallback() {
        runCatching {
            val connectivityManager = getSystemService(ConnectivityManager::class.java)
            val builder = NetworkRequest.Builder()
            connectivityManager?.registerNetworkCallback(
                builder.build(),
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        SyncManager.triggerSync(applicationContext)
                    }
                }
            )
        }
    }
}
