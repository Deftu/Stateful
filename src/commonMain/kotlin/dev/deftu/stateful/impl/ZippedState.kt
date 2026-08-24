package dev.deftu.stateful.impl

import dev.deftu.stateful.Disposable
import dev.deftu.stateful.State
import dev.deftu.stateful.Subscription

public class ZippedState<T, U>(
    first: State<T>,
    second: State<U>,
) : SimpleState<Pair<T, U>>(first.get() to second.get()), Disposable {
    private var firstState: State<T> = first
    private var secondState: State<U> = second

    private var firstSubscription: Subscription = subscribeToFirst(first)
    private var secondSubscription: Subscription = subscribeToSecond(second)

    override val isDisposed: Boolean
        get() = firstSubscription.isDisposed && secondSubscription.isDisposed

    public fun rebindFirst(newState: State<T>) {
        firstSubscription.dispose()
        firstState = newState
        firstSubscription = subscribeToFirst(newState)

        value = newState.get() to secondState.get()
    }

    public fun rebindSecond(newState: State<U>) {
        secondSubscription.dispose()
        secondState = newState
        secondSubscription = subscribeToSecond(newState)

        value = firstState.get() to newState.get()
    }

    override fun dispose() {
        firstSubscription.dispose()
        secondSubscription.dispose()
    }

    private fun subscribeToFirst(state: State<T>): Subscription {
        return state.subscribe { newValue ->
            value = newValue to secondState.get()
        }
    }

    private fun subscribeToSecond(state: State<U>): Subscription {
        return state.subscribe { newValue ->
            value = firstState.get() to newValue
        }
    }
}
