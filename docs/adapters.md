# Adapters

Adapters ship as separate artifacts so nobody pays for one they do not use. Each has its own README
with the full surface and its own test suite; this page is the map, and the parts that are easy to
get wrong.

| Artifact | Targets | What it adds |
| --- | --- | --- |
| `stateful-coroutines` | same as core | `asFlow`, `toState`, `StateFlow` bridging, schedulers over a `CoroutineScope` |
| `stateful-elementa` | jvm | Bidirectional bridge to Elementa State V2 |
| `stateful-compose` | jvm, js, wasmJs, iOS, macOS arm64 | `rememberStatefulRoot`, `asComposeState` |
| `stateful-svelte` | js | The Svelte store contract, for `$store` syntax |
| `stateful-react` | js | The `useSyncExternalStore` pair |

Two notes on targets. `stateful-compose` requires **Java 11** and covers fewer platforms than core,
because Compose Multiplatform's runtime does. `stateful-elementa` compiles against Elementa's state
artifact alone, so it does not pull a UI toolkit into your build.

> The samples on this page need their framework on the classpath, so they are not compiled by the
> core sample test. The Kotlin halves are backed by each adapter module's own tests —
> `SvelteTypeScriptSamplesTest` and `ReactTypeScriptSamplesTest` cover the exported declarations
> below. The TypeScript halves were typechecked against the `.d.ts` the build actually generates,
> but nothing re-checks them on every build; treat a change to an exported signature as a change to
> them.

## One rule for all of them

**Convert at the boundary, one state wide.** Cross once and derive on this side.

Bridging a derived graph state-by-state runs two independent propagation schemes over the same data,
and if the other one is eager — Elementa's is, and it is the model this library exists to replace —
a diamond bridged node-by-node glitches again in transit. You lose the guarantee you came for.

## Coroutines

[`coroutines/README.md`](../coroutines/README.md)

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

`statefulOwner()` is the piece worth reaching for first. A coroutine scope usually already models
the right lifetime — a request, a connection, a screen — so wiring a second teardown hook beside it
is redundant:

```kotlin
val owner = scope.statefulOwner()   // disposed when the scope is cancelled
```

It defaults to dispatching effects into the scope, so effects land on the scope's dispatcher rather
than on whichever thread performed the write. On a server, where writes arrive on request threads,
that is the whole point.

**Asynchronous work goes in an effect, not a memo.** Memo bodies run under the graph lock and cannot
suspend; effect bodies run with it released. Read in the effect, launch, write the result back:

```kotlin
effect {
    val id = selectedId()
    scope.launch { detail.set(repository.fetch(id)) }
}
```

**`asFlow` does not conflate.** Every change is delivered. A caller who wants latest-value-wins
composes `.conflate()`, which is how kotlinx flows are meant to be assembled; a caller who needs
every intermediate value could not recover one that had already been dropped. The internal buffer is
unbounded, because an effect body is not a suspending context and so cannot apply backpressure — add
`.conflate()` or `.buffer(n)` when the producer can outrun the consumer indefinitely.

**Ordering is the dispatcher's, not the graph's.** Two effects on a multi-threaded dispatcher may run
in either order. Effects that must be ordered belong in one effect.

## Compose

[`compose/README.md`](../compose/README.md)

| | |
| --- | --- |
| `rememberStatefulRoot()` | An `Owner` tied to the composition via `DisposableEffect` |
| `State<T>.asComposeState(owner)` | Reads recompose |
| `MutableState<T>.asComposeMutableState(owner)` | Writes reach the Stateful source too |

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

**Compose-to-Stateful is not provided.** Compose exposes its changes through `snapshotFlow`, which
needs a coroutine scope to collect in, and taking a coroutines dependency to ship one function would
push it onto every consumer of this adapter. The recipe is short and belongs where the scope already
is:

```kotlin
val bridged = remember { mutableStateOf(composeState.value) }
LaunchedEffect(Unit) {
    snapshotFlow { composeState.value }.collect { bridged.set(it) }
}
```

That direction is asynchronous whichever way it is written: the Stateful state catches up when the
Compose snapshot is applied, not at the instant of the write. For a control that owns its own edits,
`asComposeMutableState` carries writes back without any of this.

**Do not bridge a never-equal state bidirectionally.** The two directions avoid looping because each
side compares before storing. `Equality.never()` answers `false` to that comparison by definition,
so the echo of a write is not a no-op and the bridge oscillates.

## Elementa

[`elementa/README.md`](../elementa/README.md)

| | |
| --- | --- |
| `ElementaState<T>.asStatefulState()` | An Elementa state becomes one Stateful source |
| `State<T>.asElementaState()` | The reverse, pushed through an effect |
| `MutableState<T>.asElementaMutableState()` | Writable from either side |
| `ReferenceHolder.createStatefulOwner()` | An `Owner` the holder keeps reachable |

