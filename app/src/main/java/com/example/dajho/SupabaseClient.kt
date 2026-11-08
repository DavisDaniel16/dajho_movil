package com.example.dajho

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.SettingsSessionManager
import io.github.jan.supabase.postgrest.Postgrest

object SupabaseModule {
    val client: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                sessionManager = SettingsSessionManager()
                autoLoadFromStorage = true
                autoSaveToStorage = true
                alwaysAutoRefresh = true
                scheme = "dajho"
                host = "confirm"
            }
            install(Postgrest)
        }
    }
}
