# stateful-react

Produces the pair `useSyncExternalStore` consumes.

```kotlin
implementation("dev.deftu:stateful-react:<VERSION>")
```

JS only. **No dependency on React** — a Kotlin/JS library that hard-depends on React cannot be used
from anything that is not React, and the hook binding is two lines in your own code.

## What it provides

| | |
| --- | --- |
| `State<T>.asExternalStore()` | An `ExternalStore<T>` with `subscribe` and `getSnapshot` |
| `statefulRoot()` | An `Owner` for React to dispose on unmount |

```kotlin
val store = useMemo({ someState.asExternalStore() }, emptyArray())
val value = useSyncExternalStore(store::subscribe, store::getSnapshot)
```

```kotlin
val root = useMemo({ statefulRoot() }, emptyArray())
useEffect({ { root.dispose() } }, emptyArray())
```

`subscribe` is a core primitive of this library rather than a legacy shim precisely because this
hook wants exactly a subscription plus an untracked snapshot.

## Notes

**`getSnapshot` must be referentially stable between notifications.** React compares successive
calls with `Object.is` and re-renders until two agree. A snapshot that allocates on every call
never converges, and the component renders forever.

This adapter returns the stored value directly and adds nothing, so stability is your state's own.
Derive with `memo` and the same instance comes back until the value genuinely changes:

```kotlin
val rows = memo { items().map(::toRow) }   // stable between changes
```

**Never feed a never-equal state to React.** `Equality.never()` propagates on every write by
definition, which downstream of `useSyncExternalStore` is an infinite render loop.

**Build the store once and hold it.** A new store on every render hands React a new `subscribe`
identity each time, so it tears down and re-establishes the subscription on every render.

**Concurrent mode** is safe with the `useMemo`/`useEffect` pairing above: a render React discards
never runs its effect, and the discarded owner holds nothing.
