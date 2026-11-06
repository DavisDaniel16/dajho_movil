package com.example.dajho

/** SDK exception messages can include request headers. Never show or log those. */
fun Throwable.userMessage(): String = when (this) {
    is io.github.jan.supabase.exceptions.RestException -> error.take(300)
    is java.io.IOException -> "No se pudo conectar. Comprueba tu conexión y reintenta."
    else -> (localizedMessage ?: "No se pudo completar la operación").substringBefore("URL:").take(300)
}
