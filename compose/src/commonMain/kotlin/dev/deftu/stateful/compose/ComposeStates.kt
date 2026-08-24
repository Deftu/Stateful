package dev.deftu.stateful.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState as ComposeMutableState
import androidx.compose.runtime.State as ComposeState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf as composeMutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.runWithOwner

/**
 * Creates a Stateful [Owner] scoped to the composition, disposed when this composable leaves it.
 *
 * The entry point for using Stateful inside Compose. Everything created under the returned owner —
 * effects, nested owners — dies with the composable, which is the same contract `DisposableEffect`
 * gives Compose's own resources.
 *
 * ```kotlin
 * @Composable
 * fun Profile(model: ProfileModel) {
 *     val root = rememberStatefulRoot()
 *     val title by model.title.asComposeState(root)
 *     Text(title)
 * }
 * ```
 */
@Composable
public fun rememberStatefulRoot(scheduler: Scheduler = Scheduler.Immediate): Owner {
    val owner = remember(scheduler) { createOwner(scheduler) }
    DisposableEffect(owner) {
        onDispose { owner.dispose() }
    }

    return owner
}

/**
 * Observes this Stateful state as a Compose one, so reading it recomposes.
 *
 * ### Read this before using the two libraries together
 *
 * **`State.value` means the opposite thing in each.** In Compose, reading `.value` is how you
 * subscribe — it is the tracked read, and the recomposition scope registers on it. In Stateful,
 * reading `.value` is how you *avoid* subscribing; the tracked read is `state()`.
 *
 * The consequence is a bug with no compile error and no exception:
 *
 * ```kotlin
 * @Composable
 * fun Broken(title: State<String>) {
 *     Text(title.value)      // reads once, never recomposes
 * }
 *
 * @Composable
 * fun Fixed(title: State<String>, root: Owner) {
 *     val text by title.asComposeState(root)
 *     Text(text)             // recomposes
 * }
 * ```
 *
 * Convert at the boundary. Never pass a Stateful `State` into a composable and read `.value` from
 * it, and never assume a habit from one library carries to the other.
 *
 * [owner] bounds the bridging effect. Use [rememberStatefulRoot] to get one tied to the composition.
 *
 * ### The other direction
 *
 * Observing a Compose state *as* a Stateful one is not provided here. Compose exposes its changes
 * through `snapshotFlow`, which needs a coroutine scope to collect in, and taking a coroutines
 * dependency to ship one function would push it onto every consumer of this adapter. The recipe is
 * short and belongs where the scope already is:
 *
 * ```kotlin
 * val bridged = mutableStateOf(composeState.value)
 * scope.launch {
 *     snapshotFlow { composeState.value }.collect { bridged.set(it) }
 * }
 * ```
 *
 * That direction is asynchronous whichever way it is written — the Stateful state catches up when
 * the Compose snapshot is applied, not at the instant of the write. Going through `snapshotFlow` is
 * what makes it respect Compose's snapshot boundaries instead of reading across them. For a control
 * that owns its own edits, [asComposeMutableState] carries writes back without any of this.
 */
public fun <T> State<T>.asComposeState(owner: Owner): ComposeState<T> {
    val target = composeMutableStateOf(value)
    runWithOwner(owner) {
        effect { target.value = this@asComposeState() }
    }

    return target
}

/**
 * A Compose state that writes back through to this Stateful state.
 *
 * For a `TextField` or any other control that owns its own edits: reads recompose, and writes reach
 * the Stateful source. The two directions do not loop, because each side compares before storing.
 *
 * That relies on this state's equality, so a source built with
 * [dev.deftu.stateful.Equality.never] **will** loop. Do not bridge one.
 */
public fun <T> MutableState<T>.asComposeMutableState(owner: Owner): ComposeMutableState<T> {
    val target = composeMutableStateOf(value)
    runWithOwner(owner) {
        effect {
            val incoming = this@asComposeMutableState()
            if (target.value != incoming) target.value = incoming
        }
    }

    return WritableComposeState(target, this)
}

private class WritableComposeState<T>(
    private val delegate: ComposeMutableState<T>,
    private val source: MutableState<T>,
) : ComposeMutableState<T> {
    override var value: T
        get() = delegate.value
        set(newValue) {
            if (delegate.value != newValue) delegate.value = newValue
            if (source.value != newValue) source.set(newValue)
        }

    override fun component1(): T = value

    override fun component2(): (T) -> Unit = { newValue -> value = newValue }
}
