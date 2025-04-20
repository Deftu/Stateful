@file:Suppress("MemberVisibilityCanBePrivate")

package dev.deftu.stateful

public abstract class State<T> : TargetStateSubscriptionAdapter<T>() {

    public abstract override fun get(): T

    public fun getOrDefault(default: T): T {
        return get() ?: default
    }

    public fun getOrElse(default: () -> T): T {
        return get() ?: default.invoke()
    }

    public fun getOrThrow(exception: Throwable): T {
        return get() ?: throw exception
    }

    public fun getOrThrow(exception: () -> Throwable): T {
        return get() ?: throw exception.invoke()
    }

    public fun getOrThrow(message: String): T {
        return get() ?: throw IllegalStateException(message)
    }

    public fun getOrThrow(): T {
        return get() ?: throw IllegalStateException("Value is null")
    }

    public open fun notifyWithValue(value: T) {
        listeners.forEach { listener -> listener.invoke(value) }
    }

    public open fun notifyCurrent() {
        listeners.forEach { listener -> listener.invoke(get()) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        other as State<*>

        return get() == other.get()
    }

    override fun hashCode(): Int {
        return get()?.hashCode() ?: 0
    }

    override fun toString(): String {
        return get()?.toString() ?: "null"
    }

}
