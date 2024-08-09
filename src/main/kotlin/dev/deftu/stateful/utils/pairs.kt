package dev.deftu.stateful.utils

import dev.deftu.stateful.State

public operator fun <A, B> State<Pair<A, B>>.component1(): State<A> {
    return mappedStateOf(this) { state -> state.first }
}

public operator fun <A, B> State<Pair<A, B>>.component2(): State<B> {
    return mappedStateOf(this) { state -> state.second }
}
