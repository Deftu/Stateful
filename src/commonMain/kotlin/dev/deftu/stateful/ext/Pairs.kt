package dev.deftu.stateful.ext

import dev.deftu.stateful.State
import dev.deftu.stateful.map

public fun <A, B> State<Pair<A, B>>.first(): State<A> {
    return map { value -> value.first }
}

public fun <A, B> State<Pair<A, B>>.second(): State<B> {
    return map { value -> value.second }
}

public operator fun <A, B> State<Pair<A, B>>.component1(): State<A> {
    return first()
}

public operator fun <A, B> State<Pair<A, B>>.component2(): State<B> {
    return second()
}
