package dev.deftu.stateful

/**
 * Where the runtime reports probable misuse that is not worth throwing for.
 *
 * The only warning today is an effect created outside any root, which is almost always a leak but
 * is survivable — throwing would make the consequence of writing an effect a line too early worse
 * than the mistake itself.
 *
 * Redirect it at startup. Every host has its own logger, and `println` reaches a terminal nobody is
 * reading in most of them:
 *
 * ```kotlin
 * Diagnostics.onWarning = { message -> logger.warn(message) }
 * ```
 *
 * Tests use the same hook to assert that a warning fired at all, which is otherwise impossible from
 * outside the module.
 */
public object Diagnostics {
    /**
     * Called with each warning. Replace at startup, before any state is created.
     *
     * Not synchronised: this is process-wide configuration, not per-call state, and reassigning it
     * while the graph is running races with whatever is reading it.
     */
    public var onWarning: (String) -> Unit = { message -> println("[stateful] $message") }
}
