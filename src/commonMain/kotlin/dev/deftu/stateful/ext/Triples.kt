package dev.deftu.stateful.ext

import dev.deftu.stateful.State
import dev.deftu.stateful.map

/** The first component alone; changes to the other two do not reach this one's dependents. */
public fun <A, B, C> State<Triple<A, B, C>>.first(): State<A> {
    return map { value -> value.first }
}

/** The second component alone; changes to the other two do not reach this one's dependents. */
public fun <A, B, C> State<Triple<A, B, C>>.second(): State<B> {
    return map { value -> value.second }
}

/** The third component alone; changes to the other two do not reach this one's dependents. */
public fun <A, B, C> State<Triple<A, B, C>>.third(): State<C> {
    return map { value -> value.third }
}

public operator fun <A, B, C> State<Triple<A, B, C>>.component1(): State<A> {
    return first()
}

public operator fun <A, B, C> State<Triple<A, B, C>>.component2(): State<B> {
    return second()
}

public operator fun <A, B, C> State<Triple<A, B, C>>.component3(): State<C> {
    return third()
}
