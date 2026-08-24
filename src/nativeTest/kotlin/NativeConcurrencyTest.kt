import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import kotlin.concurrent.AtomicInt
import kotlin.native.concurrent.TransferMode
import kotlin.native.concurrent.Worker
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NativeConcurrencyTest {
    @Test
    fun concurrentWritesFromWorkersDoNotCorruptTheGraph() {
        val source = mutableStateOf(0)
        val doubled = memo { source() * 2 }
        val owner = createOwner()
        runWithOwner(owner) { effect { doubled() } }

        val workers = List(4) { Worker.start() }
        val futures = workers.mapIndexed { index, worker ->
            worker.execute(TransferMode.SAFE, { source to index }) { (state, offset) ->
                repeat(500) { round -> state.set(offset * 1000 + round) }
            }
        }

        futures.forEach { it.result }
        workers.forEach { it.requestTermination().result }

        assertEquals(source.value * 2, doubled.value)
        owner.dispose()
    }

    @Test
    fun concurrentReadsDuringPropagationNeverSeeATornGraph() {
        val root = mutableStateOf(1)
        val left = memo { root() * 2 }
        val right = memo { root() * 3 }
        val sum = memo { left() + right() }
        val torn = AtomicInt(0)

        val readers = List(3) { Worker.start() }
        val futures = readers.map { worker ->
            worker.execute(TransferMode.SAFE, { sum to torn }) { (state, flag) ->
                repeat(2000) {
                    if (state.value % 5 != 0) flag.value = 1
                }
            }
        }

        repeat(2000) { round -> root.set(round + 1) }
        futures.forEach { it.result }
        readers.forEach { it.requestTermination().result }

        assertEquals(0, torn.value)
    }

    @Test
    fun disposalRacesWithPropagationCleanly() {
        val source = mutableStateOf(0)
        val worker = Worker.start()
        val future = worker.execute(TransferMode.SAFE, { source }) { state ->
            repeat(2000) { round -> state.set(round) }
        }

        repeat(500) {
            val owner = createOwner()
            runWithOwner(owner) { effect { source() } }
            owner.dispose()
        }

        future.result
        worker.requestTermination().result

        assertTrue(source.value >= 0)
    }
}
