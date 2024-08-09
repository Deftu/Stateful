package dev.deftu.stateful

public abstract class MutableState<T> : State<T>() {

    public abstract fun set(value: T)

    public open fun set(transform: (T) -> T) {
        set(transform(get()))
    }

}
