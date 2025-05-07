package dev.deftu.stateful

public class ZippedState<T, U>(
    first: State<T>,
    second: State<U>
) : SimpleState<Pair<T, U>>(first.get() to second.get()) {

    private var firstCallback = first.subscribe { newValue ->
        value = newValue to second.get()
    }

    private var secondCallback = second.subscribe { newValue ->
        value = first.get() to newValue
    }

    public fun rebindFirst(newState: State<T>) {
        firstCallback()
        firstCallback = newState.subscribe { newValue ->
            value = newValue to value.second
        }

        value = newState.get() to value.second
    }

    public fun rebindSecond(newState: State<U>) {
        secondCallback()
        secondCallback = newState.subscribe { newValue ->
            value = value.first to newValue
        }

        value = value.first to newState.get()
    }

}
