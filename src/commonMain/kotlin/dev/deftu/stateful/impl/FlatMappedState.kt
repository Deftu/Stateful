package dev.deftu.stateful.impl

import dev.deftu.stateful.Disposable
import dev.deftu.stateful.State
import dev.deftu.stateful.Subscription

public class FlatMappedState<T, U> private constructor(
    source: State<T>,
    mapper: (T) -> State<U>,
    initial: State<U>,
) : SimpleState<U>(initial.get()), Disposable {
    public constructor(
        source: State<T>,
        mapper: (T) -> State<U>,
    ) : this(source, mapper, mapper(source.get()))

    private var innerSubscription: Subscription = subscribeToInner(initial)

    private val sourceSubscription: Subscription = source.subscribe { newValue ->
        val newInner = mapper(newValue)

        innerSubscription.dispose()
        innerSubscription = subscribeToInner(newInner)

        value = newInner.get()
    }

    override val isDisposed: Boolean
        get() = sourceSubscription.isDisposed

    override fun dispose() {
        innerSubscription.dispose()
        sourceSubscription.dispose()
    }

    private fun subscribeToInner(state: State<U>): Subscription {
        return state.subscribe { newValue ->
            value = newValue
        }
    }
}
