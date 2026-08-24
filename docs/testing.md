# Testing

Two problems, and both have the same shape as in production code: **who owns the lifetime**, and
**when do effects run**.

## Give each test its own owner

An effect that outlives its test does not fail that test. It leaks into the next one, and the
failure lands somewhere unrelated.

```kotlin
class CartTest {
    private lateinit var root: Owner

    @BeforeTest
    fun setUp() {
        root = createOwner()
    }

    @AfterTest
    fun tearDown() {
        root.dispose()
    }

    @Test
    fun subtotalTracksItems() = runWithOwner(root) {
        val cart = Cart()
        val observed = mutableListOf<Int>()
        effect { observed.add(cart.subtotal()) }

        cart.add(Item(price = 5))
        cart.add(Item(price = 3))

        assertEquals(listOf(0, 5, 8), observed)
    }
}
```

`runWithOwner(root) { … }` as the test body is the shortest form. `createRoot` works too, but then
each test disposes its own owner and a failing assertion skips the disposal.

Derived state needs none of this. A test of a `memo` is a test of a pure function:

```kotlin
val celsius = mutableStateOf(20)
val fahrenheit = memo { celsius() * 9 / 5 + 32 }

assertEquals(68, fahrenheit.value)
celsius.set(100)
assertEquals(212, fahrenheit.value)
```

No owner, no teardown, nothing to leak.

## Control when effects run

Under `Scheduler.Immediate` — the default — an effect body has already run by the time `set` returns,
so a naive assertion passes. The moment production moves to a real scheduler the same test becomes a
race.

**Inject the scheduler.** In a test, use a queued one and drain at the points you want to assert:

```kotlin
val scheduler = Scheduler.queued()
val root = createOwner(scheduler)

val cart = Cart()
val observed = mutableListOf<Int>()
runWithOwner(root) { effect { observed.add(cart.subtotal()) } }

scheduler.drain()
assertEquals(listOf(0), observed)

cart.add(Item(price = 5))
scheduler.drain()
assertEquals(listOf(0, 5), observed)

root.dispose()
```

Three properties make this reliable:

- **Nothing runs before the first drain,** the effect's initial run included. Until then it has no
  dependencies.
- **Dispatch coalesces.** Several writes before a drain produce one run with the latest value, so
  the assertion is about the settled state rather than about intermediates.
- **Tasks queued during a drain are picked up by the same call,** so an effect that writes a source
  settles before `drain()` returns.

`scheduler.size` tells you how many tasks are waiting, which is occasionally a more direct assertion
than the effect's output.

## Assert you have not leaked

`Owner` reports what it holds, specifically so a leak can be asserted from outside the library:

```kotlin
@AfterTest
fun tearDown() {
    root.dispose()
    assertTrue(root.isEmpty)
}
```

Mid-test it is a way to check that a component built what you expected and tore all of it down:

```kotlin
val owner = createOwner()
runWithOwner(owner) {
    effect { view.title = title() }
    effect { view.subtitle = subtitle() }
    effect { view.badge = badge() }
}

assertEquals(3, owner.computationCount)

owner.dispose()
assertTrue(owner.isEmpty)
```

`childCount`, `cleanupCount` and `computationCount` are the three counters. Remember that every
effect creates a child owner for its own scope, so two effects means two children.

## Assert on warnings

`StateWarnings.handler` is the hook. Replace it, run the code, put it back:

```kotlin
val warnings = mutableListOf<String>()
val previous = StateWarnings.handler
StateWarnings.handler = { message -> warnings.add(message) }

try {
    val handle = effect { source() }   // no enclosing root
    handle.dispose()
} finally {
    StateWarnings.handler = previous
}

assertEquals(1, warnings.size)
```

It is process-wide and unsynchronised, so restore it in a `finally` and do not run such tests in
parallel with each other.

## Testing an effect that does asynchronous work

Keep the graph part synchronous and assert on the source the async work writes back to:

```kotlin
val query = mutableStateOf("kotlin")
val results = mutableStateOf<List<String>>(emptyList())

val owner = createOwner()
runWithOwner(owner) {
    effect { results.set(search(query())) }
}

query.set("multiplatform")
assertEquals(listOf("multiplatform:1"), results.value)

owner.dispose()
```

With coroutines, `runTest` plus `advanceUntilIdle()` after each write does the same job. The
coroutines adapter's own tests are the worked example.

## Things that catch people

**Asserting a memo ran.** It might not have. An unobserved memo never runs, and a memo whose inputs
settled back to their old values does not re-run. Count runs with a `var` inside the body if that is
the property you mean to test, and read the memo to force it.

```kotlin
var runs = 0
val doubled = memo { runs++; source() * 2 }

assertEquals(0, runs)      // nothing has read it
doubled.value
assertEquals(1, runs)
```

**Asserting the first effect run appears.** It does, always — the initial run goes through the
scheduler like every other. Under a queued scheduler that means after the first `drain()`, not at
`effect { }`.

**Asserting on a `Double` rendered into a string.** `Double.toString` differs between the JVM and
Kotlin/JS: `"${77.0}"` is `77.0` on one and `77` on the other. Tests in `commonTest` run on every
target, so build such assertions from the value rather than from a literal, or use `Int`.

**Comparing states.** `State` uses identity equality. `stateOf("a") == stateOf("a")` is `false`;
compare `.value`.

**Reusing a source across tests.** A top-level `val` state keeps its value and its dependents between
tests. Build state inside the test.
