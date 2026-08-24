package dev.deftu.stateful

/**
 * Where the runtime reports probable misuse that is not worth throwing for.
 *
 * The warnings today are an effect created outside any root, and a cleanup registered with no
 * owner to attach it to. Both are almost always leaks, and both are survivable — throwing would
 * make the consequence of writing an effect a line too early worse than the mistake itself.
 *
 * Redirect at startup. Every host has its own logger, and `println` reaches a terminal nobody is
 * reading in most of them:
 *
 * ```kotlin
 * StateWarnings.handler = { message -> logger.warn(message) }
 * ```
 *
 * Tests use the same hook to assert that a warning fired at all, which is otherwise impossible
 * from outside the module.
 */
public object StateWarnings {
    /**
     * Called with each warning. Replace at startup, before any state is created.
     *
     * Not synchronised: this is process-wide configuration, not per-call state, and reassigning it
     * while the graph is running races with whatever is reading it.
     */
    public var handler: (String) -> Unit = { message -> println("[stateful] $message") }
}
