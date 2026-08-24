# Performance

All numbers below are JMH averages on one developer machine: three forks, one-second iterations, the
GC profiler on. Three forks because a single one cannot separate a real change from JVM-to-JVM
variance, and the differences that matter here are single-digit nanoseconds. Allocation is per
operation.

Reproduce with `./gradlew :benchmarks:jmh`. It is not wired into `build` and takes several minutes.
Treat the absolute numbers as the shape of the cost model, not as a promise about your hardware.

## The graph

| Operation | Time | Allocation |
| --- | --- | --- |
| Untracked read of a source | ~4 ns | 0 B |
| Untracked read of a clean memo | ~4 ns | 0 B |
| Untracked read of a clean 10-memo chain | ~4 ns | 0 B |
| Write with no dependents | 14.5 ns | 16 B |
| Write, then read a memo | 30.6 ns | 32 B |
| Write, then read a diamond | 64.4 ns | 64 B |
| Write, then read a 10-memo chain | 202 ns | 176 B |
| Write that wakes an effect | ~74 ns | 136 B |

**Reads are free and allocation-free.** A clean read is a lock acquisition and a field access, no
matter how deep the chain behind it is. This is the hottest path in the library — an effect that
re-runs reads each of its dependencies, and most of those are unchanged — so it is the one that was
optimised hardest.

**Writes are cheap and scale with what actually changed.** A write marks; it does not compute.
Sixteen bytes for a write with no dependents is `Integer` boxing, inherent to a generic `State<T>` on
the JVM.

**A write that changes nothing costs a comparison.** Equality is checked before anything is marked,
so a redundant write does not reach the graph at all — no dependent is touched and no effect is
queued. This is why an idempotent write loop is not a problem.

## Reactive collections

| Operation | Time | Allocation |
| --- | --- | --- |
| Untracked indexed read | 0.6 ns | 0 B |
| Tracked indexed read | 9.7 ns | 0 B |
| Untracked map read | 2.6 ns | 0 B |
| Tracked map read | 4.8 ns | 0 B |
| Append and remove, under a size memo | 118 ns | 200 B |
| Replace an element, under a memo | 113 ns | 416 B |
| `mapKeyed` edit, 100 rows | 120 ns | 325 B |
| `mapKeyed` move, 100 rows | 14,790 ns | 31,347 B |

**An in-place edit under `mapKeyed` is O(1)** — 120 ns for one row out of a hundred. Each entry
watches its own slot and its own index, so an edit wakes that entry and reconciliation does not run
at all.

**A structural change is O(n),** and the two orders of magnitude between the edit and the move rows
are exactly that. Every position after the edit point now holds a different element, so the
key-to-result mapping is rebuilt and every entry whose position shifted is woken.

The trade is deliberate: in list-shaped UI, edits vastly outnumber structural changes. If your
workload is the other way round — a list that is constantly reordered and rarely edited — a plain
`memo { list.map { … } }` is simpler and not obviously worse.

## Lifetimes

| Operation | Time | Allocation |
| --- | --- | --- |
| Create and dispose an empty root | 9.3 ns | 32 B |
| Create and dispose a root with one effect | 112 ns | 672 B |
| Create and dispose a root with ten effects | 1,107 ns | 4,016 B |
| Create and dispose a root with ten cleanups | 117 ns | — |
| Create and dispose an effect on a deferring scheduler | 114 ns | 784 B |
| Subscribe and dispose | 48 ns | 352 B |

**Lifetime construction is the largest remaining cost in the library,** and it is not amortised: a
screen that builds a hundred effects pays it once per navigation, not per frame. Extrapolating the
ten-effect row, a hundred effects is on the order of 11 µs and 40 KB. That is fine for a screen
transition and would not be fine per frame.

An empty owner allocates almost nothing: `Owner` and `StateNode` allocate their child, cleanup,
dependency and dependent lists on first use, because most are never used — an effect never has
dependents, a source never has dependencies, and an owner rarely holds all three kinds.

**Every subscription builds an effect node**, an owner lookup and a disposable, which is the 48 ns
and 352 bytes above. React and Svelte adapters pay it per component. That is per mount, not per
render, so it does not matter; a subscription created per frame would.

## What to do with this

**Prefer `memo` when the cutoff matters, `derivedStateOf` when it does not.** A memo node costs an
allocation and a graph edge. For `state() + 1` that is more than the work. For anything whose result
changes less often than its inputs, the cutoff saves more than the node costs — that is the whole
point of `name().isNotBlank()` being a memo. See
[concepts](concepts.md#memo-against-derivedstateof).

**Batch related writes.** Not primarily for speed — for correctness, so downstream never sees an
intermediate state that never existed — but one effect run instead of five is also five times less
work.

**Use `rawStateOf` for large payloads.** Structural equality on a big immutable blob costs as much as
the work the graph is trying to avoid. Referential equality is one comparison.

**Avoid whole-list reads in a hot effect.** `list.joinToString { … }` or `list.count { … }` tracks
every element, so any edit anywhere wakes it. Read the positions you need, or use `mapKeyed`.

**Do not fear unobserved derived state.** A memo nothing reads costs one object and never runs. A
class exposing twenty memos of which two are used pays for eighteen objects and no computation.

**Do not build lifetimes per frame.** Build them per mount. The graph is designed for a stable set
of effects reading changing values, not for a set of effects that churns.

## Measured and rejected

Two ideas that look right and are not, recorded so nobody spends the afternoon again.

**Reusing the flush buffers.** `flush` allocates two lists per turn that dispatches. Holding them
per-thread instead cut allocation on the effect path from 96 to 80 bytes and made it a third
*slower* — 94.5 ns against 71.4 ns, with error bars nowhere near overlapping. Most flushes find
nothing to do, and clearing two buffers under the lock before discovering that costs more than the
allocation it saves.

**Capacity hints on the lazily allocated lists.** `ArrayList(2)` allocates a backing array
immediately and then resizes as the list grows. A bare `ArrayList()` defers the array entirely and
then takes one default-sized array, and beats both: with hints, a root with ten cleanups went from
117 ns to 155 ns and allocated 53 per cent more.
