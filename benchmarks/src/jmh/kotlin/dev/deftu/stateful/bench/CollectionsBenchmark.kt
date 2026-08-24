package dev.deftu.stateful.bench

import dev.deftu.stateful.Owner
import dev.deftu.stateful.State
import dev.deftu.stateful.collections.ReactiveList
import dev.deftu.stateful.collections.ReactiveMap
import dev.deftu.stateful.collections.mapKeyed
import dev.deftu.stateful.collections.reactiveListOf
import dev.deftu.stateful.collections.reactiveMapOf
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
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
 * Reactive collections, which are what list-shaped UI spends its time on.
 *
 * Every mutating benchmark is self-inverse so the collection does not grow across an iteration and
 * the measurement stays comparable from one invocation to the next.
 */
@JmhState(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
open class CollectionsBenchmark {
    private data class Row(val id: Int, val label: String)

    private lateinit var owner: Owner
    private lateinit var list: ReactiveList<String>
    private lateinit var rows: ReactiveList<Row>
    private lateinit var map: ReactiveMap<String, Int>
    private lateinit var size: State<Int>
    private lateinit var joined: State<String>
    private lateinit var keyed: State<List<String>>

    private var counter = 0

    @Setup(Level.Iteration)
    fun setUp() {
        owner = createOwner()

        list = reactiveListOf(List(100) { index -> "item $index" })
        rows = reactiveListOf(List(100) { index -> Row(index, "row $index") })
        map = reactiveMapOf(List(100) { index -> "key $index" to index }.toMap())

        size = memo { list.size }
        joined = memo { list[0] + list[1] }

        runWithOwner(owner) {
            keyed = rows.mapKeyed(key = { row -> row.id }) { element, _ -> element.value.label }
        }

        size.value
        joined.value
        keyed.value
    }

    @TearDown(Level.Iteration)
    fun tearDown() {
        owner.dispose()
    }

    @Benchmark
    fun untrackedIndexedRead(blackhole: Blackhole) {
        blackhole.consume(list.getUntracked(0))
    }

    @Benchmark
    fun trackedIndexedRead(blackhole: Blackhole) {
        blackhole.consume(list[0])
    }

    @Benchmark
    fun replaceAnElementNobodyReads() {
        list[50] = "changed ${counter++}"
    }

    @Benchmark
    fun replaceAnElementUnderAMemo(blackhole: Blackhole) {
        list[0] = "changed ${counter++}"
        blackhole.consume(joined.value)
    }

    @Benchmark
    fun appendAndRemove(blackhole: Blackhole) {
        list.add("appended")
        list.removeAt(list.untrackedSize - 1)
        blackhole.consume(size.value)
    }

    @Benchmark
    fun untrackedMapRead(blackhole: Blackhole) {
        blackhole.consume(map.getUntracked("key 0"))
    }

    @Benchmark
    fun trackedMapRead(blackhole: Blackhole) {
        blackhole.consume(map["key 0"])
    }

    @Benchmark
    fun replaceAMapEntry() {
        map["key 50"] = counter++
    }

    @Benchmark
    fun keyedReconciliationOnAMove(blackhole: Blackhole) {
        rows.move(0, 50)
        rows.move(50, 0)
        blackhole.consume(keyed.value)
    }

    @Benchmark
    fun keyedReconciliationOnAnEdit(blackhole: Blackhole) {
        rows[10] = Row(10, "edited ${counter++}")
        blackhole.consume(keyed.value)
    }
}
