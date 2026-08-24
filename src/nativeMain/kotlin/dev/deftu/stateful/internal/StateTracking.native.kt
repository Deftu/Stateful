package dev.deftu.stateful.internal

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var holder: StateTracking? = null

internal actual val tracking: StateTracking
    get() = holder ?: StateTracking().also { holder = it }
