package dev.deftu.stateful.ext

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State
import dev.deftu.stateful.combine
import dev.deftu.stateful.map

/** Flips the value. The read is untracked, so calling this inside a computation adds no dependency. */
public fun MutableState<Boolean>.toggle() {
    update { !it }
}

/** The negation, as a cached derivation. Dependents wake when the answer flips, not on every write. */
public fun State<Boolean>.inverted(): State<Boolean> {
    return map { !it }
}

public infix fun State<Boolean>.and(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> first && second }
}

public infix fun State<Boolean>.and(other: Boolean): State<Boolean> {
    return map { it && other }
}

public infix fun Boolean.and(other: State<Boolean>): State<Boolean> {
    return other.map { this && it }
}

public infix fun State<Boolean>.or(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> first || second }
}

public infix fun State<Boolean>.or(other: Boolean): State<Boolean> {
    return map { it || other }
}

public infix fun Boolean.or(other: State<Boolean>): State<Boolean> {
    return other.map { this || it }
}

public infix fun State<Boolean>.xor(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> first xor second }
}

public infix fun State<Boolean>.xor(other: Boolean): State<Boolean> {
    return map { it xor other }
}

public infix fun State<Boolean>.nand(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> !(first && second) }
}

public infix fun State<Boolean>.nand(other: Boolean): State<Boolean> {
    return map { !(it && other) }
}

public infix fun State<Boolean>.nor(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> !(first || second) }
}

public infix fun State<Boolean>.nor(other: Boolean): State<Boolean> {
    return map { !(it || other) }
}

public operator fun State<Boolean>.not(): State<Boolean> {
    return inverted()
}
