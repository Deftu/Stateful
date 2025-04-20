package dev.deftu.stateful

public class ZippedState<T, U>(
    first: State<T>,
    second: State<U>
) : SimpleState<ZippedState.Zip<T, U>>(Zip(first.get(), second.get())) {

    private var firstCallback = first.subscribe { newValue ->
        value = Zip(newValue, second.get())
    }

    private var secondCallback = second.subscribe { newValue ->
        value = Zip(first.get(), newValue)
    }

    public fun rebindFirst(newState: State<T>) {
        firstCallback()
        firstCallback = newState.subscribe { newValue ->
            value = Zip(newValue, value.second)
        }

        value = Zip(newState.get(), value.second)
    }

    public fun rebindSecond(newState: State<U>) {
        secondCallback()
        secondCallback = newState.subscribe { newValue ->
            value = Zip(value.first, newValue)
        }

        value = Zip(value.first, newState.get())
    }

    public data class Zip<A, B>(public val first: A, public val second: B) {
        override fun toString(): String {
            return "Zip(first=$first, second=$second)"
        }
    }

}
