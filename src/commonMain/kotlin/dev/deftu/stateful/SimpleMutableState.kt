package dev.deftu.stateful

public open class SimpleMutableState<T>(
    protected var value: T
) : MutableState<T>() {

    override fun get(): T {
        return value
    }

    override fun set(value: T) {
        this.value = value
        notifyCurrent()
    }

}
