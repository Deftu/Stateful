package dev.deftu.stateful.utils

import dev.deftu.stateful.State

public val <A, B, C> State<Triple<A, B, C>>.first: State<A>
    get() = mappedStateOf(this) { state -> state.first }

public val <A, B, C> State<Triple<A, B, C>>.second: State<B>
    get() = mappedStateOf(this) { state -> state.second }

public val <A, B, C> State<Triple<A, B, C>>.third: State<C>
    get() = mappedStateOf(this) { state -> state.third }

public operator fun <A, B, C> State<Triple<A, B, C>>.component1(): State<A> {
    return mappedStateOf(this) { state -> state.first }
}

public operator fun <A, B, C> State<Triple<A, B, C>>.component2(): State<B> {
    return mappedStateOf(this) { state -> state.second }
}

public operator fun <A, B, C> State<Triple<A, B, C>>.component3(): State<C> {
    return mappedStateOf(this) { state -> state.third }
}
