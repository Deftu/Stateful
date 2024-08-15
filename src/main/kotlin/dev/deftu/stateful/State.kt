@file:Suppress("MemberVisibilityCanBePrivate")

package dev.deftu.stateful

import java.util.function.Consumer

public abstract class State<T> {

    protected val listeners: MutableList<(T) -> Unit> = mutableListOf()

    public abstract fun get(): T

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

    public fun subscribe(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return {
            listeners.remove(listener)
        }
    }

    public fun subscribe(listener: Consumer<T>): () -> Unit {
        return subscribe(listener::accept)
    }

    public fun subscribeOnce(listener: (T) -> Unit): () -> Unit {
        val subscription = subscribe(listener)
        return {
            subscription()
            listener.invoke(get())
        }
    }

    public fun subscribeOnce(listener: Consumer<T>): () -> Unit {
        return subscribeOnce(listener::accept)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

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
