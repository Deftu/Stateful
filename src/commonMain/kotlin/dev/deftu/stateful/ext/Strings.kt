package dev.deftu.stateful.ext

import dev.deftu.stateful.State
import dev.deftu.stateful.combine
import dev.deftu.stateful.map

/** The length. Dependents wake only when it actually changes, not on every edit of equal length. */
public fun State<String>.length(): State<Int> {
    return map(String::length)
}

/** Whether [other] appears. Dependents wake when the answer flips, not on every keystroke. */
public fun State<String>.contains(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return map { value -> value.contains(other, ignoreCase) }
}

/** Whether the current [other] appears. Recomputes when either side changes. */
public fun State<String>.contains(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return combine(other) { value, needle -> value.contains(needle, ignoreCase) }
}

/** Whether the value begins with [other]. Dependents wake only when the answer flips. */
public fun State<String>.startsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return map { value -> value.startsWith(other, ignoreCase) }
}

/** Whether the value begins with the current [other]. Recomputes when either side changes. */
public fun State<String>.startsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return combine(other) { value, prefix -> value.startsWith(prefix, ignoreCase) }
}

/** Whether the value ends with [other]. Dependents wake only when the answer flips. */
public fun State<String>.endsWith(other: String, ignoreCase: Boolean = false): State<Boolean> {
    return map { value -> value.endsWith(other, ignoreCase) }
}

/** Whether the value ends with the current [other]. Recomputes when either side changes. */
public fun State<String>.endsWith(other: State<String>, ignoreCase: Boolean = false): State<Boolean> {
    return combine(other) { value, suffix -> value.endsWith(suffix, ignoreCase) }
}

/** Emptiness. A form field bound to this redraws when it becomes empty, not on every character. */
public fun State<String>.isEmpty(): State<Boolean> {
    return map(String::isEmpty)
}

/** The inverse of [isEmpty], with the same cutoff. */
public fun State<String>.isNotEmpty(): State<Boolean> {
    return map(String::isNotEmpty)
}

/** Blankness, whitespace included. Dependents wake only when the answer flips. */
public fun State<String>.isBlank(): State<Boolean> {
    return map(String::isBlank)
}

/** The inverse of [isBlank], with the same cutoff. The usual shape for enabling a submit button. */
public fun State<String>.isNotBlank(): State<Boolean> {
    return map(String::isNotBlank)
}

/** The trimmed value. Adding trailing whitespace changes nothing downstream. */
public fun State<String>.trim(): State<String> {
    return map(String::trim)
}

/** The lowercased value. A change of case alone does not reach dependents. */
public fun State<String>.lowercase(): State<String> {
    return map(String::lowercase)
}

/** The uppercased value. A change of case alone does not reach dependents. */
public fun State<String>.uppercase(): State<String> {
    return map(String::uppercase)
}
