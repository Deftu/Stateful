package dev.deftu.stateful

import dev.deftu.stateful.dsl.memo

/*
 * The null-handling reads below are functions, so they track, on the same rule that makes
 * `state()` a function and `state.value` a property. Reading untracked here would make
 * `memo { config.getOrDefault(fallback) }` compile and silently never update.
 */

/**
 * Derives a state by applying [mapper] to this one.
 *
 * Lazy: [mapper] does not run until something reads the result, and does not run again unless
 * this state has actually changed.
 */
public fun <T, U> State<T>.map(mapper: (T) -> U): State<U> = memo { mapper(this()) }

/**
 * Derives a state from the state that [mapper] returns, following it as this state changes.
 *
 * The inner state is transient — each time this state changes, the previous inner state stops
 * being a dependency and the new one starts.
 */
public fun <T, U> State<T>.flatMap(mapper: (T) -> State<U>): State<U> = memo { mapper(this())() }

/** Derives a state holding both values as a pair. */
public fun <T, U> State<T>.zip(other: State<U>): State<Pair<T, U>> = memo { this() to other() }

/**
 * Derives a state by combining this one with [other].
 *
 * [transform] runs once per change of either input, never with one input updated and the other
 * stale.
 */
public fun <T, U, R> State<T>.combine(other: State<U>, transform: (T, U) -> R): State<R> =
    memo { transform(this(), other()) }

/** Reads tracked, returning [default] when the value is null. */
public fun <T : Any> State<T?>.getOrDefault(default: T): T = this() ?: default

/** Reads tracked, returning the result of [default] when the value is null. */
public fun <T : Any> State<T?>.getOrElse(default: () -> T): T = this() ?: default()

/** Reads tracked, throwing [exception] when the value is null. */
public fun <T : Any> State<T?>.getOrThrow(exception: Throwable): T = this() ?: throw exception

/** Reads tracked, throwing the result of [exception] when the value is null. */
public fun <T : Any> State<T?>.getOrThrow(exception: () -> Throwable): T = this() ?: throw exception()

/** Reads tracked, throwing [IllegalStateException] with [message] when the value is null. */
public fun <T : Any> State<T?>.getOrThrow(message: String): T = this() ?: throw IllegalStateException(message)

/** Reads tracked, throwing [IllegalStateException] when the value is null. */
public fun <T : Any> State<T?>.getOrThrow(): T = this() ?: throw IllegalStateException("Value is null")
