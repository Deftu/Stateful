package dev.deftu.stateful.utils

import dev.deftu.stateful.State

public val <A, B> State<Pair<A, B>>.first: State<A>
    get() = mappedStateOf(this) { state -> state.first }

public val <A, B> State<Pair<A, B>>.second: State<B>
    get() = mappedStateOf(this) { state -> state.second }

public operator fun <A, B> State<Pair<A, B>>.component1(): State<A> {
    return mappedStateOf(this) { state -> state.first }
}

public operator fun <A, B> State<Pair<A, B>>.component2(): State<B> {
    return mappedStateOf(this) { state -> state.second }
}
