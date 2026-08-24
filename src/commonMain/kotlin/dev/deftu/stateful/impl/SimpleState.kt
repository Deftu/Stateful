package dev.deftu.stateful.impl

import dev.deftu.stateful.State

public open class SimpleState<T>(
    initial: T,
) : State<T>() {
    private var currentValue: T = initial

    protected var value: T
        get() = currentValue
        set(value) {
            if (currentValue == value) return

            currentValue = value
            notifyCurrent()
        }

    override fun get(): T {
        return currentValue
    }
}
