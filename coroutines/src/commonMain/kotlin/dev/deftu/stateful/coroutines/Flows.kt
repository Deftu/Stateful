package dev.deftu.stateful.coroutines

import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Observes this state as a [Flow], emitting the current value first and then every change.
 *
 * Every change is delivered. Conflation is deliberately **not** baked in: a caller who wants
 * latest-value-wins composes `.conflate()`, which is how kotlinx flows are meant to be assembled,
 * and a caller who needs every intermediate value would have no way to get it back otherwise.
 *
 * The internal channel is explicitly unbounded rather than relying on a `buffer` operator fusing
 * into it, which it does not do reliably — with a rendezvous channel the effect's first run has no
 * receiver waiting yet and the current value is silently dropped. An effect body is not a
 * suspending context and so cannot apply backpressure, which is what forces the choice: a
 * collector that never keeps up accumulates. Add `.conflate()` or `.buffer(n)` when the producer
 * can outrun the consumer indefinitely.
 *
 * Collecting creates an owner and an effect, and cancelling the collection disposes them. The
 * initial value comes from the effect's own first run rather than from a separate read, so there is
 * no window in which a change can slip between the two.
 */
public fun <T> State<T>.asFlow(): Flow<T> = flow {
    val channel = Channel<T>(Channel.UNLIMITED)
    val owner = createOwner()
    runWithOwner(owner) {
        effect { channel.trySend(this@asFlow()) }
    }

    try {
        for (value in channel) emit(value)
    } finally {
        owner.dispose()
        channel.close()
    }
}

/**
 * Observes this state as a [StateFlow], started eagerly in [scope].
 *
 * The flow's initial value is read untracked at the point of the call, so the returned flow always
 * has a value.
 */
public fun <T> State<T>.asStateFlow(scope: CoroutineScope): StateFlow<T> =
    asFlow().stateIn(scope, SharingStarted.Eagerly, value)

/**
 * Collects this flow into a state, starting from [initial] until the first emission arrives.
 *
 * Collection runs in [scope] and stops when it is cancelled. The state itself outlives the scope —
 * it simply stops updating — so a reader holding it does not start failing, it goes stale. Bind the
 * reader's own lifetime to the same scope when that distinction matters.
 */
public fun <T> Flow<T>.toState(scope: CoroutineScope, initial: T): State<T> {
    val state = mutableStateOf(initial)
    scope.launch {
        collect { value -> state.set(value) }
    }

    return state
}

/**
 * Collects this [StateFlow] into a state, seeded with the flow's current value.
 *
 * The seed means the returned state never has a placeholder value, unlike [toState] over a plain
 * [Flow].
 */
public fun <T> StateFlow<T>.asState(scope: CoroutineScope): State<T> = toState(scope, value)
