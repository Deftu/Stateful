# Lifetimes

Nothing here is torn down by garbage collection. The graph holds its nodes strongly, so an effect,
an owner or a subscription runs until something disposes it. Dropping a handle is a leak, not a
hint.

Elementa solves this with weak references. That does not port — common Kotlin has none — so
lifetimes are explicit instead.

## What needs one

**Derived state does not.** A `memo` holds nothing until something reads it, and an unobserved memo
never runs. A class exposing nothing but memos leaks nothing and needs no teardown.

**Effects do.** An effect is the only thing that keeps doing work after you stop caring about it.
Every effect belongs to an `Owner`.

**Subscriptions do not need an owner,** but they do need disposing. The `Subscription` returned by
`subscribe` is the whole lifetime.

## `createRoot`

`createRoot` creates a lifetime, enters it, and hands it to your block so you can dispose it later:

```kotlin
val owner = createRoot { owner ->
    effect { view.title = title() }
    onCleanup { view.detach() }
    owner
}

owner.dispose()
```

The block returns whatever you want; returning the owner is the usual shape. One root per screen or
component, disposed on unmount, is what this is for.

Disposing tears down the subtree **depth-first**: children in reverse creation order, then this
owner's own cleanups in reverse creation order, then its nodes.

```kotlin
val order = mutableListOf<String>()

val owner = createRoot { owner ->
    onCleanup { order.add("first") }
    onCleanup { order.add("second") }
    onCleanup { order.add("third") }
    owner
}

owner.dispose()

// order == ["third", "second", "first"]
```

Reverse order is not arbitrary. A cleanup usually undoes something a later one depends on, so
unwinding in creation order would tear down a dependency before its dependent. If one cleanup
throws, the rest still run and the first failure is rethrown with the others attached.

Disposing twice is harmless. Cleanups run once.

## `createOwner` and `runWithOwner`

`createRoot` needs a block, and plenty of hosts do not give you one. Ktor, Spring, an Android
`Activity`, a Minecraft mod and a game loop all start something in one stack frame and stop it in
another. Build the owner separately and enter it from later callbacks:

```kotlin
class Screen {
    private val root = createOwner()

    fun mount() = runWithOwner(root) {
        effect { view.title = title() }
        onCleanup { view.detach() }
    }

    fun unmount() = root.dispose()
}
```

The current owner is thread-local and does not survive between callbacks, which is exactly why
`runWithOwner` exists.

**`runWithOwner` borrows a lifetime, it does not create one.** Computations created inside live
until the owner is disposed, not until the block returns. That is the point, and it is also the way
to misuse it: a per-request callback that creates an effect on an application-lifetime root
accumulates effects forever. Give a request its own short-lived root.

```kotlin
fun handle() {
    val root = createOwner()
    try {
        runWithOwner(root) { effect { observed.add(source()) } }
    } finally {
        root.dispose()
    }
}
```

`currentOwner()` returns the owner a computation created on this thread would attach to, or `null`.
Adapters use it to warn at construction rather than leak silently; application code rarely needs it.

## Cleanups

`onCleanup` registers a block to run when the enclosing owner is disposed — and, inside an effect,
**before each re-run of that effect**:

```kotlin
val source = mutableStateOf(0)
val order = mutableListOf<String>()

val owner = createRoot { owner ->
    effect {
        val value = source()
        order.add("run $value")
        onCleanup { order.add("cleanup $value") }
    }
    owner
}

source.set(1)
source.set(2)
owner.dispose()

// order == ["run 0", "cleanup 0", "run 1", "cleanup 1", "run 2", "cleanup 2"]
```

That symmetry is what makes an effect a safe place to acquire something. Open a subscription, add a
listener, allocate a handle — register the undo next to it and the effect stays balanced across
every re-run and across teardown.

Cleanups run with the graph lock released, so they may block or take other locks. They run on the
calling thread rather than through a `Scheduler`, so a cleanup touching something thread-affine — a
graphics handle, a UI widget — must be disposed from a thread allowed to touch it.

Called outside any owner, `onCleanup` warns and does nothing. There is no lifetime to attach to, so
it could never fire.

## Nesting

An effect created inside an effect belongs to the outer one, and is torn down before the outer
re-runs:

```kotlin
val outerSource = mutableStateOf(0)
val innerSource = mutableStateOf(0)
val observed = mutableListOf<String>()

val owner = createRoot { owner ->
    effect {
        val outer = outerSource()
        effect { observed.add("inner $outer:${innerSource()}") }
    }
    owner
}

innerSource.set(1)     // "inner 0:1"
outerSource.set(1)     // the outer re-runs: the old inner dies, a new one starts — "inner 1:1"
observed.clear()
innerSource.set(2)     // "inner 1:2" — only the new one is alive
owner.dispose()

// observed == ["inner 1:2"]
```

This is the same mechanism as `onCleanup`: each run of an effect gets a fresh scope, and the
previous one is reset before the next run starts.

**A root created inside another root is detached.** It is not a child, and the outer root will not
dispose it:

```kotlin
lateinit var inner: Disposable
val outer = createRoot { outer ->
    inner = createRoot { nested ->
        effect { observed.add(source()) }
        nested
    }
    outer
}

outer.dispose()
source.set(1)      // the inner effect still runs
inner.dispose()
source.set(2)      // now it does not
```

Roots are independent lifetimes by definition. Nest effects, not roots, when you want automatic
teardown.

## The orphan warning

An `effect` created outside any root still runs. It attaches to a process-wide root and logs:

```
[stateful] effect created outside of createRoot; it will run until disposed by hand, which is probably a leak
```

Nothing but the returned `Disposable` can ever stop it. This warns rather than throwing because the
usual cause is an effect written one line too early, and making that fatal would be worse than the
mistake.

Warnings go through `StateWarnings.handler`, which you should redirect at startup — `println`
reaches a terminal nobody is reading in most hosts:

```kotlin
StateWarnings.handler = { message -> logger.warn(message) }
```

It is process-wide configuration and is not synchronised. Set it before any state is created.

## Asserting you have not leaked

Failing to dispose an owner is this library's worst failure mode: silent, and it compounds. `Owner`
exposes what it is holding so you can assert against it:

```kotlin
val owner = createOwner()

owner.isEmpty            // true

runWithOwner(owner) {
    effect { source() }
    effect { source() }
    onCleanup { }
}

owner.computationCount   // 2
owner.childCount         // 2 — each effect gets a child owner for its own scope
owner.cleanupCount       // 1
owner.isEmpty            // false

owner.dispose()

owner.isEmpty            // true again — a disposed owner holds nothing
```

`childCount` counting two for two effects is not a surprise to explain away: every effect gets its
own child owner, which is what gives `onCleanup` inside an effect somewhere to attach.

See [Testing](testing.md) for how to put this in a teardown.

## The rule the types cannot express

**An effect may read state that outlives it, never state that dies first.**

Reading upward in lifetime is fine and normal — a per-row effect reading a screen-level state, a
per-request effect reading application configuration. Reading downward is a use-after-free with
extra steps: the state's owner disposes, the state stops updating, and the effect keeps running
against a value frozen at some arbitrary point.

Nothing enforces this. It is checked in review.
