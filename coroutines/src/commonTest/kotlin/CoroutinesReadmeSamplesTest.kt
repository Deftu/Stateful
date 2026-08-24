import dev.deftu.stateful.coroutines.asFlow
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.dsl.runWithOwner
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals

class CoroutinesReadmeSamplesTest {
    private class Repository {
        suspend fun fetch(id: Int): String {
            yield()
            return "detail $id"
        }
    }

    @Test
    fun theAsynchronousWorkSampleRuns() = runTest {
        val repository = Repository()
        val selectedId = mutableStateOf(1)
        val detail = mutableStateOf("")
        val owner = createOwner()

        runWithOwner(owner) {
            effect {
                val id = selectedId()
                launch { detail.set(repository.fetch(id)) }
            }
        }

        advanceUntilIdle()
        assertEquals("detail 1", detail.value)

        selectedId.set(2)
        advanceUntilIdle()
        assertEquals("detail 2", detail.value)

        owner.dispose()
    }

    @Test
    fun aCallerComposesConflationRatherThanGettingItByDefault() = runTest {
        val source = mutableStateOf(1)
        val seen = mutableListOf<Int>()

        val job = launch { source.asFlow().conflate().take(1).collect { seen.add(it) } }
        job.join()

        assertEquals(listOf(1), seen)
    }
}
