package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Phase
import io.github.lrosa2.speedbeep.core.rules.RoadContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StatusTextTest {
    private val zurich = ZoneId.of("Europe/Zurich")

    private fun status(
        phase: Phase,
        kmh: Double? = 52.6,
        noRoadData: Boolean = false,
        simple: Boolean = false,
        pausedUntil: Long? = null,
    ) = WatchStatus(phase, kmh, RoadContext.NORMAL, noRoadData, pausedUntil, 53, simple, 10)

    private fun line(s: WatchStatus) = StatusText.line(s, 0, 0, zurich)

    @Test
    fun `town road`() = assertEquals("52 km/h · town", line(status(Phase.ARMED)))

    @Test
    fun `over shows the threshold`() = assertEquals("61 km/h · OVER 53", line(status(Phase.OVER, 61.0)))

    @Test
    fun `fast road`() = assertEquals("118 km/h · fast road, silent", line(status(Phase.FAST_ROAD, 118.9)))

    @Test
    fun `quiet stretch`() = assertEquals("61 km/h · quiet this stretch", line(status(Phase.QUIET, 61.0)))

    @Test
    fun `simple mode`() = assertEquals("52 km/h · simple mode", line(status(Phase.ARMED, simple = true)))

    @Test
    fun `no road data`() = assertEquals("52 km/h · simple mode · no road data", line(status(Phase.ARMED, noRoadData = true)))

    @Test
    fun `no gps`() = assertEquals("No GPS", line(status(Phase.NO_GPS, kmh = null)))

    @Test
    fun `before the first fix`() = assertEquals("Waiting for GPS", line(status(Phase.ARMED, kmh = null)))

    @Test
    fun `the pause end is shown as a clock time`() {
        val wallNow = ZonedDateTime.of(2026, 11, 25, 14, 22, 0, 0, zurich).toInstant().toEpochMilli()
        val monoNow = 70_000L
        val s = status(Phase.PAUSED, pausedUntil = monoNow + 10 * 60_000)
        assertEquals("Paused until 14:32", StatusText.line(s, monoNow, wallNow, zurich))
    }

    @Test
    fun `notification buttons follow the state`() {
        assertEquals(listOf(NoteButton.QUIET, NoteButton.PAUSE, NoteButton.STOP), StatusText.buttons(Phase.OVER))
        assertEquals(listOf(NoteButton.RESUME, NoteButton.STOP), StatusText.buttons(Phase.PAUSED))
        for (phase in listOf(Phase.ARMED, Phase.FAST_ROAD, Phase.QUIET, Phase.NO_GPS)) {
            assertEquals(listOf(NoteButton.PAUSE, NoteButton.STOP), StatusText.buttons(phase))
        }
    }
}
