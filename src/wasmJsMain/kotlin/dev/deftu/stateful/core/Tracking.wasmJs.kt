package dev.deftu.stateful.core

// Single-threaded platform: there is nothing to make thread-local.
private val holder = Tracking()

internal actual val tracking: Tracking
    get() = holder
