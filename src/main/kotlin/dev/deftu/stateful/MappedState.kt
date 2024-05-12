package dev.deftu.stateful

/**
 * A state that is mapped from another state using the given [mapper] function.
 */
class MappedState<T, U>(
    initial: State<T>,
    private val mapper: (T) -> U
) : SimpleState<U>(mapper(initial.get())) {
    private var removeListener = initial.subscribe { newValue ->
        set(mapper(newValue))
    }

    /**
     * Updates the state from which this state is mapped from.
     *
     * This method uses [State.set], thus meaning that all listeners will be invoked.
     */
    fun rebind(newState: State<T>) {
        removeListener()
        removeListener = newState.subscribe { newValue ->
            set(mapper(newValue))
        }
        set(mapper(newState.get()))
    }
}
