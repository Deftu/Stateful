import androidx.compose.runtime.snapshots.Snapshot
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.compose.asComposeMutableState
import dev.deftu.stateful.compose.asComposeState
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeBridgeTest {
    @Test
    fun aStatefulStateBecomesAComposeState() {
        val owner = createOwner()
        val source = mutableStateOf("a")
        val bridged = source.asComposeState(owner)

        assertEquals("a", bridged.value)

        source.set("b")

        assertEquals("b", bridged.value)
        owner.dispose()
    }

    @Test
    fun aDerivedStateCrossesTheBridge() {
        val owner = createOwner()
        val source = mutableStateOf(2)
        val doubled = memo { source() * 2 }
        val bridged = doubled.asComposeState(owner)

        assertEquals(4, bridged.value)

        source.set(5)

        assertEquals(10, bridged.value)
        owner.dispose()
    }

    @Test
    fun disposingTheOwnerStopsTheBridge() {
        val owner = createOwner()
        val source = mutableStateOf(1)
        val bridged = source.asComposeState(owner)

        source.set(2)
        owner.dispose()
        source.set(3)

        assertEquals(2, bridged.value)
    }

    @Test
    fun aMutableBridgeCarriesWritesInBothDirections() {
        val owner = createOwner()
        val source = mutableStateOf(1)
        val bridged = source.asComposeMutableState(owner)

        source.set(2)
        assertEquals(2, bridged.value)

        bridged.value = 3
        assertEquals(3, source.value)

        owner.dispose()
    }

    @Test
    fun aMutableBridgeDoesNotLoop() {
        val owner = createOwner()
        val source = mutableStateOf(0)
        var writes = 0
        val bridged = source.asComposeMutableState(owner)

        val subscription = source.subscribe { writes++ }
        bridged.value = 7

        assertEquals(7, source.value)
        assertEquals(1, writes)

        subscription.dispose()
        owner.dispose()
    }

    @Test
    fun aComposeReadInsideASnapshotSeesTheBridgedValue() {
        val owner = createOwner()
        val source = mutableStateOf("start")
        val bridged = source.asComposeState(owner)

        source.set("changed")

        val observed = Snapshot.takeSnapshot().run {
            try {
                enter { bridged.value }
            } finally {
                dispose()
            }
        }

        assertEquals("changed", observed)
        owner.dispose()
    }

    @Test
    fun theMutableBridgeDestructures() {
        val owner = createOwner()
        val source = mutableStateOf(1)
        val (value, setValue) = source.asComposeMutableState(owner)

        assertEquals(1, value)

        setValue(9)

        assertEquals(9, source.value)
        owner.dispose()
    }
}
