package dev.deftu.stateful

public open class SimpleState<T>(
    protected var value: T
) : State<T>() {

    override fun get(): T {
        return value
    }

}
