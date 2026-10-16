package app.aaps.ui.compose.main

import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.aaps.ui.compose.testing.AapsScreenFixture
import app.aaps.ui.compose.testing.setAapsContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import app.aaps.core.ui.R as CoreUiR

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class MainActionConfirmationDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private var confirmed: ConfirmableAction? = null
    private var dismissed = false
    private val screen = AapsScreenFixture()
    private val context = RuntimeEnvironment.getApplication()

    private fun show(confirmation: ActionConfirmation) {
        compose.setAapsContent(screen) {
            MainActionConfirmationDialog(
                confirmation = confirmation,
                onConfirm = { confirmed = it },
                onDismiss = { dismissed = true }
            )
        }
    }

    @Test
    fun endSceneConfirmsStopWithoutAChain() {
        show(ActionConfirmation("End scene", "End Activity?", onConfirmAction = ConfirmableAction.DeactivateScene))

        compose.onNodeWithText(context.getString(CoreUiR.string.ok)).performClick()

        assertEquals(ConfirmableAction.DeactivateScene, confirmed)
    }

    @Test
    fun skipConfirmsTheChainAction() {
        val chain = ConfirmableAction.DeactivateAndChainScene("cooldown")
        show(
            ActionConfirmation(
                "End scene", "End Activity?", onConfirmAction = chain,
                confirmLabel = "Skip to Cooldown",
                secondaryAction = ConfirmableAction.DeactivateScene,
                secondaryLabel = "End scene"
            )
        )

        compose.onNodeWithText("Skip to Cooldown").performClick()

        assertEquals(chain, confirmed)
    }

    @Test
    fun endSceneDoesNotStartTheChain() {
        show(
            ActionConfirmation(
                "End Activity", "End Activity?",
                onConfirmAction = ConfirmableAction.DeactivateAndChainScene("cooldown"),
                confirmLabel = "Skip to Cooldown",
                secondaryAction = ConfirmableAction.DeactivateScene,
                secondaryLabel = "End scene"
            )
        )

        compose.onNodeWithText("End scene").performClick()

        assertEquals(ConfirmableAction.DeactivateScene, confirmed)
    }

    @Test
    fun cancelDoesNotEndTheScene() {
        show(ActionConfirmation("End scene", "End Activity?", onConfirmAction = ConfirmableAction.DeactivateScene))

        compose.onNodeWithText(context.getString(CoreUiR.string.cancel)).performClick()

        assertEquals(true, dismissed)
        assertNull(confirmed)
    }
}
