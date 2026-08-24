package dev.deftu.stateful

import dev.deftu.stateful.dsl.memo

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

/** Returns the value, or [default] when it is null. */
public fun <T : Any> State<T?>.getOrDefault(default: T): T = value ?: default

/** Returns the value, or the result of [default] when it is null. */
public fun <T : Any> State<T?>.getOrElse(default: () -> T): T = value ?: default()

/** Returns the value, or throws [exception] when it is null. */
public fun <T : Any> State<T?>.getOrThrow(exception: Throwable): T = value ?: throw exception

/** Returns the value, or throws the result of [exception] when it is null. */
public fun <T : Any> State<T?>.getOrThrow(exception: () -> Throwable): T = value ?: throw exception()

/** Returns the value, or throws [IllegalStateException] with [message] when it is null. */
public fun <T : Any> State<T?>.getOrThrow(message: String): T = value ?: throw IllegalStateException(message)

/** Returns the value, or throws [IllegalStateException] when it is null. */
public fun <T : Any> State<T?>.getOrThrow(): T = value ?: throw IllegalStateException("Value is null")
