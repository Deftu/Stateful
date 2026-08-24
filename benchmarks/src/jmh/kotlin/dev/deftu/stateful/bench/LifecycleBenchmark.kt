package dev.deftu.stateful.bench

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.Scheduler
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.onCleanup
import dev.deftu.stateful.dsl.runWithOwner
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State as JmhState
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * Building and tearing down lifetimes, which a UI does on every mount and unmount.
 *
 * Nothing here is amortised: a screen that builds a hundred effects pays this once per navigation,
 * so the numbers matter in whole milliseconds rather than nanoseconds per operation.
 */
@JmhState(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class LifecycleBenchmark {
    private lateinit var source: MutableState<Int>
    private lateinit var longLived: Owner

    @Setup(Level.Iteration)
    fun setUp() {
        source = mutableStateOf(0)
        longLived = createOwner()
    }

    @Benchmark
    fun createAndDisposeAnEmptyRoot(blackhole: Blackhole) {
        val owner = createOwner()
        blackhole.consume(owner)
        owner.dispose()
    }

    @Benchmark
    fun createAndDisposeARootWithOneEffect() {
        val owner = createRoot { owner ->
            effect { source() }
            owner
        }

        owner.dispose()
    }

    @Benchmark
    fun createAndDisposeARootWithTenEffects() {
        val owner = createRoot { owner ->
            repeat(10) { effect { source() } }
            owner
        }

        owner.dispose()
    }

    @Benchmark
    fun createAndDisposeANestedTree() {
        val owner = createRoot { owner ->
            effect {
                source()
                repeat(4) { effect { source() } }
            }
            owner
        }

        owner.dispose()
    }

    @Benchmark
    fun createAndDisposeCleanups() {
        val owner = createRoot { owner ->
            repeat(10) { onCleanup { } }
            owner
        }

        owner.dispose()
    }

    @Benchmark
    fun subscribeAndDispose() {
        val subscription = source.subscribe { }
        subscription.dispose()
    }

    @Benchmark
    fun subscribeReceiveAndDispose() {
        val subscription = source.subscribe { }
        source.set(source.value + 1)
        subscription.dispose()
    }

    @Benchmark
    fun buildAndDiscardAMemoChain(blackhole: Blackhole) {
        var link: dev.deftu.stateful.State<Int> = source
        repeat(5) {
            val previous = link
            link = memo { previous() + 1 }
        }

        blackhole.consume(link.value)
    }

    @Benchmark
    fun buildAnEffectOnADeferringScheduler() {
        val scheduler = Scheduler.queued()
        val owner = createOwner(scheduler)
        runWithOwner(owner) { effect { source() } }
        scheduler.drain()
        owner.dispose()
    }
}
