package dev.deftu.stateful

/**
 * A simple implementation of [State] that stores the value in a variable.
 */
open class SimpleState<T>(
    protected var value: T
) : State<T>() {
    override fun get() = value

    override fun set(value: T) {
        if (this.value == value) return

        this.value = value
        super.notifyCurrent()
    }
}
