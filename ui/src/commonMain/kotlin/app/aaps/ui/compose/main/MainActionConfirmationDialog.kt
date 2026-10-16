package app.aaps.ui.compose.main

import androidx.compose.runtime.Composable
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.dialogs.OkCancelDialog
import app.aaps.core.ui.compose.dialogs.ThreeButtonDialog
import app.aaps.core.ui.compose.stringResource

@Composable
fun MainActionConfirmationDialog(
    confirmation: ActionConfirmation,
    onConfirm: (ConfirmableAction) -> Unit,
    onDismiss: () -> Unit
) {
    val secondaryAction = confirmation.secondaryAction
    val secondaryLabel = confirmation.secondaryLabel
    if (secondaryAction != null && secondaryLabel != null) {
        ThreeButtonDialog(
            title = confirmation.title,
            message = confirmation.message,
            icon = confirmation.icon,
            primaryLabel = confirmation.confirmLabel ?: stringResource(CoreUiStrings.ok),
            onPrimary = { onConfirm(confirmation.onConfirmAction) },
            secondaryLabel = secondaryLabel,
            onSecondary = { onConfirm(secondaryAction) },
            onDismiss = onDismiss
        )
    } else {
        OkCancelDialog(
            title = confirmation.title,
            message = confirmation.message,
            icon = confirmation.icon,
            onConfirm = { onConfirm(confirmation.onConfirmAction) },
            onDismiss = onDismiss
        )
    }
}
