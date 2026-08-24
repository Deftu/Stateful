# Stateful

[![wakatime](https://wakatime.com/badge/user/25be8ed5-7461-4fcf-93f7-0d88a7692cca/project/018c9cc0-7f56-4cb1-b257-8fd5f81573b6.svg)](https://wakatime.com/badge/user/25be8ed5-7461-4fcf-93f7-0d88a7692cca/project/018c9cc0-7f56-4cb1-b257-8fd5f81573b6)

---

[![Discord Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/social/discord-singular_64h.png)](https://s.deftu.dev/discord)
[![Ko-Fi Badge](https://raw.githubusercontent.com/intergrav/devins-badges/v2/assets/cozy/donate/kofi-singular_64h.png)](https://s.deftu.dev/kofi)

---

Signals for Kotlin Multiplatform. A value you can read, observe, and derive other values from,
with automatic dependency tracking and no manual wiring.

Evaluation is **pull-based**. Writing to a state does not compute anything — it marks what might
be affected. Values are computed when they are read, which is what makes derived state both lazy
and glitch-free.

```kotlin
val root = mutableStateOf(1)
val a = memo { root() * 2 }
val b = memo { root() * 3 }
val sum = memo { a() + b() }

effect { println(sum()) }   // prints 5
root.set(2)                 // prints 10, once
```

An eager, push-based library prints `7` first — `a` updated, `b` not yet — and then `10`. `7` is
not the value of the system at any point in time. Nothing here produces it, because `sum` is not
computed until both branches have settled.

## The one thing to know

**`state()` tracks. `state.value` does not.**

```kotlin
val name = mutableStateOf("world")

val greeting = memo { "hello, ${name()}" }   // tracked: recomputes when name changes
val once = name.value                        // untracked: a plain read, no dependency
```

A tracked read *mutates* the dependency graph, so it is a function call. An untracked read is
plain data access, so it is a property. Use `state()` when the surrounding code should react, and
`state.value` when it should not.

> [!WARNING]
> **This is the opposite of Compose.** In Compose, `.value` is the tracked read. Here it is the
> untracked one. A Compose habit applied here compiles cleanly and silently never updates. The
> `stateful-compose` adapter documents this at every entry point — convert at the boundary rather
> than passing a Stateful `State` into a composable.

## A tour

Sources, derivations, and effects:

```kotlin
val celsius = mutableStateOf(20.0)
val fahrenheit = memo { celsius() * 9 / 5 + 32 }

createRoot { owner ->
    effect { display.text = "${fahrenheit()}°F" }
    celsius.set(25.0)
    owner
}
```

`memo` is lazy and cached: it does not run until something reads it, and does not run again
unless a dependency it actually read has changed. An unobserved memo never runs at all. For a
computation cheap enough that a cache node costs more than the work, `derivedStateOf` skips the
node entirely — at the cost of the equality cutoff a memo gives you.

Lifetimes are explicit. Every effect belongs to an owner, and disposing the owner tears down the
whole subtree, running cleanups in reverse creation order:

```kotlin
val owner = createOwner()
runWithOwner(owner) {
    effect { view.title = title() }
    onCleanup { view.detach() }
}

owner.dispose()
```

Effects are dispatched through a `Scheduler`, which is how an adapter puts them on the thread it
needs — a render thread, a frame loop, a coroutine scope:

```kotlin
val scheduler = Scheduler.queued()
val root = createOwner(scheduler)

runWithOwner(root) { effect { render(frame()) } }
scheduler.drain()   // at the top of a frame
```

Writes that belong together should arrive together:

```kotlin
batch {
    first.set(10)
    second.set(20)
}   // an effect reading both runs once
```

Collections are tracked per element, so a consumer of one entry is not woken by an edit to
another:

```kotlin
val todos = reactiveListOf("write", "test")

todos[1] = "ship"   // wakes a consumer of index 1 and nobody else
```

For list-shaped UI, `mapKeyed` reconciles by identity rather than position, so reordering moves
results instead of rebuilding them.

## Setup

### Repository

Stateful is currently only available on my **snapshots** repository. Please keep in mind that this
may change in the future.

<details>
    <summary>Groovy (.gradle)</summary>

```gradle
maven {
    name = "Deftu Releases"
    url = "https://maven.deftu.dev/snapshots"
}
```
</details>

<details>
    <summary>Kotlin (.gradle.kts)</summary>

```kotlin
maven(url = "https://maven.deftu.dev/snapshots") {
    name = "Deftu Releases"
}
```
</details>

### Dependency

![Repository badge](https://maven.deftu.dev/api/badge/latest/snapshots/dev/deftu/stateful?color=C33F3F&name=Stateful)

<details>
    <summary>Groovy (.gradle)</summary>

```gradle
implementation "dev.deftu:stateful:<VERSION>"
```

</details>

<details>
    <summary>Kotlin (.gradle.kts)</summary>

```gradle
implementation("dev.deftu:stateful:<VERSION>")
```

</details>

### Modules

The core has no dependencies beyond `kotlinx-atomicfu`, which it uses for the graph lock. Adapters
ship separately so nobody pays for one they do not use.

| Artifact | Targets | What it adds |
| --- | --- | --- |
| `stateful` | jvm, js, wasmJs, linux/mingw/macOS, iOS, tvOS, watchOS | The library |
| `stateful-coroutines` | same as core | `asFlow`, `toState`, `StateFlow` bridging, schedulers over a `CoroutineScope` |
| `stateful-elementa` | jvm | Bidirectional bridge to Elementa State V2 |
| `stateful-compose` | jvm, js, wasmJs, iOS, macOS arm64 | `rememberStatefulRoot`, `asComposeState` |
| `stateful-svelte` | js | The Svelte store contract, for `$store` syntax |
| `stateful-react` | js | The `useSyncExternalStore` pair |

Two notes on the adapter targets. `stateful-compose` requires **Java 11** and covers fewer
platforms than core, because Compose Multiplatform's runtime does the same. `stateful-elementa`
compiles against Elementa's state artifact alone, so it does not pull a UI toolkit into your build.

Each adapter module has its own README.

## Upgrading from 0.7

0.8 replaces the evaluation model and every 0.7 consumer has to change something. One of those
changes — `get()` splitting into a tracked `state()` and an untracked `state.value` — fails
silently if you pick the wrong one, so start with [MIGRATING.md](MIGRATING.md), which covers the
whole break with a replacement for each removed API.

## Documentation

Every public declaration carries KDoc. The two worth reading before anything else are `State.invoke`
and `State.value`, which carry the tracked/untracked distinction and the Compose warning.

---

[![BisectHosting](https://www.bisecthosting.com/partners/custom-banners/8fb6621b-811a-473b-9087-c8c42b50e74c.png)](https://s.deftu.dev/bisect)

---

**This project is licensed under [LGPL-3.0][lgpl]**\
**&copy; 2024 Deftu**

[lgpl]: https://www.gnu.org/licenses/lgpl-3.0.en.html
