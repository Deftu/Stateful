# Reactive collections

A `State<List<T>>` is one signal. Any edit wakes every reader. Reactive collections are tracked per
element instead, so a consumer of one entry is not woken by an edit to another.

```kotlin
val todos = reactiveListOf("write", "test")
val map = reactiveMapOf("a" to 1)
val selected = reactiveSetOf<Int>()
```

All three implement the ordinary Kotlin mutable interfaces, so everything you already know works.
Only the tracking is different.

## `ReactiveList`

Two independent kinds of signal back it.

- Every position has a **slot** carrying the value at that index, created on the first tracked read
  of it. A slot nothing has read costs nothing.
- One **structure** signal covers size and ordering.

Replacing an element touches its slot alone:

```kotlin
val list = reactiveListOf("a", "b", "c")
val first = mutableListOf<String>()
val second = mutableListOf<String>()

val owner = createRoot { owner ->
    effect { first.add(list[0]) }
    effect { second.add(list[1]) }
    owner
}

list[1] = "B"

// first  == ["a"]        — never woken
// second == ["b", "B"]
```

Reading `size`, iterating, or anything else that depends on the shape of the list registers on the
structure signal. So replacing an element does not wake a size consumer, and appending does:

```kotlin
val list = reactiveListOf("a")
val sizes = mutableListOf<Int>()

val owner = createRoot { owner ->
    effect { sizes.add(list.size) }
    owner
}

list[0] = "A"      // sizes == [1]
list.add("b")      // sizes == [1, 2]
```

### The coarse edge

**An indexed read registers on that slot *and* on the structure signal.** It has to: an index that
is valid now may not be after a removal, and a slot alone cannot tell a consumer that its position
stopped existing.

The consequence is that a structural edit wakes *every* positional reader, not only the ones whose
value changed:

```kotlin
val list = reactiveListOf("a", "b")
val first = mutableListOf<String>()

val owner = createRoot { owner ->
    effect { first.add(list[0]) }
    owner
}

list.add("c")      // first == ["a", "a"] — woken, index 0 did not move

list.add(0, "z")   // first == ["a", "a", "z"] — values shift through the slots
```

