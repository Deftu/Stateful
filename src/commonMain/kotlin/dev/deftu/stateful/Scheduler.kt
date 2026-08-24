package dev.deftu.stateful

/**
 * Decides when and where an effect body runs.
 *
 * The graph resolves under its lock and hands finished effect bodies here, so a scheduler is free
 * to defer, batch, or marshal them onto another thread. This is what makes the UI adapters
 * possible: a retained-mode toolkit needs its render thread, Compose has a frame clock, React
 * wants effects folded into its own scheduler.
 *
 * A scheduler is chosen per root and inherited by every computation under it.
 */
public fun interface Scheduler {
    /** Runs [task] at whatever point this scheduler considers correct. */
    public fun schedule(task: () -> Unit)

    /** The two schedulers the library ships; every host supplies its own beyond these. */
    public companion object {
        /**
         * Runs the task on the calling thread, before returning. The default.
         *
         * Effects still run with the runtime lock released, so this is safe — it is immediate,
         * not reentrant into the graph's critical section.
         */
        public val Immediate: Scheduler = Scheduler { task -> task() }

        /**
         * A scheduler that collects tasks until [Queued.drain] is called.
         *
         * The shape every host with its own loop needs: a game drains at the top of a frame, a
         * terminal UI drains before a redraw, a test drains to make assertions deterministic.
         * Without it each of those hand-writes the same class.
         *
         * Draining runs the tasks queued so far. Tasks queued *during* a drain are picked up by
         * the same call, so an effect that writes a source settles before [Queued.drain] returns
         * rather than being held to the next frame.
         */
        public fun queued(): Queued = Queued()
    }

    /** A [Scheduler] that holds tasks until [drain] runs them. See [Scheduler.queued]. */
    public class Queued internal constructor() : Scheduler {
        private val pending = mutableListOf<() -> Unit>()

        /** How many tasks are waiting. */
        public val size: Int
            get() = pending.size

        override fun schedule(task: () -> Unit) {
            pending.add(task)
        }

        /**
         * Runs every queued task, including any queued while draining.
         *
         * A task that throws does not swallow the rest: the first failure is rethrown once the
         * queue is empty, with the others attached to it.
         */
        public fun drain() {
            var failure: Throwable? = null

            while (pending.isNotEmpty()) {
                val task = pending.removeAt(0)
                try {
                    task()
                } catch (throwable: Throwable) {
                    if (failure == null) failure = throwable else failure.addSuppressed(throwable)
                }
            }

            if (failure != null) throw failure
        }
    }
}
