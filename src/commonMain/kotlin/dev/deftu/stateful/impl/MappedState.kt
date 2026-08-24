package dev.deftu.stateful.impl

import dev.deftu.stateful.Disposable
import dev.deftu.stateful.State
import dev.deftu.stateful.Subscription

public open class MappedState<T, U>(
    initial: State<T>,
    private val mapper: (T) -> U,
) : SimpleState<U>(mapper(initial.get())), Disposable {
    private var subscription: Subscription = subscribeTo(initial)

    override val isDisposed: Boolean
        get() = subscription.isDisposed

    public fun rebind(newState: State<T>) {
        subscription.dispose()
        subscription = subscribeTo(newState)

        value = mapper(newState.get())
    }

    override fun dispose() {
        subscription.dispose()
    }

    private fun subscribeTo(state: State<T>): Subscription {
        return state.subscribe { newValue ->
            value = mapper(newValue)
        }
    }
}