Slots are positional: `list[i]` means whatever is at `i` now, and after a shift the slot at an index
carries whatever now lives there. For list-shaped UI, where waking every row on every insert is a
real cost, use [`mapKeyed`](#mapkeyed).

Whole-list reads track everything, which is usually what you want:

```kotlin
val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
val labels = memo { list.joinToString { it.label } }

labels.value        // "one, two"
list[0] = Row(1, "ONE")
labels.value        // "ONE, two"
```

### Untracked reads

Each tracked read has an untracked counterpart, for the same reason `state.value` exists:

```kotlin
list.getUntracked(0)
list.untrackedSize
map.getUntracked("key")
map.untrackedSize
set.containsUntracked("element")
set.untrackedSize
```

### `changes` and `ListChange`

Every edit publishes a batch of changes, in apply order:

```kotlin
val list = reactiveListOf("a", "b")
val seen = mutableListOf<ListChange<String>>()

val owner = createRoot { owner ->
    effect { seen.addAll(list.changes()) }
    owner
}
seen.clear()

list[1] = "B"

// seen == [ListChange.Set(index = 1, previous = "b", current = "B")]
```

`changes` is a `State`, so `list.changes()` is a tracked read and wakes its reader on every edit.

| Change | Emitted by |
| --- | --- |
| `Insert(index, elements)` | `add`, `addAll` — one change for a range, not one per element |
| `Remove(index, elements)` | `removeAt` |
| `Set(index, previous, current)` | `list[i] = x`. `previous` is carried so a consumer can diff without keeping its own copy |
| `Move(from, to, element)` | `move` |
| `Clear(previous)` | `clear` |

`move` emits a `Move` rather than a removal and an insertion precisely so a keyed consumer can reuse
the element's derived state instead of tearing it down and rebuilding it:

```kotlin
val list = reactiveListOf("a", "b", "c")
list.move(0, 2)
list.toList()   // ["b", "c", "a"]
```

`replaceAll(elements)` swaps the whole contents in one batch, emitting a `Clear` followed by an
`Insert`.

## `ReactiveMap`

Every key has a slot, created on first tracked read. Reading a key that is **absent** still registers
on its slot, so a later insert wakes the reader:

```kotlin
val map = reactiveMapOf<String, Int>()
val observed = mutableListOf<Int?>()

val owner = createRoot { owner ->
    effect { observed.add(map["pending"]) }
    owner
}

map["pending"] = 7

// observed == [null, 7]
```

Without that, "render nothing until this arrives" would never update, which is the single most
common shape a map-shaped state is used for.

Reading `size` or any collection view registers on the structure signal, which changes when a key is
added or removed but not when an existing key's value is replaced.

`containsKey` registers on that key's slot and answers by testing the stored value against null, so
a map whose value type is nullable will report `false` for a key explicitly holding `null`. Model
"present but empty" with a non-null sentinel rather than a null value.

## `ReactiveSet`

Membership is tracked per element. Asking about an absent element registers, so a later add wakes
the asker — which is the whole point for "is this selected":

```kotlin
val set = reactiveSetOf("a")
val first = mutableListOf<Boolean>()
val second = mutableListOf<Boolean>()

val owner = createRoot { owner ->
    effect { first.add("a" in set) }
    effect { second.add("b" in set) }
    owner
}

set.add("b")

// first  == [true]           — never woken
// second == [false, true]
```

Reading `size` or iterating registers on the structure signal instead.

## `mapKeyed`

```kotlin
public fun <E, R> ReactiveList<E>.mapKeyed(
    key: (E) -> Any?,
    transform: (element: State<E>, index: State<Int>) -> R,
): State<List<R>>
```

Derives one result per element, **keyed by identity rather than by position**. `transform` runs once
per key, not once per change:

```kotlin
val rows = reactiveListOf(Row(1, "one"), Row(2, "two"), Row(3, "three"))
var builds = 0

lateinit var labels: State<List<String>>
val owner = createRoot { owner ->
    labels = rows.mapKeyed(key = { it.id }) { element, _ ->
        builds++
        element.value.label
    }
    owner
}

builds              // 3
rows.move(0, 2)
builds              // still 3 — the results moved, they were not rebuilt
labels.value        // ["two", "three", "one"]
```

An element that moves keeps the result built for it, with its index state updated. An element
replaced in place keeps its result and sees the new value through its element state. Only a
genuinely new key builds anything, and only a departed key tears anything down.

This is what makes list-shaped UI viable. Deriving with `memo { list.map { … } }` instead is
*correct*, and it rebuilds every result on every change — which for a list of views means discarding
and recreating the whole list to move one row.

The usual shape:

```kotlin
val rows = todos.mapKeyed(key = { it.id }) { todo, index ->
    RowView().also { row ->
        effect { row.label = todo().label }
        effect { row.position = index() }
    }
}
```

An element that moves takes its `RowView` with it: the view is not rebuilt, its `position` effect
simply re-runs with the new index. An element edited in place keeps its view too, and only the
`label` effect re-runs.

Each result is built under its own child owner, so anything `transform` creates — nested effects,
cleanups — is disposed when that element leaves the list:

```kotlin
val list = reactiveListOf(Row(1, "one"), Row(2, "two"))
val cleaned = mutableListOf<Int>()

val owner = createRoot { owner ->
    list.mapKeyed(key = { it.id }) { element, _ ->
        val id = element.value.id
        onCleanup { cleaned.add(id) }
    }
    owner
}

list.removeAt(0)
// cleaned == [1]

owner.dispose()
// cleaned == [1, 2]
```

`transform` runs with the graph lock released, so it may build views, take other locks, and create
effects of its own. `mapKeyed` itself needs an enclosing owner: without one there is nothing to
dispose the per-element scopes.

### Cost

Replacing an element is **O(1)**. Each entry watches its own position, so an edit wakes that entry
alone and reconciliation does not run at all.

A structural change — insert, remove, move, clear — is **O(n)**, because every position after the
edit point now holds a different element and the key-to-result mapping has to be rebuilt.

The numbers are in [Performance](performance.md#reactive-collections); the short version is that an
edit is about 120 ns and a move on a hundred rows is about 15 µs. The trade is deliberate: in list
UI, edits vastly outnumber structural changes.

## Thread safety

Reads are safe concurrently with a write — the underlying signals are the ordinary graph.
**Structural edits from several threads at once are not.** Serialise writers yourself, the same as
with any `ArrayList`.

One more caveat: `ReactiveMap.entries` and `ReactiveSet.iterator()` expose the backing collection.
Mutating through them bypasses the signals, so nothing is woken. Go through the collection's own
methods.
