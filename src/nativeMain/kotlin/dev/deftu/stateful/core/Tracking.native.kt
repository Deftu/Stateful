package dev.deftu.stateful.core

import kotlin.native.concurrent.ThreadLocal

@ThreadLocal
private var holder: Tracking? = null

internal actual val tracking: Tracking
    get() = holder ?: Tracking().also { holder = it }
