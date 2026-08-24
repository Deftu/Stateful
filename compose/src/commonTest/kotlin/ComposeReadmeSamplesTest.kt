import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import dev.deftu.stateful.Owner
import dev.deftu.stateful.State
import dev.deftu.stateful.compose.asComposeMutableState
import dev.deftu.stateful.compose.asComposeState
import dev.deftu.stateful.compose.rememberStatefulRoot
import dev.deftu.stateful.dsl.createOwner
import dev.deftu.stateful.dsl.mutableStateOf
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The composable shapes are declared but never invoked. Running them would need a composition;
 * type-checking them is what catches a signature drifting away from the documentation, which is the
 * failure these guard against.
 */
class ComposeReadmeSamplesTest {
    private fun text(value: String) = value

    @Composable
    private fun Broken(title: State<String>): String {
        return text(title.value)
    }

    @Composable
    private fun Fixed(title: State<String>): String {
        val root = rememberStatefulRoot()
        val value by title.asComposeState(root)
        return text(value)
    }

    @Composable
    private fun WithAnExplicitRoot(title: State<String>, root: Owner): String {
        val value by title.asComposeState(root)
        return text(value)
    }

    @Test
    fun theBridgeCarriesChanges() {
        val owner = createOwner()
        val title = mutableStateOf("first")
        val bridged = title.asComposeState(owner)

        assertEquals("first", bridged.value)

        title.set("second")

        assertEquals("second", bridged.value)
        owner.dispose()
    }

    @Test
    fun theWritableBridgeCarriesEditsBack() {
        val owner = createOwner()
        val source = mutableStateOf("typed")
        val field = source.asComposeMutableState(owner)

        field.value = "edited"

        assertEquals("edited", source.value)
        owner.dispose()
    }
}
