# Core concepts

## Pull-based evaluation

A write does not compute anything. It marks the nodes that might be affected. Values are computed
when something reads them.

That one sentence explains most of the library's behaviour, and the clearest demonstration is a
diamond — two derivations over one source, joined again:

```kotlin
val root = mutableStateOf(1)
val a = memo { root() * 2 }
val b = memo { root() * 3 }
val sum = memo { a() + b() }

val owner = createRoot { owner ->
    effect { println(sum()) }   // prints 5
    owner
}

root.set(2)                     // prints 10, once
```

An eager, push-based library prints `7` first — `a` updated, `b` not yet — and then `10`. `7` is not
the value of the system at any point in time. Nothing here produces it, because `sum` is not
computed until the effect asks for it, and by then both branches have settled. This is what
"glitch-free" means, and it is not a special case in `sum`: it falls out of computing on read.

The same shape explains three other behaviours:

**An unobserved memo never runs.**

```kotlin
var computations = 0
val source = mutableStateOf(1)
memo {
    computations++
    source() * 2
}

source.set(2)
source.set(3)

// computations == 0
```

Nothing read it, so nothing needed the answer. Building a derived state costs an object and no
work.

**A memo caches until a dependency it read has changed.** Two reads in a row run the body once.

**A memo whose value did not change stops propagation dead.**

```kotlin
val source = mutableStateOf(1)
val parity = memo { source() % 2 }

var downstream = 0
val derived = memo {
    downstream++
    parity()
}

derived.value       // 1, downstream == 1
source.set(3)
derived.value       // 1, downstream is still 1
```

`source` changed, so `parity` recomputed. Its answer was the same, so `derived` was never marked.
This equality cutoff is the main reason to prefer `memo` over a plain lambda, and the reason nearly
every operator in this library is built on one.

### The three node states

Internally a node is Clean, Check or Dirty, and marking only ever raises. A write marks its direct
dependents Dirty and everything transitively downstream Check. Check means "a dependency of yours
may have changed; ask before trusting your value". Resolving a Check node walks its dependencies,
and only recomputes if one of them actually produced a new value.

You never see these states. They are why a write into a wide graph is cheap: the closure gets a
cheap mark, and only the paths whose values genuinely changed recompute.

Only effects are queued, and they are queued on Check as well as Dirty — an effect is where a pull
has to start. Memos are never queued, which is what makes an unobserved memo free.

## Tracked and untracked reads

| | Read | Registers a dependency |
| --- | --- | --- |
| `state()` | tracked | yes |
| `state.value` | untracked | no |
| `untracked { state() }` | untracked | no |
| `snapshot(state)` | untracked, detached | no |
| `getOrDefault`, `getOrElse`, `getOrThrow` | tracked | yes |
| `stateBound(state)` delegate | untracked | no |
| `trackedStateBound(state)` delegate | tracked | yes |

Outside a `memo` or `effect` the two forms do the same thing, because there is no computation to
register against. The distinction only matters inside one.

`untracked { }` is the block form, for reading state inside a computation without depending on it:

```kotlin
val tracked = mutableStateOf(1)
val ignored = mutableStateOf(100)

var runs = 0
val combined = memo {
    runs++
    tracked() + untracked { ignored() }
}

combined.value      // 101, runs == 1
ignored.set(200)
combined.value      // still 101, runs is still 1 — the value is cached and stale
tracked.set(2)
combined.value      // 202, runs == 2
```

Note the middle line. An untracked read is not a promise that the value stays current; it is a
promise that changing it will not wake you. The memo picks the new value up on its next run, for
whatever other reason it runs.

Tracking follows the call stack, not the lexical body. A state read inside a callback your memo
passed to some unrelated library still registers, because the tracking context is per-thread and
lives for the duration of the run:

```kotlin
val name = mutableStateOf("world")
val document = Document(listOf(Node { "hello, ${name()}" }))
val rendered = mutableListOf<String>()

val owner = createRoot { owner ->
    effect { rendered.add(document.render()) }
    owner
}

name.set("there")

// rendered == ["hello, world", "hello, there"]
```

That is what makes this usable with a templating or layout library that knows nothing about it.

## `memo` against `derivedStateOf`

Both derive a value. They differ in whether there is a node behind it.

```kotlin
val source = mutableStateOf(1)
val cached = memo { source() % 2 }
val uncached = derivedStateOf { source() % 2 }
```

`memo` allocates a graph node: the result is cached, the body runs only when a dependency changed,
and an unchanged result stops propagation.

`derivedStateOf` allocates nothing but a lambda with a `State` interface on it. The body runs on
every read, in the caller's tracking context, so a tracked read of the result registers dependencies
on whatever the body read. There is no cache and — the part that catches people — **no equality
cutoff**:

