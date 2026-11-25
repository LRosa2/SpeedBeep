package io.github.lrosa2.speedbeep.roadbuilder

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RingAssemblerTest {

    private fun assemble(vararg members: LongArray) = RingAssembler.assemble(members.toList())

    @Test
    fun `a closed way is a ring by itself`() {
        val result = assemble(longArrayOf(1, 2, 3, 1))
        assertEquals(listOf(listOf(1L, 2, 3, 1)), result.rings.map { it.toList() })
        assertEquals(0, result.unclosed)
    }

    @Test
    fun `two halves join into one ring`() {
        val result = assemble(longArrayOf(1, 2, 3), longArrayOf(3, 4, 1))
        assertEquals(listOf(listOf(1L, 2, 3, 4, 1)), result.rings.map { it.toList() })
    }

    @Test
    fun `a member drawn the other way round is reversed`() {
        val result = assemble(longArrayOf(1, 2, 3), longArrayOf(1, 4, 3))
        assertEquals(listOf(listOf(1L, 2, 3, 4, 1)), result.rings.map { it.toList() })
    }

    @Test
    fun `an open chain is dropped and counted, other rings are kept`() {
        val result = assemble(longArrayOf(1, 2), longArrayOf(2, 3), longArrayOf(7, 8, 9, 7))
        assertEquals(listOf(listOf(7L, 8, 9, 7)), result.rings.map { it.toList() })
        assertEquals(1, result.unclosed)
    }
}
