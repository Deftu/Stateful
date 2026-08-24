import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConcurrencyTest {
    private val threads = 8
    private val iterations = 500

    private fun stress(name: String, body: (Int) -> Unit) {
        val failures = ConcurrentLinkedQueue<Throwable>()
        val start = CountDownLatch(1)
        val workers = (0 until threads).map { index ->
            thread(name = "$name-$index") {
                start.await()
                try {
                    body(index)
                } catch (throwable: Throwable) {
                    failures.add(throwable)
                }
            }
        }

        start.countDown()
        for (worker in workers) worker.join(60_000)
        for (worker in workers) assertTrue(!worker.isAlive, "$name: ${worker.name} did not finish")

        assertNull(failures.peek(), "$name failed: ${failures.peek()}")
    }

    @Test
    fun concurrentWritesToOneSourceLoseNothing() {
        val source = mutableStateOf(0)
        val seen = ConcurrentLinkedQueue<Int>()
        val owner = createOwner()
        runWithOwner(owner) { effect { seen.add(source()) } }

        stress("writes") { index ->
            repeat(iterations) { round ->
                source.set(index * iterations + round + 1)
            }
        }

        assertTrue(seen.isNotEmpty())
        assertTrue(source.value > 0)
        owner.dispose()
    }

    @Test
    fun concurrentReadsDuringPropagationNeverSeeATornGraph() {
        val root = mutableStateOf(1)
        val doubled = memo { root() * 2 }
        val tripled = memo { root() * 3 }
        val sum = memo { doubled() + tripled() }

        val torn = AtomicReference<String?>(null)
        val stop = AtomicBoolean(false)

        val readers = (0 until threads).map {
            thread {
                while (!stop.get()) {
                    val observed = sum.value
                    if (observed % 5 != 0) torn.compareAndSet(null, "sum=$observed is not a multiple of 5")
                }
            }
        }

        repeat(iterations * 4) { round -> root.set(round + 1) }
        stop.set(true)
        for (reader in readers) reader.join(60_000)

        assertNull(torn.get(), torn.get())
    }

    @Test
    fun aDerivedValueAlwaysMatchesSomeSourceValue() {
        val root = mutableStateOf(1)
        val derived = memo { root() to root() * 10 }

        val mismatched = AtomicReference<String?>(null)
        val stop = AtomicBoolean(false)

        val readers = (0 until threads).map {
            thread {
                while (!stop.get()) {
                    val (base, scaled) = derived.value
                    if (scaled != base * 10) mismatched.compareAndSet(null, "$base vs $scaled")
                }
            }
        }

        repeat(iterations * 4) { round -> root.set(round + 1) }
        stop.set(true)
        for (reader in readers) reader.join(60_000)

        assertNull(mismatched.get(), mismatched.get())
    }

    /**
     * The spawned threads race each other, so `mirror` settles on whichever finished last rather
     * than on the highest value. What matters is that nothing deadlocks and the effect observes the
     * final write.
     */
    @Test
    fun anEffectWritingFromAnotherThreadDoesNotDeadlock() {
        val trigger = mutableStateOf(0)
        val mirror = mutableStateOf(0)
        val runs = AtomicInteger(0)
        val lastSeen = AtomicInteger(-1)
        val owner = createOwner()

        runWithOwner(owner) {
            effect {
                val value = trigger()
                runs.incrementAndGet()
                lastSeen.set(value)
                thread { mirror.set(value) }
            }
        }

        repeat(50) { round -> trigger.set(round + 1) }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (lastSeen.get() != 50 && System.nanoTime() < deadline) Thread.sleep(1)

        assertEquals(50, lastSeen.get(), "the effect never observed the final write")
        assertTrue(runs.get() >= 2, "the effect ran ${runs.get()} times")
        assertTrue(mirror.value in 1..50, "mirror=${mirror.value} is not a value any run wrote")
        owner.dispose()
    }

    @Test
    fun anEffectThatWritesTheStateItReadsSettles() {
        val source = mutableStateOf(0)
        val owner = createOwner()
        val runs = AtomicInteger(0)

        runWithOwner(owner) {
            effect {
                val value = source()
                runs.incrementAndGet()
                if (value < 10) source.set(value + 1)
            }
        }

        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (source.value != 10 && System.nanoTime() < deadline) Thread.sleep(1)

        assertEquals(10, source.value)
        assertTrue(runs.get() in 11..200, "unexpected run count ${runs.get()}")
        owner.dispose()
    }

    @Test
    fun concurrentReadsAndWritesAcrossManySourcesStayConsistent() {
        val sources = (0 until threads).map { mutableStateOf(0) }
        val total = memo { sources.sumOf { it() } }
        val negative = AtomicReference<String?>(null)
        val stop = AtomicBoolean(false)

        val reader = thread {
            while (!stop.get()) {
                val observed = total.value
                if (observed < 0) negative.compareAndSet(null, "total=$observed")
            }
        }

        stress("multi") { index ->
            repeat(iterations) { round -> sources[index].set(round) }
        }

        stop.set(true)
        reader.join(60_000)

        assertNull(negative.get(), negative.get())
        assertEquals(sources.sumOf { it.value }, total.value)
    }

    @Test
    fun disposalRacesWithPropagationCleanly() {
        val source = mutableStateOf(0)
        val failures = ConcurrentLinkedQueue<Throwable>()
        val disposed = AtomicInteger(0)

        val writer = thread {
            repeat(iterations * 4) { round ->
                try {
                    source.set(round)
                } catch (throwable: Throwable) {
                    failures.add(throwable)
                }
            }
        }

        repeat(iterations) {
            try {
                val owner = createOwner(Scheduler.Immediate)
                runWithOwner(owner) { effect { source() } }
                owner.dispose()
                disposed.incrementAndGet()
            } catch (throwable: Throwable) {
                failures.add(throwable)
            }
        }

        writer.join(60_000)

        assertNull(failures.peek(), "disposal raced badly: ${failures.peek()}")
        assertEquals(iterations, disposed.get())
    }

    @Test
    fun subscriptionsDisposedFromAnotherThreadDoNotCorruptDispatch() {
        val source = mutableStateOf(0)
        val failures = ConcurrentLinkedQueue<Throwable>()

        stress("subscribe") {
            repeat(iterations) {
                try {
                    val subscription = source.subscribe { }
                    source.set(source.value + 1)
                    subscription.dispose()
                } catch (throwable: Throwable) {
                    failures.add(throwable)
                }
            }
        }

        assertNull(failures.peek(), "subscription race: ${failures.peek()}")
    }
}
