# Migrating to 0.8

0.8 replaces the evaluation model. Every consumer of 0.7 has to change something, and one of those
changes is silent if you get it wrong — read [Reads](#reads-the-one-that-fails-quietly) first.

## Why it changed

0.7 was push-based and eager: a write walked the graph immediately, computing as it went. That
produces values that never existed. Given

```kotlin
val root = mutableStateOf(1)
val a = root.map { it * 2 }
val b = root.map { it * 3 }
val sum = a.combine(b) { x, y -> x + y }
```

`root.set(2)` made a subscriber on `sum` observe `[7, 10]`. `7` is `a` updated and `b` not yet — no
state of the system ever held it. This was not a bug in `combine`; it is what eager depth-first
propagation does to any shape where two derived values share an ancestor.

0.8 is pull-based. A write marks what may be affected; values compute when read. The same code now
observes `[5, 10]`, and derived state is lazy: an unobserved `memo` never runs.

## Reads: the one that fails quietly

`get()` is **removed**, and replaced by two reads that mean different things.

| | |
| --- | --- |
| `state()` | **Tracked.** Registers a dependency. Use inside `memo` and `effect`. |
| `state.value` | **Untracked.** A plain read. No dependency. |

```kotlin
// 0.7
val name = state.get()

// 0.8 — inside a memo or effect, where the surrounding code should react
val name = state()

// 0.8 — a one-off read that should not subscribe
val name = state.value
```

A tracked read mutates the dependency graph, so it is a function call. An untracked read is plain
data access, so it is a property.

**Choosing wrongly compiles and produces no error.** `memo { config.value }` never updates. If you
are unsure, use `state()`: an unnecessary dependency causes extra work, while a missing one causes
a value that silently stops changing.

Functions that read and unwrap follow the same rule and now **track**: `getOrDefault`, `getOrElse`
and `getOrThrow`. In 0.7 they were the only read; in 0.8 they behave like `state()`. For the
untracked form, write `state.value ?: fallback`.

## Effects now need a lifetime

In 0.7 a derived state subscribed to its source on construction and stayed subscribed until you
called `dispose()` — which nothing did automatically, so a `map` chain built per frame leaked every
intermediate.

In 0.8, `memo` holds nothing until something reads it, so derived state needs no lifetime at all. An
`effect` does:

```kotlin
val owner = createRoot { owner ->
    effect { view.title = title() }
    onCleanup { view.detach() }
    owner
}

owner.dispose()   // tears down the subtree, cleanups in reverse creation order
```

For a host that hands you callbacks rather than a scope — a server framework, a game, an Android
`Activity` — build the owner separately and enter it later:

```kotlin
private val root = createOwner()

fun start() = runWithOwner(root) { effect { … } }
fun stop() = root.dispose()
```

An `effect` created outside any root still runs; it attaches to a process-wide root and logs a
warning naming it a probable leak.

## Removed types

The concrete derived-state classes are gone. They were the push model, and there is nothing left for
them to do.

| 0.7 | 0.8 |
| --- | --- |
| `MappedState(source, mapper)` | `memo { mapper(source()) }`, or `source.map(mapper)` |
| `ZippedState(a, b)` | `memo { a() to b() }`, or `a.zip(b)` |
| `FlatMappedState(source, mapper)` | `memo { mapper(source())() }`, or `source.flatMap(mapper)` |
| `SimpleState`, `SimpleMutableState` | `stateOf`, `mutableStateOf` |
| `mapped.rebind(other)` | `flatMap` over a state holding the source |
| `zipped.rebindFirst(other)` | as above |
| `state.notifyCurrent()` | nothing — see below |
| `MappedState.dispose()` | dispose the owner instead |

`map`, `flatMap`, `zip`, `combine`, the `*StateOf` factories, everything in `ext/`, the delegates,
`subscribe`, `subscribeOnce`, `Subscription` and `Disposable` all still work. They return `State<T>`
now rather than a concrete class, which only matters if you named those types.

### Re-pointing a derived state

`rebind` existed because a derived state was bound to one source for life. A state holding the
source does the same thing and composes:

```kotlin
// 0.7
val mapped = mappedStateOf(first) { it * 2 }
mapped.rebind(second)

// 0.8
val source = mutableStateOf<State<Int>>(first)
val mapped = source.flatMap { it }.map { it * 2 }
source.set(second)
```

### `notifyCurrent`

There is no push to trigger and no "current" distinct from what the next read computes, so it has no
meaning. If you used it to force dependents to re-run regardless of equality, build the source with
`Equality.never()` — it propagates on every write.

## `equals` and `hashCode` are identity again

0.7 delegated both to the value. Since states are mutable, `hashCode` mutated, so any `State` used
as a map key or held in a `HashSet` was silently corrupt, and two unrelated states holding the same
value compared equal.

```kotlin
// 0.7: true
stateOf("a") == stateOf("a")

// 0.8: false. Compare values instead.
stateOf("a").value == stateOf("a").value
```

## `State` is an interface

It was an abstract class. If you subclassed it, implement the interface instead — or, more likely,
use `memo`, which is what most subclasses existed to do by hand.

## What is new

None of this is required to migrate, but it is what the model buys.

- **`effect`, `createRoot`, `onCleanup`, `Owner`** — explicit lifetimes.
- **`Scheduler`** — effects dispatch through it, so a render thread, frame loop or coroutine scope
  can own when they run. `Scheduler.queued()` drains on demand.
- **`batch { }`** — several writes, one effect run.
- **`untracked { }`** — read without subscribing.
- **`Equality`** — per-state, with `structural`, `referential` and `never`.
- **`derivedStateOf`** — an uncached derivation, for when a `memo` node costs more than the work.
- **`reactiveListOf` / `reactiveMapOf` / `reactiveSetOf`** — tracked per element, and `mapKeyed` for
  keyed reconciliation.
- **`rawStateOf`, `snapshot`** — identity equality for large payloads, and a non-reactive copy.
- **Adapters** — `stateful-coroutines`, `-elementa`, `-compose`, `-svelte`, `-react`.

## If you use Compose

`State.value` means the opposite thing in each library. In Compose, reading `.value` subscribes. In
Stateful, reading `.value` is how you *avoid* subscribing. Convert at the boundary with
`stateful-compose` rather than passing a Stateful `State` into a composable, and never carry the
habit across.
