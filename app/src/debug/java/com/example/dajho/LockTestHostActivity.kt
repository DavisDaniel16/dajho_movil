package com.example.dajho

import android.os.Bundle
import android.widget.TextView

/** Deterministic account boundary for instrumentation only; absent from release APKs. */
class LockTestHostActivity : ProtectedActivity() {
    var unlockRequests = 0
        private set

    override suspend fun authenticatedUserId(): String = "lock-test-user"

    override fun requestUnlock() { unlockRequests++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Contenido protegido" })
    }
}
