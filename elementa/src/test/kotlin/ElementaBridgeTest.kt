import dev.deftu.stateful.Diagnostics
import dev.deftu.stateful.dsl.createRoot
import dev.deftu.stateful.dsl.effect
import dev.deftu.stateful.dsl.memo
import dev.deftu.stateful.dsl.mutableStateOf
import dev.deftu.stateful.elementa.asElementaMutableState
import dev.deftu.stateful.elementa.asElementaState
import dev.deftu.stateful.elementa.asStatefulState
import dev.deftu.stateful.elementa.createStatefulOwner
import gg.essential.elementa.unstable.state.v2.ReferenceHolderImpl
import gg.essential.elementa.unstable.state.v2.mutableStateOf as elementaMutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ElementaBridgeTest {
    @Test
    fun anElementaStateBecomesAStatefulOne() {
        val source = elementaMutableStateOf(1)

        val owner = createRoot { owner ->
            val bridged = source.asStatefulState()
            assertEquals(1, bridged.value)

            source.set(2)
            assertEquals(2, bridged.value)

            owner
        }

        owner.dispose()
    }

    @Test
    fun aBridgedElementaStateDrivesDerivedState() {
        val source = elementaMutableStateOf(2)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            val bridged = source.asStatefulState()
            val doubled = memo { bridged() * 2 }
            effect { observed.add(doubled()) }
            owner
        }

        source.set(5)

        assertEquals(listOf(4, 10), observed)
        owner.dispose()
    }

    @Test
    fun disposingTheOwnerStopsFollowingTheElementaState() {
        val source = elementaMutableStateOf(1)
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            val bridged = source.asStatefulState()
            effect { observed.add(bridged()) }
            owner
        }

        source.set(2)
        owner.dispose()
        source.set(3)

        assertEquals(listOf(1, 2), observed)
    }

    @Test
    fun aStatefulStateBecomesAnElementaOne() {
        val source = mutableStateOf("a")

        val owner = createRoot { owner ->
            val bridged = source.asElementaState()
            assertEquals("a", bridged.getUntracked())

            source.set("b")
            assertEquals("b", bridged.getUntracked())

            owner
        }

        owner.dispose()
    }

    @Test
    fun aBridgedStatefulStateDrivesAnElementaMemo() {
        val source = mutableStateOf(3)
        val holder = ReferenceHolderImpl()
        val observed = mutableListOf<Int>()

        val owner = createRoot { owner ->
            val bridged = source.asElementaState()
            gg.essential.elementa.unstable.state.v2.effect(holder) { observed.add(bridged() * 10) }
            owner
        }

        source.set(4)

        assertEquals(listOf(30, 40), observed)
        owner.dispose()
    }

    @Test
    fun aMutableBridgeCarriesWritesInBothDirections() {
        val source = mutableStateOf(1)

        val owner = createRoot { owner ->
            val bridged = source.asElementaMutableState()

            source.set(2)
            assertEquals(2, bridged.getUntracked())

            bridged.set(3)
            assertEquals(3, source.value)

            owner
        }

        owner.dispose()
    }

    @Test
    fun aMutableBridgeDoesNotLoop() {
        val source = mutableStateOf(0)
        var writes = 0

        val owner = createRoot { owner ->
            val bridged = source.asElementaMutableState()
            effect { source(); writes++ }
            writes = 0

            bridged.set(7)
            owner
        }

        assertEquals(7, source.value)
        assertEquals(1, writes)
        owner.dispose()
    }

    @Test
    fun aReferenceHolderCanHoldAStatefulOwner() {
        val holder = ReferenceHolderImpl()
        val owner = holder.createStatefulOwner()

        assertTrue(!owner.isDisposed)

        owner.dispose()

        assertTrue(owner.isDisposed)
    }

    @Test
    fun bridgingOutsideARootWarns() {
        val warnings = mutableListOf<String>()
        val previous = Diagnostics.onWarning
        Diagnostics.onWarning = { message -> warnings.add(message) }

        try {
            elementaMutableStateOf(1).asStatefulState()
        } finally {
            Diagnostics.onWarning = previous
        }

        assertTrue(warnings.any { it.contains("asStatefulState") }, warnings.toString())
    }
}
