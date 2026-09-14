package app.aaps.trio.ui.compose.main

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.core.ui.compose.AapsTopAppBar

@Composable
fun TrioTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    AapsTopAppBar(
        title = { Text(title) },
        modifier = modifier,
        actions = actions
    )
}

@Preview(showBackground = true)
@Composable
private fun TrioTopBarPreview() {
    AapsTheme {
        TrioTopBar(title = "Trio")
    }
}