```kotlin
val source = mutableStateOf(1)
val fromDerived = mutableListOf<Int>()
val fromMemo = mutableListOf<Int>()

val owner = createRoot { owner ->
    effect { fromDerived.add(uncached()) }
    effect { fromMemo.add(cached()) }
    owner
}

source.set(3)

// fromDerived == [1, 1] — woken, recomputed, same answer
// fromMemo    == [1]    — the memo absorbed it
```

Use `derivedStateOf` when the body is cheaper than the node `memo` would allocate: `state() + 1`, a
field access, a comparison whose result you do not care to cut off. Use `memo` when the computation
is expensive, or when the cutoff matters. Deriving `name().isNotBlank()` with `derivedStateOf` wakes
dependents on every keystroke; deriving it with `memo` wakes them only when the answer flips.

That cutoff is usually the whole reason an operator exists, which is why `map`, `combine` and
everything in `ext` build memos.

## Effects

An effect runs its body once, and again whenever a state it read has changed.

Dependencies are re-collected on every run, so a conditional effect subscribes only to the branch it
took:

```kotlin
val condition = mutableStateOf(true)
val left = mutableStateOf("left")
val right = mutableStateOf("right")

var runs = 0
val chosen = memo {
    runs++
    if (condition()) left() else right()
}

chosen.value            // "left", runs == 1
right.set("changed")
chosen.value            // "left", runs is still 1 — right was never a dependency
condition.set(false)
chosen.value            // "changed", runs == 2 — now it is
left.set("ignored")
chosen.value            // "changed", runs is still 2 — left no longer is
```

The same holds for effects. This is not an optimisation you opt into; it is a consequence of
collecting dependencies during the run rather than declaring them.

Effect bodies run through a [`Scheduler`](scheduling.md) with the graph lock released, so they may
block, take other locks, and touch another thread. Memo bodies may not — see
[Concurrency](concurrency.md).

### `effect` against `subscribe`

`subscribe` is the low-level form. It takes one state, fires on change only — not on subscription —
and hands back a `Subscription` that is the whole lifetime. No owner is involved.

```kotlin
val source = mutableStateOf(0)
val observed = mutableListOf<Int>()

val subscription = source.subscribe { value -> observed.add(value) }
source.set(1)
source.set(2)
subscription.dispose()
source.set(3)

// observed == [1, 2]
```

Use `effect` for anything that reads more than one state or that belongs to a component's lifetime.
Use `subscribe` when you have exactly one state, want no owner, and are bridging to something that
wants a callback and an unsubscribe function — which is exactly what the React and Svelte adapters
need, and why it is a core primitive rather than a leftover.

`subscribeOnce` disposes itself before invoking the listener, so it fires for the next change and
nothing after.

## Equality

Every state carries an `Equality`, consulted on write for sources and after recomputation for memos.
Returning `true` stops propagation: dependents are not marked and no effect runs.

```kotlin
Equality.structural()   // ==, the default
Equality.referential()  // ===
Equality.never()        // always propagates
```

Any other rule is a lambda, and because `equality` is the last parameter of `mutableStateOf` it
takes a trailing-lambda form:

```kotlin
val parity = mutableStateOf(1) { a, b -> a % 2 == b % 2 }
val observed = mutableListOf<Int>()
parity.subscribe { value -> observed.add(value) }

parity.set(3)   // odd, same parity: nothing
parity.set(5)   // odd: nothing
parity.set(2)   // even: fires

// observed == [2]
```

`memo` takes one too — `memo(Equality.referential()) { … }` — which is the way to cut off a
derivation whose result is expensive to compare.

**`rawStateOf`** is `mutableStateOf` with referential equality, for large or expensive-to-compare
payloads. A structural comparison of a big immutable blob on every write costs as much as the work
the graph is trying to save:

```kotlin
val first = listOf("a")
val second = listOf("a")          // equal, not identical

val raw = rawStateOf(first)
raw.set(second)                   // propagates: a different instance
raw.set(second)                   // does not: the same instance

val ordinary = mutableStateOf(first)
ordinary.set(second)              // does not propagate: structurally equal
```

Nothing here tracks the *contents* of a value, so this is not a deep-versus-shallow switch. It
changes only the cost and the meaning of a write. When the contents themselves need observing,
reach for a [reactive collection](collections.md).

**`Equality.never()`** is the escape hatch for payloads mutated in place, and for a state used
purely as a signal that something happened. It is also a loaded gun: never feed one to React, where
guaranteed propagation downstream of `useSyncExternalStore` means an infinite render loop, and never
bridge one bidirectionally, because the loop-breaking in those bridges is a comparison this equality
answers `false` to.

**`stateOf`** creates a constant. Reads are free and register nothing, tracked or not — there is
nothing to depend on.

## Identity, not value

`State` uses identity `equals` and `hashCode`.

```kotlin
stateOf("a") == stateOf("a")               // false
stateOf("a").value == stateOf("a").value   // true
```

0.7 delegated both to the value, which meant `hashCode` mutated and any state used as a map key or
held in a `HashSet` was silently corrupt. Compare values, not states.
