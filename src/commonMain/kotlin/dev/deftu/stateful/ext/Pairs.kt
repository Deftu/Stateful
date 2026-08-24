package dev.deftu.stateful.ext

import dev.deftu.stateful.State
import dev.deftu.stateful.map

/** The first component alone, so a change to the second does not wake this one's dependents. */
public fun <A, B> State<Pair<A, B>>.first(): State<A> {
    return map { value -> value.first }
}

/** The second component alone, so a change to the first does not wake this one's dependents. */
public fun <A, B> State<Pair<A, B>>.second(): State<B> {
    return map { value -> value.second }
}

public operator fun <A, B> State<Pair<A, B>>.component1(): State<A> {
    return first()
}

public operator fun <A, B> State<Pair<A, B>>.component2(): State<B> {
    return second()
}
