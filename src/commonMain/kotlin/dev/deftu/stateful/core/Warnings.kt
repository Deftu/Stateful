package dev.deftu.stateful.core

import dev.deftu.stateful.Diagnostics

/**
 * Reports a probable misuse that is not fatal enough to throw for.
 *
 * Throwing would make the consequence of a small mistake — an effect created a line too early —
 * worse than the mistake itself, so these are warnings.
 */
internal fun warn(message: String) {
    Diagnostics.onWarning(message)
}
