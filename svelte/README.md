# stateful-svelte

Implements the Svelte store contract, so a Stateful state works with `$store` syntax.

```kotlin
implementation("dev.deftu:stateful-svelte:<VERSION>")
```

JS only. **No dependency on Svelte** — the store contract is duck-typed, so producing the right
object shape is the whole job.

## What it provides

| | |
| --- | --- |
| `State<T>.asSvelteStore()` | A `Readable<T>`: `subscribe(fn)` returning an unsubscribe function |
| `MutableState<T>.asSvelteStore()` | A `Writable<T>`: the above plus `set` and `update` |

```kotlin
@JsExport
val count = mutableStateOf(0)

@JsExport
val countStore = count.asSvelteStore()
```

```svelte
<script>
  import { countStore } from "your-kotlin-module";
</script>

<button on:click={() => countStore.update(n => n + 1)}>
  {$countStore}
</button>
```

Works in Svelte 3, 4 and 5 alike.

## Notes

**Runes are not the target, and cannot be.** `$state` is compiler magic applied to a declaration,
so a rune cannot wrap state that lives outside the component. The store contract is the supported
route for external state, and it is stable across every Svelte version that has runes and every
version that does not.

**The immediate call is synthesised.** The contract requires the subscriber to be called at once
with the current value. `State.subscribe` fires on change only, so this adapter calls it from
`value` before subscribing. Written by hand without that line, a component renders blank until the
first change — which looks like the state being broken.

**The unsubscribe function is the whole lifetime.** No owner is involved, matching Svelte: the
component's teardown calls it.
