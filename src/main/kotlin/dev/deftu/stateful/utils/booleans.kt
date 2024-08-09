package dev.deftu.stateful.utils

import dev.deftu.stateful.State

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

public operator fun State<Boolean>.not(): State<Boolean> {
    return mappedStateOf(this) { !it }
}
