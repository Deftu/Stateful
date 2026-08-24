# Concurrency

## The model

One coarse reentrant lock guards all graph mutation and traversal.

Fine-grained locking over a graph traversed in dependency order invites deadlock, and computations
are meant to be cheap, so the trade was made deliberately. The cost is real and worth stating
plainly: **every write, and every read of a non-clean node, serialises globally.** A clean read is
still guarded, but does no work beyond taking the lock.

Reentrancy is required, not incidental. Resolving a memo reads other states while already holding
the lock.

On JS and wasm the lock compiles to a no-op, so none of this costs anything in the browser.

## What is guaranteed

**No torn reads.** A read either sees the state before a write or fully after it, never a
half-propagated graph. A diamond read concurrently with writes always yields a value some source
value actually produced:

```kotlin
val root = mutableStateOf(1)
val doubled = memo { root() * 2 }
val tripled = memo { root() * 3 }
val sum = memo { doubled() + tripled() }

// sum.value is always a multiple of 5, from any thread, at any time
```

**Concurrent writes lose nothing.** Writes serialise; each one marks what it must.

**Listeners and effect bodies never see a torn value.** Both run after the graph is consistent and
the lock has been released.

**Disposal races cleanly with propagation.** Creating and disposing owners on one thread while
another writes is safe; a disposed node stops propagating and never recomputes.

**Two threads may flush concurrently.** The re-entrancy guard that stops a flush recursing into
itself is per-thread, not shared, and that is load-bearing: a shared guard lets one thread's flush
silently cancel another's and strand its effects. This was measured — fifty writes produced
thirty-three effect runs.

**An effect may write the state it reads.** It re-enters, and it settles. An effect that writes from
another thread does not deadlock.

The JVM stress suite in `src/jvmTest/kotlin/ConcurrencyTest.kt` asserts each of these with eight
threads.

## What is not guaranteed

**Effect ordering under a multi-threaded scheduler.** The graph hands bodies to the scheduler in a
well-defined order; what happens after that is the scheduler's. Two effects dispatched onto a
multi-threaded dispatcher may run in either order. Effects that must be ordered relative to each
other belong in one effect.

**Reactive collections under concurrent structural edits.** Reads are safe against a concurrent
write. Two concurrent writers are not — the backing list, map or set is an ordinary one. Serialise
writers.

**`StateWarnings.handler`.** Process-wide configuration, not per-call state, and not synchronised.
Set it at startup.

**Which thread a cleanup runs on.** `dispose()` runs cleanups on the calling thread rather than
through a `Scheduler`. A cleanup touching a thread-affine resource must be disposed from a thread
allowed to touch it.

## What a memo body may not do

**Memo bodies run under the graph lock.** A tracked read resolves the node it reads, and resolving
recomputes, all inside the critical section.

That gives one hard rule with three consequences:

> A memo body must be pure, cheap, and take no other lock.

- **No other lock.** A foreign lock acquired under the graph lock is a deadlock waiting for a thread
  that takes them in the other order. Not a slowdown — a real, permanent deadlock.
- **No blocking and no I/O.** Every other thread touching the graph is stalled for the duration.
- **No suspending.** A memo body is not a suspending context, and there is nowhere to suspend to.
- **No side effects.** A memo may run more or fewer times than you expect: it does not run when
  nothing reads it, and it does not re-run when its inputs settle back. Anything observable from
  outside is unpredictable.

**Effect bodies are the opposite.** They run with the lock released, through a
[`Scheduler`](scheduling.md). Blocking, other locks, foreign threads, launching a coroutine — all
fine. So is the rule for cleanups.

That gives the standard shape for anything asynchronous: read in the effect, do the work off the
graph, write the result back into a source.

```kotlin
val query = mutableStateOf("kotlin")
val results = mutableStateOf<List<String>>(emptyList())

val owner = createRoot { owner ->
    effect {
        val current = query()
        results.set(search(current))   // may block: the lock is released here
    }
    owner
}

query.set("multiplatform")             // results follows
```

With coroutines, `launch` inside the effect and write the source when the call returns — see
[Adapters](adapters.md#coroutines).

## Reading state from another thread

Reading is always safe. What is not safe is *assuming* the value you read is still current by the
time you use it, which is true of any concurrent program and is not special here.

If you need a value that will not move under you, take a [`snapshot`](operators.md#snapshot). It
reads untracked and detaches collections into read-only copies, which is exactly what a payload
crossing a thread boundary wants.

## A note on Compose

Compose solves thread safety with MVCC snapshots; this library uses a lock. The two systems are
deliberately **not** unified. Bridge at the edge with `stateful-compose` and both stay honest about
what they guarantee.
