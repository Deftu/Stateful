package dev.deftu.stateful

/**
 * Decides whether a new value differs from the old one.
 *
 * Consulted on write for sources, and after recomputation for memos. Returning `true` stops
 * propagation dead: dependents are not marked, and no effect runs.
 */
public fun interface Equality<in T> {
    /** Returns `true` when [a] and [b] are to be treated as the same value. */
    public fun areEqual(a: T, b: T): Boolean

    public companion object {
        private val STRUCTURAL = Equality<Any?> { a, b -> a == b }
        private val REFERENTIAL = Equality<Any?> { a, b -> a === b }
        private val NEVER = Equality<Any?> { _, _ -> false }

        /** `==`. The default. */
        @Suppress("UNCHECKED_CAST")
        public fun <T> structural(): Equality<T> = STRUCTURAL as Equality<T>

        /**
         * `===`. Use for values whose `equals` is expensive, or for wrappers whose identity is
         * the thing that matters.
         */
        @Suppress("UNCHECKED_CAST")
        public fun <T> referential(): Equality<T> = REFERENTIAL as Equality<T>

        /**
         * Always propagates, even when the value is unchanged.
         *
         * The escape hatch for payloads that are mutated in place — where the reference is equal
         * but the contents are not — and for states used purely as a signal that something
         * happened.
         */
        @Suppress("UNCHECKED_CAST")
        public fun <T> never(): Equality<T> = NEVER as Equality<T>
    }
}
