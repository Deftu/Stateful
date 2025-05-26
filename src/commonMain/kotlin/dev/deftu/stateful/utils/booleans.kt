package dev.deftu.stateful.utils

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.State

public fun MutableState<Boolean>.toggle() {
    set { !it }
}

public infix fun State<Boolean>.and(other: State<Boolean>): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a && b }
}

public infix fun State<Boolean>.and(other: Boolean): State<Boolean> {
    return mappedStateOf(this) { it && other }
}

public infix fun Boolean.and(other: State<Boolean>): State<Boolean> {
    return mappedStateOf(other) { this && it }
}

public infix fun State<Boolean>.or(other: State<Boolean>): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a || b }
}

public infix fun State<Boolean>.or(other: Boolean): State<Boolean> {
    return mappedStateOf(this) { it || other }
}

public infix fun Boolean.or(other: State<Boolean>): State<Boolean> {
    return mappedStateOf(other) { this || it }
}

public infix fun State<Boolean>.xor(other: Boolean): State<Boolean> {
    return mappedStateOf(this) { it xor other }
}

public infix fun State<Boolean>.xor(other: State<Boolean>): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a xor b }
}

public infix fun State<Boolean>.nand(other: Boolean): State<Boolean> {
    return mappedStateOf(this) { !(it && other) }
}

public infix fun State<Boolean>.nand(other: State<Boolean>): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> !(a && b) }
}

public operator fun State<Boolean>.not(): State<Boolean> {
    return mappedStateOf(this) { !it }
}
