# stateful-compose

Bridges Stateful into Compose.

```kotlin
implementation("dev.deftu:stateful-compose:<VERSION>")
```

Requires **Java 11**, and covers jvm, js, wasmJs, iOS and macOS arm64 — Compose Multiplatform's
runtime does not publish for Intel macOS, tvOS or watchOS.

## What it provides

| | |
| --- | --- |
| `rememberStatefulRoot()` | An `Owner` tied to the composition via `DisposableEffect` |
| `State<T>.asComposeState(owner)` | Reads recompose |
| `MutableState<T>.asComposeMutableState(owner)` | Writes reach the Stateful source too |

## The inversion

**`State.value` means the opposite thing in each library.** In Compose, reading `.value` is how you
subscribe. In Stateful, reading `.value` is how you *avoid* subscribing — the tracked read is
`state()`.

There is no compile error and no exception:

```kotlin
@Composable
fun Broken(title: State<String>) {
    Text(title.value)      // reads once, never recomposes
}

@Composable
fun Fixed(title: State<String>) {
    val root = rememberStatefulRoot()
    val text by title.asComposeState(root)
    Text(text)             // recomposes
}
```

Convert at the boundary, every time. Never pass a Stateful `State` into a composable and read
`.value` from it.

## Notes

**Compose-to-Stateful is not provided.** Compose exposes its changes through `snapshotFlow`, which
needs a coroutine scope, and taking that dependency here would push it onto every consumer. The
recipe is five lines and belongs where the scope already is:

```kotlin
val bridged = remember { mutableStateOf(composeState.value) }
LaunchedEffect(Unit) {
    snapshotFlow { composeState.value }.collect { bridged.set(it) }
}
```

For a control that owns its own edits, `asComposeMutableState` already carries writes back.

**The snapshot systems are not unified,** deliberately. Compose solves thread safety with MVCC
snapshots; this library uses a lock. Bridging at the edge keeps both honest.
