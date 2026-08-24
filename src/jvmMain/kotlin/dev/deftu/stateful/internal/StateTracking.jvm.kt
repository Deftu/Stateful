package dev.deftu.stateful.internal

private val holder = ThreadLocal.withInitial { StateTracking() }

internal actual val tracking: StateTracking
    get() = holder.get()
