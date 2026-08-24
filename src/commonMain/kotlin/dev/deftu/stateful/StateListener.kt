package dev.deftu.stateful

public fun interface StateListener<T> {
    public fun onChanged(value: T)
}
