# Stateful documentation

Signals for Kotlin Multiplatform. This directory is the long-form documentation; the
[root README](../README.md) is the introduction and the setup instructions, and every public
declaration carries KDoc.

Read in order if you are new. Jump straight in if you are not.

| Page | What it covers |
| --- | --- |
| [Getting started](getting-started.md) | Install, your first state, your first effect, and the one rule that everything else assumes |
| [Core concepts](concepts.md) | Pull-based evaluation, tracked and untracked reads, `memo` against `derivedStateOf`, effects, equality |
| [Operators](operators.md) | `map`, `combine`, the null-handling reads, the per-type extensions, the delegates, `snapshot` |
| [Lifetimes](lifetimes.md) | Owners, `createRoot` against `createOwner`/`runWithOwner`, cleanups, the orphan warning |
| [Scheduling](scheduling.md) | `Scheduler`, `Immediate`, `queued()`, `batch`, and which thread an effect body lands on |
| [Reactive collections](collections.md) | Per-element tracking, `ListChange`, and what `mapKeyed` buys |
| [Concurrency](concurrency.md) | What the graph guarantees, the lock, and what a memo body may not do |
| [Adapters](adapters.md) | coroutines, Elementa, Compose, Svelte, React |
| [Testing](testing.md) | Owning a test's lifetime, making effects deterministic, asserting you have not leaked |
| [Performance](performance.md) | What is cheap and what is not, with the benchmark numbers |

## The shortest possible summary

```kotlin
val celsius = mutableStateOf(20)
val fahrenheit = memo { celsius() * 9 / 5 + 32 }

val root = createRoot { owner ->
    effect { println("${fahrenheit()}F") }   // prints 68F
    owner
}

celsius.set(100)                             // prints 212F
root.dispose()                               // the effect stops
```

Three kinds of thing, and nothing else:

- **Sources** hold a value you write. `mutableStateOf`.
- **Derivations** compute a value from other state. `memo`, `derivedStateOf`, and every operator.
- **Effects** do something with a value. `effect`, `subscribe`.

Sources and derivations are pure and need no lifetime. Effects touch the world, so they belong to
an [`Owner`](lifetimes.md) and stop when it is disposed.

## Upgrading from 0.7

Start with [MIGRATING.md](../MIGRATING.md). The evaluation model changed and one of the breaks —
`get()` splitting into a tracked `state()` and an untracked `state.value` — fails silently if you
pick the wrong one.

## A note on the samples

Every Kotlin sample on these pages that depends only on the core library is mirrored by
`src/commonTest/kotlin/DocsSamplesTest.kt`, which asserts what the surrounding prose claims rather
than merely that the code compiles. A sample nobody runs is an untested claim. Samples that need a
foreign framework are marked where they appear.
