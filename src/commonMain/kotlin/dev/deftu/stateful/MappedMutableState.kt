package dev.deftu.stateful

public class MappedMutableState<T, U>(
    initial: State<T>,
    private val mapper: (T) -> U
) : SimpleMutableState<U>(mapper(initial.get())) {

    private var removeListener = initial.subscribe { newValue ->
        value = mapper(newValue)
    }

    public fun rebind(newState: State<T>) {
        removeListener()
        removeListener = newState.subscribe { newValue ->
            value = mapper(newValue)
        }

        value = mapper(newState.get())
    }

}