Every bridge needs an enclosing owner; that is what unregisters the foreign subscription. Creating
one outside a root warns rather than failing.

The two libraries model lifetime in opposite ways. Elementa holds effects weakly and uses a
`ReferenceHolder` to keep them alive; this library holds them strongly and uses an `Owner` to end
them. `createStatefulOwner` is where they meet: the holder keeps the owner reachable for as long as
the component lives, and you dispose the owner when the component is removed. **Holding is not
disposing** — a held owner that is never disposed still leaks.

Both directions push. An Elementa `State` produces its value inside Elementa's own `Observer`, so a
wrapper that read through would register on neither graph and never update.

No render-thread scheduler ships in the module, because naming `Window.enqueueRenderOperation`
class-loads Minecraft and nothing in that module's tests could exercise it. Supply your own:

```kotlin
val scheduler = Scheduler { task -> Window.enqueueRenderOperation(task) }
```

State V1 is not bridged. Move to V2, then bridge.

## Kotlin/JS and TypeScript

Both JS targets generate TypeScript definitions, so a JavaScript or TypeScript front end can consume
a Stateful state — but only through a door you open yourself. Four facts decide the shape of every
sample below.

**The core library exports nothing to TypeScript.** Nothing in `stateful` is annotated `@JsExport`,
so its emitted `stateful.d.ts` declares no types at all. `State`, `mutableStateOf`, `memo` and the
rest never cross the boundary, and they are not meant to — the reactive graph stays on the Kotlin
side.

**The adapters export one type each,** and nothing else. `stateful-svelte.d.ts` carries `Readable`
and `Writable`; `stateful-react.d.ts` carries `ExternalStore`. The functions that *build* a store —
`asSvelteStore`, `asExternalStore`, `statefulRoot` — are not exported, so TypeScript cannot call
them.

**So you export the store.** Build it in Kotlin, annotate it `@JsExport`, and give it an explicit
type, because every type in an exported signature must itself be exportable — an inferred
`MutableState<Int>` is not:

```kotlin
private val count = mutableStateOf(0)

@JsExport
val countStore: Writable<Int> = count.asSvelteStore()

@JsExport
fun increment() {
    count.update { it + 1 }
}
```

`@JsExport` is still opt-in, so the compiler asks for `@OptIn(ExperimentalJsExport::class)` or the
matching compiler flag. Export a function for every write the front end needs to make: the store's
own `set` and `update` cover a `Writable`, and a React `ExternalStore` is read-only by design.

**Consume the exported interfaces, never implement them.** Kotlin/JS stamps each one with a
`__doNotUseOrImplementIt` marker, so an object literal of the right shape is rejected:

```ts
// error TS2741: Property '__doNotUseOrImplementIt' is missing
const handWritten: ExternalStore<number> = {
  subscribe: () => () => {},
  getSnapshot: () => 1,
};
```

Import the type from the adapter's declarations. The emitted namespace is dotted, which is why the
import looks the way it does:

```ts
import type { dev } from "stateful-react";

type ExternalStore<T> = dev.deftu.stateful.react.ExternalStore<T>;
```

> [!WARNING]
> **Never pass an exported method unbound.** Kotlin/JS emits `subscribe` and `getSnapshot` as
> prototype methods that read `this`, so `store.getSnapshot` detached from its receiver throws.
> Kotlin's `store::getSnapshot` binds; TypeScript's `store.getSnapshot` does not. Wrap in an arrow,
> as every sample below does.

## Svelte

[`svelte/README.md`](../svelte/README.md)

| | |
| --- | --- |
| `State<T>.asSvelteStore()` | A `Readable<T>`: `subscribe(fn)` returning an unsubscribe function |
| `MutableState<T>.asSvelteStore()` | A `Writable<T>`: the above plus `set` and `update` |

No dependency on Svelte. The store contract is duck-typed, so producing the right object shape is
the whole job, and it works in Svelte 3, 4 and 5 alike.

Kotlin side — the store crosses, the `MutableState` behind it does not:

```kotlin
private val count = mutableStateOf(0)

@JsExport
val countStore: Writable<Int> = count.asSvelteStore()
```

TypeScript side, in a `.svelte` component. `$countStore` is Svelte's auto-subscription, and it
infers `number` from the emitted declarations:

```svelte
<script lang="ts">
  import { countStore } from "your-kotlin-module";
</script>

<button on:click={() => countStore.update((n) => n + 1)}>
  {$countStore}
</button>
```

Svelte 5 spells that event attribute `onclick`; the store contract itself is identical across 3, 4
and 5.

