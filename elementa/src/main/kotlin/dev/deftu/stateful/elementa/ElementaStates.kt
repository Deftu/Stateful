package dev.deftu.stateful.elementa

import dev.deftu.stateful.Diagnostics
import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.currentOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import gg.essential.elementa.state.v2.ReferenceHolder
import gg.essential.elementa.unstable.state.v2.MutableState as ElementaMutableState
import gg.essential.elementa.unstable.state.v2.ReferenceHolderImpl
import gg.essential.elementa.unstable.state.v2.State as ElementaState
import gg.essential.elementa.unstable.state.v2.effect as elementaEffect
import gg.essential.elementa.unstable.state.v2.mutableStateOf as elementaMutableStateOf

/**
 * Observes an Elementa V2 state as a Stateful one.
 *
 * The bridge is deliberately **one node wide**. One Elementa state becomes one Stateful source, and
 * anything derived from it is derived on this side. Bridging a derived graph state-by-state would
 * run two independent propagation schemes over the same data, and Elementa's is the eager one this
 * library exists to replace — the diamond would glitch again on the way across.
 *
 * Needs an enclosing owner: the Elementa effect feeding the source is unregistered when that owner
 * is disposed, and without one nothing ever unregisters it.
 */
public fun <T> ElementaState<T>.asStatefulState(): State<T> {
    warnWithoutOwner("asStatefulState")

    val source = mutableStateOf(getUntracked())
    val holder = ReferenceHolderImpl()
    val stop = elementaEffect(holder) { source.set(this@asStatefulState()) }

    onCleanup {
        stop()
        holder.keepAlive()
    }

    return source
}

/**
 * Publishes a Stateful state as an Elementa V2 state.
 *
 * Pushed rather than wrapped. An Elementa `State` produces its value inside Elementa's own
 * `Observer`, so a wrapper that read through would register on neither graph and would never
 * update; the value has to be written into an Elementa source by a Stateful effect.
 *
 * Needs an enclosing owner, which is what stops the feeding effect.
 */
public fun <T> State<T>.asElementaState(): ElementaState<T> {
    warnWithoutOwner("asElementaState")

    val target = elementaMutableStateOf(value)
    effect { target.set(this@asElementaState()) }
    return target
}

/**
 * Publishes a Stateful mutable state as an Elementa V2 mutable state, writable from either side.
 *
 * Writes through the returned state are forwarded here, and changes here are pushed across. The two
 * directions do not loop: each side compares before storing, so the echo of a write arrives as a
 * no-op and propagation stops. That relies on this state's own equality, so a source built with
 * [dev.deftu.stateful.Equality.never] **will** loop — do not bridge one.
 */
public fun <T> MutableState<T>.asElementaMutableState(): ElementaMutableState<T> {
    warnWithoutOwner("asElementaMutableState")

    val target = elementaMutableStateOf(value)
    val holder = ReferenceHolderImpl()

    effect { target.set(this@asElementaMutableState()) }

    val stop = elementaEffect(holder) {
        val incoming = target()
        if (incoming != value) set(incoming)
    }

    onCleanup {
        stop()
        holder.keepAlive()
    }

    return target
}

/**
 * Creates a Stateful [Owner] whose lifetime this [ReferenceHolder] extends.
 *
 * Elementa holds effects weakly and uses a `ReferenceHolder` to keep them alive; this library holds
 * them strongly and uses an [Owner] to end them. The two models meet here: the holder keeps the
 * owner reachable for as long as the component lives, and the caller disposes the owner when the
 * component is removed.
 *
 * Holding is not the same as disposing. A held owner that is never disposed still leaks — the
 * holder only stops it being collected early.
 */
public fun ReferenceHolder.createStatefulOwner(scheduler: Scheduler = Scheduler.Immediate): Owner {
    val owner = createOwner(scheduler)
    holdOnto(owner)
    return owner
}

private fun warnWithoutOwner(what: String) {
    if (currentOwner() == null) {
        Diagnostics.onWarning(
            "$what called outside of createRoot; nothing will unregister the Elementa side",
        )
    }
}

/**
 * Elementa holds its effect registrations weakly, and this holder is the only strong reference to
 * one. Touching it here keeps it reachable until cleanup runs.
 */
private fun ReferenceHolderImpl.keepAlive() {
    this.toString()
}
