package dev.deftu.stateful.ext

import dev.deftu.stateful.State
import dev.deftu.stateful.combine
import dev.deftu.stateful.map

public fun State<String>.length(): State<Int> {
    return map(String::length)
}

public fun State<String>.contains(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return map { value -> value.contains(other, ignoreCase) }
}

public fun State<String>.contains(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return combine(other) { value, needle -> value.contains(needle, ignoreCase) }
}

public fun State<String>.startsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return map { value -> value.startsWith(other, ignoreCase) }
}

public fun State<String>.startsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return combine(other) { value, prefix -> value.startsWith(prefix, ignoreCase) }
}

public fun State<String>.endsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return map { value -> value.endsWith(other, ignoreCase) }
}

public fun State<String>.endsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return combine(other) { value, suffix -> value.endsWith(suffix, ignoreCase) }
}

public fun State<String>.isEmpty(): State<Boolean> {
    return map(String::isEmpty)
}

public fun State<String>.isNotEmpty(): State<Boolean> {
    return map(String::isNotEmpty)
}

public fun State<String>.isBlank(): State<Boolean> {
    return map(String::isBlank)
}

public fun State<String>.isNotBlank(): State<Boolean> {
    return map(String::isNotBlank)
}

public fun State<String>.trim(): State<String> {
    return map(String::trim)
}

public fun State<String>.lowercase(): State<String> {
    return map(String::lowercase)
}

public fun State<String>.uppercase(): State<String> {
    return map(String::uppercase)
}
