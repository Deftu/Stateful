package dev.deftu.stateful.dsl

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

public open class StateDelegate<T>(
    public open val state: State<T>,
) : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state.get()
    }
}

public open class MutableStateDelegate<T>(
    override val state: MutableState<T>,
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
