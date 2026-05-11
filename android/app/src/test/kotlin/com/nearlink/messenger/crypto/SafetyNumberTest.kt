package com.nearlink.messenger.crypto

import com.google.common.truth.Truth.assertThat
import com.nearlink.messenger.core.crypto.SafetyNumber
import org.junit.Test

class SafetyNumberTest {

    @Test
    fun `safety number is symmetric across A and B`() {
        val pkA = ByteArray(32) { 0x11 }
        val pkB = ByteArray(32) { 0x22 }
        val ab = SafetyNumber.compute(pkA, pkB)
        val ba = SafetyNumber.compute(pkB, pkA)
        assertThat(ab).isEqualTo(ba)
    }

    @Test
    fun `safety number is 12 groups of 5 digits`() {
        val pkA = ByteArray(32) { 0x33 }
        val pkB = ByteArray(32) { 0x44 }
        val s = SafetyNumber.compute(pkA, pkB)
        val groups = s.split(' ')
        assertThat(groups).hasSize(12)
        for (g in groups) {
            assertThat(g).hasLength(5)
            assertThat(g.all { it.isDigit() }).isTrue()
        }
    }

    @Test
    fun `safety number changes when pubkey changes`() {
        val pkA = ByteArray(32) { 0x55 }
        val pkB = ByteArray(32) { 0x66 }
        val pkBp = pkB.copyOf().also { it[0] = 0x67 }
        val s1 = SafetyNumber.compute(pkA, pkB)
        val s2 = SafetyNumber.compute(pkA, pkBp)
        assertThat(s1).isNotEqualTo(s2)
    }
}
