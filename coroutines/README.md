# stateful-coroutines

Bridges Stateful and `kotlinx.coroutines`, in both directions, plus schedulers and owners built on
a `CoroutineScope`.

```kotlin
implementation("dev.deftu:stateful-coroutines:<VERSION>")
```

Targets match core.

## What it provides

| | |
| --- | --- |
| `State<T>.asFlow()` | Emits the current value, then every change |
| `State<T>.asStateFlow(scope)` | The same as a `StateFlow`, started eagerly |
| `Flow<T>.toState(scope, initial)` | Collects into a state, in `scope` |
| `StateFlow<T>.asState(scope)` | The same, seeded with the flow's current value |
| `CoroutineScope.asScheduler()` | Dispatches effect bodies into the scope |
| `CoroutineDispatcher.asScheduler(scope)` | The same, on a specific dispatcher |
| `CoroutineScope.statefulOwner()` | An `Owner` that dies with the scope |
| `Owner.disposeWith(job)` | The manual form, for an owner that already exists |

## Notes

**`asFlow` does not conflate.** Every change is delivered. A caller who wants latest-value-wins
composes `.conflate()`; a caller who needs every intermediate value could not recover one that had
already been dropped. The internal buffer is unbounded, because an effect body is not a suspending
context and so cannot apply backpressure — add `.conflate()` or `.buffer(n)` when the producer can
outrun the consumer indefinitely.

**A memo cannot suspend.** Memo bodies run under the runtime lock and must be pure, cheap, and take
no other lock. Asynchronous work belongs in an effect that writes a source when it finishes:

```kotlin
effect {
    val id = selectedId()
    scope.launch { detail.set(repository.fetch(id)) }
}
```

**Ordering is the dispatcher's, not the graph's.** Two effects scheduled in sequence are not
guaranteed to run in that order unless the dispatcher is single-threaded. Effects that must be
ordered relative to each other belong in one effect.
