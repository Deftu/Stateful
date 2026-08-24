package dev.deftu.stateful.bench

import dev.deftu.stateful.MutableState
import dev.deftu.stateful.Owner
import dev.deftu.stateful.State
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.derivedStateOf
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.BenchmarkMode
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Mode
import org.openjdk.jmh.annotations.OutputTimeUnit
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State as JmhState
import org.openjdk.jmh.annotations.TearDown
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit

/**
 * The read and write paths, which are what every consumer pays on every frame.
 *
 * A clean read is the hottest path in the library: an effect that re-runs reads each of its
 * dependencies, and most of those are unchanged.
 */
@JmhState(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class GraphBenchmark {
    private lateinit var owner: Owner
    private lateinit var source: MutableState<Int>
    private lateinit var derived: State<Int>
    private lateinit var uncached: State<Int>
    private lateinit var chain: State<Int>
    private lateinit var diamond: State<Int>
    private lateinit var observed: MutableState<Int>

    private var counter = 0

    @Setup(Level.Iteration)
    fun setUp() {
        owner = createOwner()
        source = mutableStateOf(0)
        derived = memo { source() * 2 }
        uncached = derivedStateOf { source() * 2 }

        var link: State<Int> = source
        repeat(10) {
            val previous = link
            link = memo { previous() + 1 }
        }
        chain = link

        val left = memo { source() * 2 }
        val right = memo { source() * 3 }
        diamond = memo { left() + right() }

        observed = mutableStateOf(0)
        runWithOwner(owner) { effect { observed() } }

        derived.value
        uncached.value
        chain.value
        diamond.value
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        owner.dispose()
    }

    @Benchmark
    fun untrackedReadOfASource(blackhole: Blackhole) {
        blackhole.consume(source.value)
    }

    @Benchmark
    fun untrackedReadOfACleanMemo(blackhole: Blackhole) {
        blackhole.consume(derived.value)
    }

    @Benchmark
    fun untrackedReadOfACleanChain(blackhole: Blackhole) {
        blackhole.consume(chain.value)
    }

    @Benchmark
    fun readOfAnUncachedDerivation(blackhole: Blackhole) {
        blackhole.consume(uncached.value)
    }

    @Benchmark
    fun writeWithNoDependents() {
        source.set(counter++)
    }

    @Benchmark
    fun writeThenReadAMemo(blackhole: Blackhole) {
        source.set(counter++)
        blackhole.consume(derived.value)
    }

    @Benchmark
    fun writeThenReadADiamond(blackhole: Blackhole) {
        source.set(counter++)
        blackhole.consume(diamond.value)
    }

    @Benchmark
    fun writeThenReadAChain(blackhole: Blackhole) {
        source.set(counter++)
        blackhole.consume(chain.value)
    }

    @Benchmark
    fun writeThatWakesAnEffect() {
        observed.set(counter++)
    }

    @Benchmark
    fun writeThatChangesNothing() {
        source.set(0)
    }
}
