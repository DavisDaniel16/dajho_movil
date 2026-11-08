package com.example.dajho

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class CashRegisterSummaryTest {
    @Test fun openRegisterExplainsMissingClosingAmounts() {
        val row = buildJsonObject {
            put("monto_inicial", 50)
            put("monto_esperado", JsonNull)
            put("monto_declarado", JsonNull)
        }
        assertEquals("Inicial: $50.00  ·  Esperado: Pendiente de cierre\nDeclarado: Pendiente de cierre", cashRegisterSummary(row))
    }

    @Test fun zeroClosingAmountsRemainAmounts() {
        val row = buildJsonObject {
            put("monto_inicial", 0)
            put("monto_esperado", 0)
            put("monto_declarado", 0)
        }
        assertEquals("Inicial: $0.00  ·  Esperado: $0.00\nDeclarado: $0.00", cashRegisterSummary(row))
    }
}
