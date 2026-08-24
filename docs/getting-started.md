# Getting started

## Install

Stateful is published to my snapshots repository.

```kotlin
repositories {
    maven(url = "https://maven.deftu.dev/snapshots") {
        name = "Deftu Releases"
    }
}

dependencies {
    implementation("dev.deftu:stateful:<VERSION>")
}
```

The core targets jvm, js(IR), wasmJs, linuxX64, mingwX64, macOS, iOS, tvOS and watchOS. It pulls in
`kotlinx-atomicfu` and nothing else — that is the graph lock, and on JS and wasm it compiles to a
no-op. Adapters ship as separate artifacts so nobody pays for one they do not use; see
[Adapters](adapters.md).

The root [README](../README.md) has the Groovy spellings and the module table.

## Your first state

```kotlin
val count = mutableStateOf(0)

count.set(1)
count.update { it + 1 }

count.value   // 2
```

`mutableStateOf` is a **source**: a value you write. `set` replaces it, `update` applies a function
to the current value. A write equal to the current value does nothing at all — see
[equality](concepts.md#equality).

## Your first derivation

```kotlin
val celsius = mutableStateOf(20)
val fahrenheit = memo { celsius() * 9 / 5 + 32 }

fahrenheit.value   // 68
celsius.set(100)
fahrenheit.value   // 212
```

`memo` is a **derivation**. You never wire `fahrenheit` to `celsius`: the read of `celsius()` inside
the body registers the dependency, and it is re-registered on every recomputation, so a branch that
is not taken does not subscribe.

Nothing computed when `celsius.set(100)` ran. The write marked `fahrenheit` as needing work, and the
body ran when `.value` asked for the answer. That is what pull-based means, and it is why a memo
nothing reads never runs at all.

## Your first effect

```kotlin
val name = mutableStateOf("world")

val root = createRoot { owner ->
    effect { println("hello, ${name()}") }   // prints "hello, world"
    owner
}

name.set("there")                            // prints "hello, there"
root.dispose()                               // the effect stops
name.set("ignored")                          // prints nothing
```

An **effect** is where a value leaves the graph and touches something: a widget, a log, a socket.
It runs once immediately and again whenever a state it read has changed.

Effects are the only thing here with a lifetime, because they are the only thing that can keep
doing work after you stop caring. `createRoot` gives you one, and disposing it tears down
everything created inside — see [Lifetimes](lifetimes.md). Create an effect outside any root and it
still runs, but a warning is logged calling it a probable leak, because nothing but the returned
handle can ever stop it.

## The one rule

**`state()` tracks. `state.value` does not.**

```kotlin
val name = mutableStateOf("world")

val greeting = memo { "hello, ${name()}" }   // tracked: recomputes when name changes
val atStartup = name.value                    // untracked: a plain read, no dependency
```

A tracked read mutates the dependency graph, so it is a function call. An untracked read is plain
data access, so it is a property. Use `state()` when the surrounding code should react, and
`state.value` when it should not.

Choosing wrongly compiles cleanly and produces no error. `memo { config.value }` computes once and
then never updates again. When in doubt use `state()`: an unnecessary dependency causes extra work,
while a missing one causes a value that silently stops changing.

> [!WARNING]
> **This is the opposite of Compose.** There, `.value` is the tracked read. A Compose habit applied
> here compiles and silently never updates. Convert at the boundary with `stateful-compose` rather
> than passing a Stateful `State` into a composable — see [Adapters](adapters.md#compose).

The rule extends to anything that reads and unwraps. `getOrDefault`, `getOrElse` and `getOrThrow`
are functions, so they track. For the untracked form write `state.value ?: fallback`.

## Where to go next

- [Core concepts](concepts.md) — how evaluation actually works, and when to reach for
  `derivedStateOf` instead of `memo`.
- [Lifetimes](lifetimes.md) — the part you have to get right in a real application.
- [Testing](testing.md) — if you would rather start by writing a test.
