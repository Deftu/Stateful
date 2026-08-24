# Operators

Everything on this page builds a `memo`, so everything on this page is lazy, cached, and cuts
propagation off when its result is unchanged. That cutoff is usually the reason to reach for an
operator rather than writing the expression inline — see
[memo against derivedStateOf](concepts.md#memo-against-derivedstateof).

## Combining

```kotlin
val first = mutableStateOf(1)
val second = mutableStateOf(10)

val doubled  = first.map { it * 2 }
val paired   = first.zip(second)                       // State<Pair<Int, Int>>
val summed   = first.combine(second) { a, b -> a + b }
```

`combine` runs its transform once per change of either input, never with one input updated and the
other stale — that is the diamond guarantee, applied to the shape people usually reach for.

`flatMap` follows the state that its mapper returns:

```kotlin
val a = mutableStateOf("from a")
val b = mutableStateOf("from b")
val which = mutableStateOf(a)

val current = which.flatMap { it }

current.value     // "from a"
a.set("edited")
current.value     // "edited"
which.set(b)
current.value     // "from b"
a.set("ignored")
current.value     // still "from b" — a is no longer a dependency
```

The inner state is transient: each time the outer state changes, the previous inner state stops
being a dependency and the new one starts. This is the replacement for 0.7's `rebind` — a state
holding the source does the same job and composes.

Each operator has a factory form, for when the receiver reads badly:

```kotlin
mappedStateOf(source) { it * 2 }
flatMappedStateOf(source) { it.inner }
zippedStateOf(first, second)
zippedStateOf(first, second, third)
combineStateOf(first, second) { a, b -> a + b }
combineStateOf(first, second, third) { a, b, c -> a + b + c }
```

## Reading nullable state

```kotlin
val name = mutableStateOf<String?>(null)

name.getOrDefault("anonymous")                 // "anonymous"
name.getOrElse { "anonymous" }                 // "anonymous"
name.getOrThrow()                              // throws IllegalStateException
name.getOrThrow("name was never set")          // throws IllegalStateException, with that message
name.getOrThrow(IllegalArgumentException())    // throws that
name.getOrThrow { IllegalArgumentException() } // builds it lazily, and throws that
```

**These are functions, so they track.** They follow the same rule as `state()`: reading and
unwrapping is still reading. `memo { config.getOrDefault(fallback) }` updates. For the untracked
form write `config.value ?: fallback`.

## Per-type extensions

These live in `dev.deftu.stateful.ext`. Each one exists for the equality cutoff it buys, not for
brevity.

### Booleans

```kotlin
val enabled = mutableStateOf(false)
val visible = mutableStateOf(true)

enabled.toggle()                 // flips it; the read is untracked

val hidden  = enabled.inverted()  // or !enabled
val both    = enabled and visible
val either  = enabled or visible
val one     = enabled xor visible
val notBoth = enabled nand visible
val neither = enabled nor visible
```

`and`, `or`, `xor`, `nand` and `nor` each have an overload taking a plain `Boolean` on either side,
so `enabled and true` and `true and enabled` both work.

### Strings

```kotlin
val query = mutableStateOf("")

val length     = query.length()
val empty      = query.isEmpty()
val notEmpty   = query.isNotEmpty()
val blank      = query.isBlank()
val submittable = query.isNotBlank()
val trimmed    = query.trim()
val lower      = query.lowercase()
val upper      = query.uppercase()
val hasPrefix  = query.startsWith("cmd:")
val hasSuffix  = query.endsWith("!")
val matches    = query.contains("error", ignoreCase = true)
```

The cutoff is the point. A submit button bound to `query.isNotBlank()` redraws when the answer
flips, not on every keystroke:

```kotlin
val query = mutableStateOf("")
val submittable = query.isNotBlank()

var redraws = 0
val owner = createRoot { owner ->
    effect { submittable(); redraws++ }
    owner
}
redraws = 0

query.set("h")      // redraws == 1: false to true
query.set("he")     // redraws is still 1
query.set("hel")    // still 1
query.set("")       // redraws == 2: true to false
```

`contains`, `startsWith` and `endsWith` also take a `State<String>` on the right, in which case the
result recomputes when either side changes.

### Pairs and triples

```kotlin
val point = mutableStateOf(1 to 2)

val x = point.first()    // changing y does not wake x's dependents
val y = point.second()

val (a, b) = point       // destructuring, via component1/component2
```

`State<Triple<A, B, C>>` gets `first()`, `second()`, `third()` and three-way destructuring on the
same terms. Splitting a tuple state into components is not cosmetic: each component is a memo, so a
consumer of one is not woken by a change confined to another.

### Colours (JVM only)

`java.awt.Color` has no common equivalent, so these live in `jvmMain`:

```kotlin
val colour = mutableStateOf(Color.RED)

colour.rgb()      // the packed ARGB int: a different instance holding the same colour changes nothing
colour.red()      // one channel; a change confined to another does not reach dependents
colour.lighter()  // Color.brighter, saturating at white
colour.darker()
colour.setRgb(0x80FF0000.toInt())   // writes a packed value, keeping its alpha
```

## Delegates

```kotlin
val source = mutableStateOf(1)

val untrackedRead: Int by stateBound(source)
var readWrite: Int by mutableStateBound(source)      // assignment writes through
val trackedRead: Int by trackedStateBound(source)
```

`stateBound` and `mutableStateBound` read **untracked**. A property access looks like plain data
access at the call site, so silently creating a dependency there would be surprising.

`trackedStateBound` reads tracked, and the declaration is the only place a reader can see that:

```kotlin
class Header(state: State<String>) {
    val title: String by trackedStateBound(state)
}

val header = Header(source)
val rendered = memo { header.title }   // subscribes, and the read site does not say so
```

That is the hazard as well as the point. Prefer it where the whole class is reactive and `state()`
at every use would obscure more than it reveals; avoid it on a type that mixes reactive and plain
properties.

## `snapshot`

`snapshot(state)` reads untracked and returns a plain, non-reactive copy, for handing a value to
something that must not hold a live reference into the graph: serialisation, logging, an undo stack,
a payload crossing a thread boundary.

```kotlin
val backing = mutableListOf("a", "b")
val source = mutableStateOf<List<String>>(backing)

val taken = snapshot(source)
backing.add("c")

// taken == ["a", "b"] — a copy, not the live list
```

It removes **reactivity, not mutability**. Nested states are unwrapped to their values and
collections are copied into read-only ones. Anything else — your own data classes, arrays, platform
types — comes back as it is, because common Kotlin has no reflection and there is no way to clone an
arbitrary object. A value made of data classes, primitives and collections comes back fully
detached; one holding a mutable object of your own comes back sharing it.

One typing caveat: a reactive or mutable collection comes back as the plain read-only equivalent, so
declare such states as `List<E>`, `Set<E>` or `Map<K, V>` rather than as `ArrayList<E>` or a reactive
collection type. Casting the result to a concrete type will fail.
