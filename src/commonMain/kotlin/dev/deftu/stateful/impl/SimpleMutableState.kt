package dev.deftu.stateful.impl

import dev.deftu.stateful.MutableState

public open class SimpleMutableState<T>(
    initial: T,
) : MutableState<T>() {
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

    override fun set(value: T) {
        this.value = value
    }
}
