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

/**
 * Both sides, as a cached derivation.
 *
 * **Does not short-circuit.** Both operands are read on every recomputation, so both are tracked
 * and a change to either wakes this one. Dependents wake only when the result flips.
 */
public infix fun State<Boolean>.and(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> first && second }
}

/** Both sides, against a constant. Dependents wake only when the result flips. */
public infix fun State<Boolean>.and(other: Boolean): State<Boolean> {
    return map { it && other }
}

/** Both sides, against a constant. Dependents wake only when the result flips. */
public infix fun Boolean.and(other: State<Boolean>): State<Boolean> {
    return other.map { this && it }
}

/**
 * Either side, as a cached derivation.
 *
 * **Does not short-circuit.** Both operands are read on every recomputation, so both are tracked.
 * Dependents wake only when the result flips.
 */
public infix fun State<Boolean>.or(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> first || second }
}

/** Either side, against a constant. Dependents wake only when the result flips. */
public infix fun State<Boolean>.or(other: Boolean): State<Boolean> {
    return map { it || other }
}

/** Either side, against a constant. Dependents wake only when the result flips. */
public infix fun Boolean.or(other: State<Boolean>): State<Boolean> {
    return other.map { this || it }
}

/** Exactly one side. Dependents wake only when the result flips. */
public infix fun State<Boolean>.xor(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> first xor second }
}

/** Exactly one of this and [other]. Dependents wake only when the result flips. */
public infix fun State<Boolean>.xor(other: Boolean): State<Boolean> {
    return map { it xor other }
}

/** Not both. Both operands are read and tracked; dependents wake only when the result flips. */
public infix fun State<Boolean>.nand(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> !(first && second) }
}

/** Not both this and [other]. Dependents wake only when the result flips. */
public infix fun State<Boolean>.nand(other: Boolean): State<Boolean> {
    return map { !(it && other) }
}

/** Neither side. Both operands are read and tracked; dependents wake only when the result flips. */
public infix fun State<Boolean>.nor(other: State<Boolean>): State<Boolean> {
    return combine(other) { first, second -> !(first || second) }
}

/** Neither this nor [other]. Dependents wake only when the result flips. */
public infix fun State<Boolean>.nor(other: Boolean): State<Boolean> {
    return map { !(it || other) }
}

/** Operator form of [inverted], so `!state` reads as it would on a plain `Boolean`. */
public operator fun State<Boolean>.not(): State<Boolean> {
    return inverted()
}
