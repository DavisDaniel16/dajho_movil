package com.example.dajho

import org.junit.Assert.*
import org.junit.Test

class MoneyTest {
    @Test fun totalsUseExactCents() {
        val items = listOf(ItemCarritoDto(VarianteDto(precio_venta = 0.10)), ItemCarritoDto(VarianteDto(precio_venta = 0.20)))
        assertEquals("0.30", Money.total(items).toPlainString())
        assertEquals("0.30", Money.json(Money.total(items)).toString())
    }
    @Test fun quantitiesAreIncludedBeforeRounding() {
        assertEquals("59.97", Money.total(listOf(ItemCarritoDto(VarianteDto(precio_venta = 19.99), 3))).toPlainString())
    }
    @Test fun acceptsCommaDecimalInput() { assertEquals("12.50", Money.amount("12,50").toPlainString()) }
    @Test fun rejectsFractionalCents() { assertThrows(IllegalArgumentException::class.java) { Money.amount("1.005") } }
    @Test fun rejectsNonFiniteInput() { assertThrows(NumberFormatException::class.java) { Money.amount("NaN") } }
}
