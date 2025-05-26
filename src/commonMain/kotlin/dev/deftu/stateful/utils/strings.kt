package dev.deftu.stateful.utils

import dev.deftu.stateful.State

public val State<String>.length: State<Int>
    get() = mappedStateOf(this, String::length)

public fun State<String>.contains(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(this) { value -> value.contains(other, ignoreCase) }
}

public fun State<String>.contains(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a.contains(b, ignoreCase) }
}

public fun State<String>.startsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(this) { value -> value.startsWith(other, ignoreCase) }
}

public fun State<String>.startsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a.startsWith(b, ignoreCase) }
}

public fun State<String>.endsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(this) { value -> value.endsWith(other, ignoreCase) }
}

public fun State<String>.endsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a.endsWith(b, ignoreCase) }
}

public fun State<String>.isEmpty(): State<Boolean> {
    return mappedStateOf(this, String::isEmpty)
}

public fun State<String>.isNotEmpty(): State<Boolean> {
    return mappedStateOf(this, String::isNotEmpty)
}

public fun State<String>.isBlank(): State<Boolean> {
    return mappedStateOf(this, String::isBlank)
}

public fun State<String>.isNotBlank(): State<Boolean> {
    return mappedStateOf(this, String::isNotBlank)
}

public fun State<String>.trim(): State<String> {
    return mappedStateOf(this, String::trim)
}

public fun State<String>.lowercase(): State<String> {
    return mappedStateOf(this, String::lowercase)
}

public fun State<String>.uppercase(): State<String> {
    return mappedStateOf(this, String::uppercase)
}