To hold the store in a `.ts` file rather than a component, either import the emitted type or
annotate it with Svelte's own — the Kotlin `Writable<T>` satisfies `svelte/store`'s `Writable<T>`
structurally, `this: void` annotations included:

```ts
import type { Writable } from "svelte/store";
import { countStore } from "your-kotlin-module";

const store: Writable<number> = countStore;

const stop = store.subscribe((n) => console.log(n));
store.update((n) => n + 1);
stop();
```

**Runes are not the target, and cannot be.** `$state` is compiler magic applied to a declaration, so
a rune cannot wrap state that lives outside the component. The store contract is the supported route
for external state.

**The immediate call is synthesised.** The contract requires the subscriber to be called at once
with the current value; `State.subscribe` fires on change only, so the adapter calls it from `value`
before subscribing. Written by hand without that line, a component renders blank until the first
change.

The unsubscribe function is the whole lifetime. No owner is involved, matching Svelte: the
component's teardown calls it.

## React

[`react/README.md`](../react/README.md)

| | |
| --- | --- |
| `State<T>.asExternalStore()` | An `ExternalStore<T>` with `subscribe` and `getSnapshot` |
| `statefulRoot()` | An `Owner` for React to dispose on unmount |

No dependency on React — a Kotlin/JS library that hard-depends on React cannot be used from anything
that is not React, and the hook binding is two lines in your own code.

From Kotlin, with the React wrappers:

```kotlin
val store = useMemo({ someState.asExternalStore() }, emptyArray())
val value = useSyncExternalStore(store::subscribe, store::getSnapshot)
```

```kotlin
val root = useMemo({ statefulRoot() }, emptyArray())
useEffect({ { root.dispose() } }, emptyArray())
```

`subscribe` is a core primitive of this library rather than a legacy shim precisely because this hook
wants exactly a subscription plus an untracked snapshot.

### From TypeScript

Export the store and the writes from Kotlin:

```kotlin
private val counter = mutableStateOf(0)

@JsExport
val counterStore: ExternalStore<Int> = memo { counter() }.asExternalStore()

@JsExport
fun increment() {
    counter.update { it + 1 }
}
```

Then in a `.tsx` component:

```tsx
import { useSyncExternalStore } from "react";
import { counterStore, increment } from "your-kotlin-module";

const subscribe = (onStoreChange: () => void) => counterStore.subscribe(onStoreChange);
const getSnapshot = () => counterStore.getSnapshot();

export function Counter() {
  const count = useSyncExternalStore(subscribe, getSnapshot);

  return <button onClick={() => increment()}>{count}</button>;
}
```

Two things about those two wrapper lines, both of which are why they sit at module scope rather than
inside the component.

**They bind the receiver.** `counterStore.getSnapshot` on its own is a detached prototype method and
throws when React calls it:

```tsx
// Wrong twice over: unbound methods, and a new identity every render.
const count = useSyncExternalStore(counterStore.subscribe, counterStore.getSnapshot);
```

**They keep a stable identity.** React re-subscribes whenever the `subscribe` function it is handed
changes identity, so arrows created inside the component body tear the subscription down and rebuild
it on every render. Module scope, or `useCallback` with an empty dependency array, fixes both.

If you want the store's type in your own signatures, take it from the emitted declarations:

```ts
import type { dev } from "stateful-react";

type ExternalStore<T> = dev.deftu.stateful.react.ExternalStore<T>;

export function readTwice(store: ExternalStore<number>): [number, number] {
  return [store.getSnapshot(), store.getSnapshot()];
}
```

**`getSnapshot` must be referentially stable between notifications.** React compares successive calls
with `Object.is` and re-renders until two agree. A snapshot that allocates on every call never
converges and the component renders forever. The adapter returns the stored value directly, so
stability is your state's own — derive with `memo` and the same instance comes back until the value
genuinely changes:

```kotlin
val rows = memo { items().map(::toRow) }   // stable between changes
```

**Never feed a never-equal state to React.** `Equality.never()` propagates on every write by
definition, which downstream of `useSyncExternalStore` is an infinite render loop.

**Build the store once and hold it.** A new store on every render hands React a new `subscribe`
identity, so it tears down and re-establishes the subscription every render.

Concurrent mode is safe with the `useMemo`/`useEffect` pairing above: a render React discards never
runs its effect, and the discarded owner holds nothing.

## Hosts with no adapter

Most hosts need no adapter at all — a `Scheduler` lambda and an `Owner` on the host object is the
whole binding. See [Lifetimes](lifetimes.md#createowner-and-runwithowner) for the shape, and
[Scheduling](scheduling.md#writing-your-own) for the scheduler.
