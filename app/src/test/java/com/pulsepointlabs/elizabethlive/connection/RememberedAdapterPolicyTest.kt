package com.pulsepointlabs.elizabethlive.connection

import com.pulsepointlabs.elizabethlive.PairedObdDevice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class RememberedAdapterPolicyTest {
    private val remembered = PairedObdDevice("vLinker MC+", "AA:BB:CC:DD:EE:FF")

    @Test
    fun pairedRememberedAdapterConnectsWithoutPicker() {
        val result = RememberedAdapterPolicy.resolve(
            remembered,
            listOf("11:22:33:44:55:66", "aa:bb:cc:dd:ee:ff"),
        )

        assertSame(remembered, (result as RememberedAdapterDecision.Connect).device)
    }

    @Test
    fun missingRememberedAdapterOpensPicker() {
        assertEquals(
            RememberedAdapterDecision.ShowPicker,
            RememberedAdapterPolicy.resolve(null, listOf(remembered.address)),
        )
    }

    @Test
    fun unpairedRememberedAdapterOpensPicker() {
        assertEquals(
            RememberedAdapterDecision.ShowPicker,
            RememberedAdapterPolicy.resolve(remembered, listOf("11:22:33:44:55:66")),
        )
    }
}
