package io.github.lrosa2.speedbeep.engine

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectAttemptTest {
    @Test
    fun `cancelling closes the socket of an attempt in progress`() {
        val attempt = ConnectAttempt()
        var closed = 0
        attempt.onClose { closed++ }
        attempt.cancel()
        assertEquals(1, closed)
        assertTrue(attempt.cancelled)
    }

    @Test
    fun `a socket registered after cancelling is closed at once`() {
        val attempt = ConnectAttempt()
        attempt.cancel()
        var closed = 0
        attempt.onClose { closed++ }
        assertEquals(1, closed)
    }
}
