package com.example.dajho

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class OfflineSyncTest {

    @Test
    fun testPendingSyncItemPayload() {
        val payload = buildJsonObject {
            put("p_negocio", 101L)
            put("p_descuento", 5.0)
        }
        val item = PendingSyncItem(
            id = 1L,
            negocioId = 101L,
            tipo = "registrar_venta",
            payloadJson = ScreenArgs.json.encodeToString(kotlinx.serialization.json.JsonObject.serializer(), payload),
            requestId = "test-uuid"
        )

        assertEquals("registrar_venta", item.tipo)
        assertEquals(101L, item.negocioId)
        assertTrue(item.payloadJson.contains("101"))
    }

    @Test
    fun testStockDeductionCalculation() {
        val stockActual = 10
        val cantidadVendida = 3
        val nuevoStock = (stockActual - cantidadVendida).coerceAtLeast(0)
        assertEquals(7, nuevoStock)
    }

    @Test
    fun testStockDeductionClampedAtZero() {
        val stockActual = 2
        val cantidadVendida = 5
        val nuevoStock = (stockActual - cantidadVendida).coerceAtLeast(0)
        assertEquals(0, nuevoStock)
    }

    @Test
    fun testCajaStatusJsonParsing() {
        val jsonOpen = "[{\"id\":12,\"estado\":\"abierta\",\"monto_inicial\":50.0}]"
        val jsonClosed = "[{\"id\":12,\"estado\":\"cerrada\",\"monto_inicial\":50.0}]"

        val isOpen = jsonOpen.contains("\"estado\":\"abierta\"")
        val isClosed = !jsonClosed.contains("\"estado\":\"abierta\"")

        assertTrue(isOpen)
        assertTrue(isClosed)
    }
}
