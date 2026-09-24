package me.ash.reader.ui.component.base

import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import me.ash.reader.ui.theme.ProvideUiTextScale

@Composable
fun RYDialog(
    modifier: Modifier = Modifier,
    visible: Boolean,
    properties: DialogProperties = DialogProperties(),
    onDismissRequest: () -> Unit = {},
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: @Composable (() -> Unit)? = null,
) {
    if (visible) {
        // Almost every dialog in the app is built on this one, so applying the interface text size
        // here covers them all at once - and covers them *uniformly*, whether they were opened
        // from a settings page or from a list page.
        //
        // It also has to be here rather than only on the settings routes: a dialog is composed
        // into its own window, and while composition locals do cross that boundary, relying on a
        // page-level scope would make a dialog's size depend on where it was opened from. Applying
        // it in both places is safe because `ProvideUiTextScale` rebuilds from the captured
        // unscaled typography instead of multiplying the current one.
        ProvideUiTextScale {
            AlertDialog(
                properties = properties,
                modifier = modifier,
                onDismissRequest = onDismissRequest,
                icon = icon,
                title = title,
                text = text,
                confirmButton = confirmButton,
                dismissButton = dismissButton,
            )
        }
    }
}