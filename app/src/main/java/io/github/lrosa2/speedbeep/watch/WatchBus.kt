package io.github.lrosa2.speedbeep.watch

import io.github.lrosa2.speedbeep.engine.WatchStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the running service shows; null while not watching. Written by WatchService on the main thread. */
object WatchBus {
    private val state = MutableStateFlow<WatchStatus?>(null)
    val status: StateFlow<WatchStatus?> = state.asStateFlow()
    val running: Boolean get() = state.value != null

    fun publish(status: WatchStatus?) {
        state.value = status
    }
}
