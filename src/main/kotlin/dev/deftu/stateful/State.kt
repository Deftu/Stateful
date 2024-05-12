package dev.deftu.stateful

import java.util.function.Consumer
import java.util.function.Function

/**
 * The base class for all state types.
 *
 * State classes are wrappers around a value (or values) that can be accessed, modified, and subscribed to. These are especially useful for constantly changing values and updating UI elements.
 */
abstract class State<T> {
    protected val listeners = mutableListOf<(T) -> Unit>()

    abstract fun get(): T
    abstract fun set(value: T)
    fun getOrDefault(default: T) = get() ?: default
    fun getOrElse(default: () -> T) = get() ?: default()
    fun getOrThrow(exception: Throwable) = get() ?: throw exception
    fun getOrThrow(exception: () -> Throwable) = get() ?: throw exception()
    fun getOrThrow(message: String) = get() ?: throw IllegalStateException(message)
    fun getOrThrow() = get() ?: throw IllegalStateException("State is null")

    open fun notifyWithValue(value: T) {
        listeners.forEach { it(value) }
    }

    open fun notifyCurrent() {
        listeners.forEach { it(get()) }
    }

    /**
     * Subscribes a listener to this state. The listener will be called whenever the state is changed.
     */
    fun subscribe(listener: (T) -> Unit): () -> Unit {
        listeners.add(listener)
        return { listeners.remove(listener) }
    }

    /**
     * Subscribes a listener to this state. The listener will be called whenever the state is changed.
     */
    fun subscribe(listener: Consumer<T>): () -> Unit = subscribe(listener::accept)

    /**
     * Subscribes a listener to this state for only one call. The listener will be called whenever the state is changed, and then immediately unsubscribed.
     */
    fun subscribeOnce(listener: (T) -> Unit): () -> Unit {
        val subscription = subscribe(listener)
        return {
            subscription()
            listener(get())
        }
    }

    /**
     * Subscribes a listener to this state for only one call. The listener will be called whenever the state is changed, and then immediately unsubscribed.
     */
    fun subscribeOnce(listener: Consumer<T>): () -> Unit = subscribeOnce(listener::accept)

    /**
     * Maps this state to a new state using the given [mapper] function.
     */
    fun <U> map(mapper: (T) -> U) = MappedState(this, mapper)

    /**
     * Maps this state to a new state using the given [mapper] function.
     */
    fun <U> map(mapper: Function<T, U>) = map(mapper::apply)
}
