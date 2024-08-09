package dev.deftu.stateful.utils

import dev.deftu.stateful.State

public fun State<String>.contains(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a.contains(b, ignoreCase) }
}

public fun State<String>.contains(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(this) { value -> value.contains(other, ignoreCase) }
}

public fun State<String>.startsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a.startsWith(b, ignoreCase) }
}

public fun State<String>.startsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(this) { value -> value.startsWith(other, ignoreCase) }
}

public fun State<String>.endsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(zippedStateOf(this, other)) { (a, b) -> a.endsWith(b, ignoreCase) }
}

public fun State<String>.endsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return mappedStateOf(this) { value -> value.endsWith(other, ignoreCase) }
}

public fun State<String>.isEmpty(): State<Boolean> {
    return mappedStateOf(this) { value -> value.isEmpty() }
}

public fun State<String>.isNotEmpty(): State<Boolean> {
    return mappedStateOf(this) { value -> value.isNotEmpty() }
}
