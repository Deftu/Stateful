package dev.deftu.stateful.dsl

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import dev.deftu.stateful.impl.FlatMappedState
import dev.deftu.stateful.impl.MappedState
import dev.deftu.stateful.impl.SimpleMutableState
import dev.deftu.stateful.impl.SimpleState
import dev.deftu.stateful.impl.ZippedState

public fun <T> stateOf(value: T): State<T> {
    return SimpleState(value)
}

public fun <T> mutableStateOf(value: T): MutableState<T> {
    return SimpleMutableState(value)
}

public fun <T, U> mappedStateOf(state: State<T>, mapper: (T) -> U): MappedState<T, U> {
    return MappedState(state, mapper)
}

public fun <T, U> flatMappedStateOf(state: State<T>, mapper: (T) -> State<U>): FlatMappedState<T, U> {
    return FlatMappedState(state, mapper)
}

public fun <T, U> zippedStateOf(first: State<T>, second: State<U>): ZippedState<T, U> {
    return ZippedState(first, second)
}

public fun <A, B, C> zippedStateOf(first: State<A>, second: State<B>, third: State<C>): State<Triple<A, B, C>> {
    return first.zip(second).zip(third).map { (leading, last) ->
        Triple(leading.first, leading.second, last)
    }
}

public fun <A, B, R> combineStateOf(a: State<A>, b: State<B>, transform: (A, B) -> R): State<R> {
    return a.combine(b, transform)
}

public fun <A, B, C, R> combineStateOf(a: State<A>, b: State<B>, c: State<C>, transform: (A, B, C) -> R): State<R> {
    return zippedStateOf(a, b, c).map { (first, second, third) ->
        transform(first, second, third)
    }
}
