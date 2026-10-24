package com.example

import com.example.game.PickupPool
import com.example.game.PickupType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PickupTypeTest {

    @Test
    fun stableIdsResolveToTypedPickupValues() {
        assertEquals(PickupType.HEALTH, PickupType.fromId("health"))
        assertEquals(PickupType.FLASK, PickupType.fromId("flask"))
        assertEquals(PickupType.SULFUR, PickupType.fromId("sulfur"))
        assertEquals(PickupType.MERCURY, PickupType.fromId("mercury"))
        assertEquals(PickupType.VITRIOL, PickupType.fromId("vitriol"))
    }

    @Test
    fun idParsingIgnoresCaseAndSurroundingWhitespace() {
        assertEquals(PickupType.MERCURY, PickupType.fromId("  MERCURY "))
    }

    @Test
    fun unknownIdsAreRejectedInsteadOfSilentlyBecomingAnotherItem() {
        assertNull(PickupType.fromId("unknown-relic"))
    }

    @Test
    fun pickupPoolStoresTypedLootAndReusesObjects() {
        val pool = PickupPool(capacity = 1)

        val first = pool.spawn(10f, 20f, PickupType.SULFUR)
        assertEquals(PickupType.SULFUR, first.type)
        assertEquals(1, pool.activeCount)

        val reused = pool.spawn(30f, 40f, PickupType.VITRIOL)
        assertSame(first, reused)
        assertEquals(PickupType.VITRIOL, pool.get(0).type)
        assertEquals(1, pool.activeCount)
    }
}
