package com.example.dajho

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class BusinessSessionTest {
    @After fun cleanup() { BusinessSession.clear() }

    @Test fun membershipFromPreviousAccountIsNeverReused() {
        val membership = NegocioUsuarioDto(negocio_id = 10, rol = "propietario", usuario_id = "owner-a")
        BusinessSession.restore("owner-a", membership)
        assertEquals(listOf(membership), BusinessSession.cachedFor("owner-a"))
        assertNull(BusinessSession.cachedFor("owner-b"))
        assertNull(BusinessSession.cachedFor(null))
        BusinessSession.clear()
        assertNull(BusinessSession.cachedFor("owner-a"))
        assertNull(BusinessSession.selectedId)
    }
}
