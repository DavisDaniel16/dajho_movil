package com.example.dajho

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ScreenStateTest {
    @Test fun draftCartSurvivesSerialization() {
        val cart = listOf(ItemCarritoDto(VarianteDto(id = 42, sku = "CAM-01", precio_venta = 19.99, stock_actual = 4, producto = ProductoSimpleDto(7, "Camisa")), 2))
        val restored = ScreenArgs.json.decodeFromString<List<ItemCarritoDto>>(ScreenArgs.json.encodeToString(cart))
        assertEquals(cart, restored)
        assertEquals("39.98", Money.total(restored).toPlainString())
    }
    @Test fun errorsDoNotExposeHttpHeaders() {
        val error = io.github.jan.supabase.exceptions.RestException("Sin acceso", null, 403, "URL: https://example.invalid\nHeaders: Authorization=Bearer secret")
        assertTrue(error.userMessage().contains("permisos"))
        assertFalse(error.userMessage().contains("secret"))
        assertFalse(error.userMessage().contains("Headers:"))
        assertFalse(error.userMessage().contains("https://"))
    }
}
