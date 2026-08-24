package dev.deftu.stateful.internal

// Single-threaded platform: there is nothing to make thread-local.
private val holder = StateTracking()

internal actual val tracking: StateTracking
    get() = holder
