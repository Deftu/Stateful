package dev.deftu.stateful

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.jvm.isAccessible

open class StateDelegate<T>(
    val state: State<T>
) : ReadWriteProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>) =
        state.get()

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.set(value)
    }
}

class MappedStateDelegate<T, U>(
    state: State<T>,
    mapper: (T) -> U
) : StateDelegate<U>(state.map(mapper))

fun <T> simpleState(initialValue: T) = StateDelegate(SimpleState(initialValue))
fun <T, U> mappedState(
    property: KProperty0<T>,
    mapper: (T) -> U
) = MappedStateDelegate(getDelegate(property).state, mapper)

private fun <T> getDelegate(property: KProperty0<T>): StateDelegate<T> {
    property.isAccessible = true
    val delegate = property.getDelegate() ?: throw IllegalArgumentException("map cannot be used on a non-delegated property")
    @Suppress("UNCHECKED_CAST")
    return (delegate as? StateDelegate<T>) ?: throw IllegalArgumentException("map can only be used on StateDelegate<T> properties")
}
