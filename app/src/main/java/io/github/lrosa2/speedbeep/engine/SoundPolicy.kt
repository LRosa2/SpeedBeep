package io.github.lrosa2.speedbeep.engine

import io.github.lrosa2.speedbeep.core.rules.Sound

enum class SoundCommand { NONE, PLAY_MAIN, PLAY_REMINDER, STOP }

/**
 * Turns the rules' [Sound] into what the beeper does. A reminder never cuts into a main beep
 * that is still playing: main beeps can last 10 s while reminders can come every 3 s.
 */
class SoundPolicy {
    private var mainEndsMs = Long.MIN_VALUE

    fun decide(sound: Sound, nowMs: Long, mainLengthMs: Int): SoundCommand = when (sound) {
        Sound.NONE -> SoundCommand.NONE
        Sound.MAIN -> {
            mainEndsMs = nowMs + mainLengthMs
            SoundCommand.PLAY_MAIN
        }
        Sound.REMINDER -> if (nowMs < mainEndsMs) SoundCommand.NONE else SoundCommand.PLAY_REMINDER
        Sound.STOP -> {
            mainEndsMs = Long.MIN_VALUE
            SoundCommand.STOP
        }
    }
}
