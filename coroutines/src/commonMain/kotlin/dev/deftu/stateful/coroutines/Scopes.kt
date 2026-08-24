package dev.deftu.stateful.coroutines

import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.dsl.createOwner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * A [Scheduler] that runs effect bodies as coroutines in this scope.
 *
 * Effects land on the scope's dispatcher rather than on whichever thread performed the write,
 * which is the point on a server where writes arrive on request threads.
 *
 * Ordering is the dispatcher's, not the graph's: two effects scheduled in sequence are not
 * guaranteed to run in that order unless the dispatcher is single-threaded. Effects that must be
 * ordered relative to each other belong in one effect.
 */
public fun CoroutineScope.asScheduler(): Scheduler = Scheduler { task -> launch { task() } }

/** A [Scheduler] that runs effect bodies on this dispatcher, in [scope]. */
public fun CoroutineDispatcher.asScheduler(scope: CoroutineScope): Scheduler =
    Scheduler { task -> scope.launch(this) { task() } }

/**
 * Creates an [Owner] whose lifetime is this scope's.
 *
 * Cancelling the scope disposes the owner, so a coroutine scope that already models the right
 * lifetime — a request, a connection, a screen — does not need a second teardown hook wired up
 * beside it.
 *
 * Defaults to dispatching effects into the scope. Pass [Scheduler.Immediate] to keep them on the
 * writing thread, or another scheduler where the host demands a particular one.
 */
public fun CoroutineScope.statefulOwner(scheduler: Scheduler = asScheduler()): Owner {
    val owner = createOwner(scheduler)
    coroutineContext.job.invokeOnCompletion { owner.dispose() }
    return owner
}

/**
 * Disposes this owner when [job] completes.
 *
 * The manual form of [statefulOwner], for an owner that already exists or that needs a scheduler
 * unrelated to the job's dispatcher.
 */
public fun Owner.disposeWith(job: Job) {
    job.invokeOnCompletion { dispose() }
}
