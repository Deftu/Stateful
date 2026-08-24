package dev.deftu.stateful.core

private val holder = ThreadLocal.withInitial { Tracking() }

internal actual val tracking: Tracking
    get() = holder.get()
