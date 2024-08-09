package dev.deftu.stateful.utils

import dev.deftu.stateful.*
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

public fun <T> stateOf(value: T): State<T> {
    return SimpleState(value)
}

public fun <T> mutableStateOf(value: T): MutableState<T> {
    return SimpleMutableState(value)
}

public fun <T, U> mappedStateOf(state: State<T>, mapper: (T) -> U): MappedState<T, U> {
    return MappedState(state, mapper)
}

public fun <T, U> mappedMutableStateOf(state: State<T>, mapper: (T) -> U): MappedMutableState<T, U> {
    return MappedMutableState(state, mapper)
}

public fun <T, U> zippedStateOf(first: State<T>, second: State<U>): ZippedState<T, U> {
    return ZippedState(first, second)
}

public open class StateDelegate<T>(
    public open val state: State<T>
) : ReadOnlyProperty<Any?, T> {

    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state.get()
    }

}

public open class MutableStateDelegate<T>(
    override val state: MutableState<T>
) : StateDelegate<T>(state), ReadWriteProperty<Any?, T> {

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.set(value)
    }

}

public fun <T> stateBound(state: State<T>): StateDelegate<T> {
    return StateDelegate(state)
}

public fun <T> mutableStateBound(state: MutableState<T>): MutableStateDelegate<T> {
    return MutableStateDelegate(state)
}
