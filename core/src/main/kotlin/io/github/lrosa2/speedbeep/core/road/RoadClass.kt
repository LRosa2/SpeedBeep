package io.github.lrosa2.speedbeep.core.road

/** Class of a road as stored in the road file (spec §6.2). */
enum class RoadClass(val code: Int) {
    /** Posted 50 or less, or no speed information: the threshold applies. */
    TOWN(0),

    /** Motorway, Autostrasse, or posted above 50: silent once confirmed. */
    FAST(1);

    companion object {
        fun fromCode(code: Int): RoadClass =
            entries.firstOrNull { it.code == code } ?: throw RoadFileFormatException("unknown road class code $code")
    }
}
