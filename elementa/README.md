# stateful-elementa

Bidirectional bridge to Elementa State V2.

```kotlin
implementation("dev.deftu:stateful-elementa:<VERSION>")
```

JVM only.

## What it provides

| | |
| --- | --- |
| `ElementaState<T>.asStatefulState()` | An Elementa state becomes one Stateful source |
| `State<T>.asElementaState()` | The reverse, pushed through an effect |
| `MutableState<T>.asElementaMutableState()` | Writable from either side |
| `ReferenceHolder.createStatefulOwner()` | An `Owner` the holder keeps reachable |

Every bridge needs an enclosing owner; that is what unregisters the foreign subscription. Creating
one outside a root warns rather than failing.

## Notes

**Bridge at the edge, one state wide.** Cross once and derive on this side. Bridging a derived
graph state-by-state runs two propagation schemes over the same data, and Elementa's is the eager
one — a diamond bridged node-by-node glitches again in transit.

**Both directions push.** An Elementa `State` produces its value inside Elementa's own `Observer`,
so a wrapper that read through would register on neither graph and never update.

**Do not bridge a never-equal state.** `asElementaMutableState` avoids looping because each side
compares before storing. A source built with `Equality.never()` has no such comparison and will
loop.

**No render-thread scheduler ships here.** It would have to name `Window.enqueueRenderOperation`,
which class-loads Minecraft, and nothing in this module's tests could exercise it. Supply your own:

```kotlin
val scheduler = Scheduler { task -> Window.enqueueRenderOperation(task) }
```

This one line is the only thing in this README that cannot be compiled against a test here, because
naming `Window` class-loads Minecraft. Everything else the adapter provides is covered by
`ElementaBridgeTest`, against real Elementa state objects rather than fakes.

**State V1 is not bridged.** Elementa's own deprecation messages point V1 users at V2, for the same
reasons this library moved off the push model. Move to V2, then bridge.

## Dependencies

Compiles against `elementa-unstable-statev2`, which depends only on the Kotlin stdlib.
`ReferenceHolder` lives in the main `elementa` artifact and is taken `compileOnly` — a consumer is
a mod that already has Elementa on its classpath.
