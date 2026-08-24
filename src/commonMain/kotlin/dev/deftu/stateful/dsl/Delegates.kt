package dev.deftu.stateful.dsl

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import kotlin.properties.ReadOnlyProperty
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Reads [state] as a delegated property, **without** registering a dependency.
 *
 * A property access looks like plain data access at the call site, so silently creating a
 * dependency there would be surprising. Use [TrackedStateDelegate] when the read should track, and
 * say so at the declaration where a reader can see it.
 */
public open class StateDelegate<T>(
    /** The state being read. Exposed so a subclass can widen it. */
    public open val state: State<T>,
) : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state.value
    }
}

/**
 * Reads [state] as a delegated property, and writes through on assignment.
 *
 * The read is untracked, for the reason given on [StateDelegate].
 */
public open class MutableStateDelegate<T>(
    override val state: MutableState<T>,
) : StateDelegate<T>(state), ReadWriteProperty<Any?, T> {
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        state.set(value)
    }
}

/**
 * Reads [state] as a delegated property **and registers a dependency**, the same as `state()`.
 *
 * The declaration is where this is visible — `val title by trackedStateBound(titleState)` — and
 * the reads that follow look like any other property access. That is the hazard as well as the
 * point: inside a [memo] or [effect], every read of the property subscribes, and a reader skimming
 * the call site cannot tell. Prefer it where the whole class is reactive and the noise of `state()`
 * at every use would obscure more than it reveals.
 */
public class TrackedStateDelegate<T>(
    /** The state being read, tracked on every property access. */
    public val state: State<T>,
) : ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T {
        return state()
    }
}

/** Delegates to [state] with an untracked read. */
public fun <T> stateBound(state: State<T>): StateDelegate<T> {
    return StateDelegate(state)
}

/** Delegates to [state] with an untracked read, and writes through on assignment. */
public fun <T> mutableStateBound(state: MutableState<T>): MutableStateDelegate<T> {
    return MutableStateDelegate(state)
}

/** Delegates to [state] with a tracked read, so reads of the property register a dependency. */
public fun <T> trackedStateBound(state: State<T>): TrackedStateDelegate<T> {
    return TrackedStateDelegate(state)
}
