@file:Suppress("MemberVisibilityCanBePrivate")

package dev.deftu.stateful

import dev.deftu.stateful.impl.FlatMappedState
import dev.deftu.stateful.impl.MappedState
import dev.deftu.stateful.impl.ZippedState

public abstract class State<T> {
    private val subscriptions: MutableList<ListenerSubscription> = mutableListOf()

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

    public fun subscribe(listener: StateListener<T>): Subscription {
        val subscription = ListenerSubscription(listener)
        subscriptions.add(subscription)
        return subscription
    }

    public fun subscribeOnce(listener: StateListener<T>): Subscription {
        lateinit var subscription: Subscription
        subscription = subscribe { value ->
            subscription.dispose()
            listener.onChanged(value)
        }

        return subscription
    }

    public open fun notifyCurrent() {
        val value = get()
        var failure: Throwable? = null

        // Iterate a snapshot so listeners may subscribe or dispose during dispatch, and isolate
        // failures so one bad listener cannot starve the rest.
        for (subscription in subscriptions.toList()) {
            if (subscription.isDisposed) continue

            try {
                subscription.listener.onChanged(value)
            } catch (throwable: Throwable) {
                if (failure == null) {
                    failure = throwable
                } else {
                    failure.addSuppressed(throwable)
                }
            }
        }

        if (failure != null) throw failure
    }

    public fun <U> map(mapper: (T) -> U): MappedState<T, U> {
        return MappedState(this, mapper)
    }

    public fun <U> flatMap(mapper: (T) -> State<U>): FlatMappedState<T, U> {
        return FlatMappedState(this, mapper)
    }

    public fun <U> zip(other: State<U>): ZippedState<T, U> {
        return ZippedState(this, other)
    }

    public fun <U, R> combine(other: State<U>, transform: (T, U) -> R): State<R> {
        return zip(other).map { (first, second) -> transform(first, second) }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is State<*>) return false

        return get() == other.get()
    }

    override fun hashCode(): Int {
        return get()?.hashCode() ?: 0
    }

    override fun toString(): String {
        return get()?.toString() ?: "null"
    }

    private inner class ListenerSubscription(
        val listener: StateListener<T>,
    ) : Subscription {
        override var isDisposed: Boolean = false
            private set

        override fun dispose() {
            if (isDisposed) return

            isDisposed = true
            subscriptions.remove(this)
        }
    }
}
