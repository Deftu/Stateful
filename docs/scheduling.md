# Scheduling

Every effect body — the first run included — is handed to a `Scheduler`. That is the seam a host
uses to decide *when* and *on which thread* effects run.

```kotlin
public fun interface Scheduler {
    public fun schedule(task: () -> Unit)
}
```

A scheduler is chosen per root and inherited by every computation under it:

```kotlin
val root = createOwner(myScheduler)
runWithOwner(root) { effect { … } }        // dispatched through myScheduler

createRoot(myScheduler) { owner -> … }     // same
```

The graph resolves under its lock and hands *finished* bodies to the scheduler, with the lock
released. A scheduler is therefore free to defer, batch, or marshal onto another thread without
risking the graph.

## `Scheduler.Immediate`

The default. Runs the task on the calling thread, before `schedule` returns.

```kotlin
val source = mutableStateOf(0)
val observed = mutableListOf<Int>()

val owner = createRoot(Scheduler.Immediate) { owner ->
    effect { observed.add(source()) }
    owner
}

source.set(1)
// observed == [0, 1] — already, before set returned
```

Immediate is not reentrant into the graph's critical section: the lock is released before the body
runs, which is why an effect body may safely write another source, take a lock, or block.

**Immediate runs the body wherever the write happened.** On a server that is a request thread; in a
game client it is whichever thread called `set`. In any host with a designated thread, supplying a
scheduler is mandatory rather than optional.

## `Scheduler.queued()`

Collects tasks until you drain them. This is the shape every host with its own loop needs: a game
drains at the top of a frame, a terminal UI before a redraw, a test to make assertions
deterministic.

```kotlin
val scheduler = Scheduler.queued()
val source = mutableStateOf(0)
val observed = mutableListOf<Int>()

val root = createOwner(scheduler)
runWithOwner(root) { effect { observed.add(source()) } }

scheduler.size      // 1 — even the first run is scheduled
observed            // [] — it has not run, and has no dependencies yet

scheduler.drain()
observed            // [0]

source.set(1)
source.set(2)
observed            // still [0]
scheduler.size      // 1, not 2

scheduler.drain()
observed            // [0, 2]
```

Two things worth naming there.

**Dispatch coalesces.** Several writes before a drain queue one run of the effect, not one per
write, and that run sees the latest value. Ten writes still produce one task.

**Nothing has run before the first drain,** so the effect has no dependencies yet. Under a deferring
scheduler, an effect that has not been drained is not yet subscribed to anything.

Draining runs the tasks queued so far *and* any queued during the drain, so an effect that writes a
source settles within the same call rather than being held to the next frame:

```kotlin
val scheduler = Scheduler.queued()
val first = mutableStateOf(0)
val second = mutableStateOf(0)
val observed = mutableListOf<String>()

val root = createOwner(scheduler)
runWithOwner(root) {
    effect { second.set(first() + 1) }
    effect { observed.add("second=${second()}") }
}
scheduler.drain()
observed.clear()

first.set(10)
scheduler.drain()

// observed == ["second=11"], and scheduler.size == 0
```

If a task throws, the rest still run. The first failure is rethrown once the queue is empty, with
the others attached to it.

## Writing your own

`Scheduler` is a `fun interface`, so a lambda will do. Whatever your host uses to get onto its own
thread goes inside it:

```kotlin
val queue = ArrayDeque<() -> Unit>()
val onMyLoop = Scheduler { task -> queue.addLast(task) }

val frame = mutableStateOf(0)
val rendered = mutableListOf<Int>()

val root = createOwner(onMyLoop)
runWithOwner(root) { effect { rendered.add(frame()) } }

// at the top of a frame
while (queue.isNotEmpty()) queue.removeFirst().invoke()
```

In a real host that body is `Window.enqueueRenderOperation(task)`, `SwingUtilities.invokeLater(task)`,
`vertx.runOnContext { task() }`, or whatever the equivalent is. The coroutines adapter ships the ones
built on a `CoroutineScope`; see [Adapters](adapters.md#coroutines).

Two constraints on anything you write:

**Ordering is yours to guarantee.** The graph hands tasks over in a well-defined order, but what
happens after that is the scheduler's. Two effects dispatched onto a multi-threaded dispatcher are
not guaranteed to run in the order they were scheduled. Effects that must be ordered relative to
each other belong in one effect.

**Do not drop tasks.** A scheduler that silently discards a task strands the effect: the graph has
already consumed the dirty state that would have re-scheduled it.

## `batch`

Batching is about *writes*, not about threads. It defers effect dispatch until the outermost
`batch` returns, so several writes that belong to one logical change produce one effect run:

```kotlin
val first = mutableStateOf(1)
val second = mutableStateOf(2)
val observed = mutableListOf<Int>()

val owner = createRoot { owner ->
    effect { observed.add(first() + second()) }
    owner
}

batch {
    first.set(10)
    second.set(20)
}

// observed == [3, 30] — one run for the batch, not two
```

Without the batch, an effect reading both sees `12` — a state that is arguably real but never
intended. Two writes that form one change should be one batch.

**Writes apply immediately.** Only dispatch is deferred, so a read inside the block sees pending
writes, memos included:

```kotlin
val source = mutableStateOf(1)
val doubled = memo { source() * 2 }
val seen = mutableListOf<Int>()

batch {
    source.set(5)
    seen.add(source.value)    // 5
    seen.add(doubled.value)   // 10
    source.set(7)
    seen.add(doubled.value)   // 14
}
```

**Nesting is by depth counter.** Only the outermost exit flushes. A throwing batch still flushes on
the way out, so a failure mid-block does not strand the writes that already applied.

**A memo absorbs a net-zero change; a direct effect does not.**

```kotlin
val source = mutableStateOf(1)
val doubled = memo { source() * 2 }

// an effect reading the source directly:
batch { source.set(2); source.set(1) }    // it runs, with the value it already had

// an effect reading the memo:
batch { source.set(2); source.set(1) }    // it does not run at all
```

The source genuinely changed twice, so anything depending on it directly was marked. The memo
recomputed, produced the value it already held, and its equality cut propagation off. If you want
that absorption for a direct reader, derive through a memo.

**Batching does not bypass the scheduler.** The deferred run is still handed to the root's
scheduler; batching only decides how many runs there are.

## Which thread does an effect body land on?

| Scheduler | Thread |
| --- | --- |
| `Scheduler.Immediate` | Whichever thread performed the write |
| `Scheduler.queued()` | Whichever thread calls `drain()` |
| `CoroutineScope.asScheduler()` | The scope's dispatcher |
| Your own | Wherever you put it |

Three things do **not** go through the scheduler, and run on the calling thread:

- **Memo bodies**, which run under the graph lock during a read. See
  [Concurrency](concurrency.md).
- **`subscribe` listeners**, which run after the graph has settled and the lock is released.
- **Cleanups and `dispose()`**, so a cleanup touching a thread-affine resource must be disposed from
  a thread allowed to touch it.
