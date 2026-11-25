package io.github.lrosa2.speedbeep.core.road

/** The road file is unreadable: wrong magic or version, truncated, or inconsistent. */
class RoadFileFormatException(message: String) : RuntimeException(message)
