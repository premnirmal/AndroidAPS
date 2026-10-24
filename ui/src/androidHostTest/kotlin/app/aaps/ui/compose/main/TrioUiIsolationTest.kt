package app.aaps.ui.compose.main

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.jupiter.api.Test
import java.io.File

class TrioUiIsolationTest {

    private val root: File = generateSequence(File(checkNotNull(System.getProperty("user.dir")))) { it.parentFile }
        .first { File(it, "settings.gradle").isFile || File(it, "settings.gradle.kts").isFile }

    @Test
    fun `shared screens and chips do not own Trio code`() {
        val trioName = Regex("""\b(?:[Tt]rio\w*|isTrio)\b""")
        listOf(
            "ui/src/commonMain/kotlin/app/aaps/ui/compose/main/MainScreen.kt",
            "ui/src/commonMain/kotlin/app/aaps/ui/compose/overview/OverviewScreen.kt",
            "ui/src/commonMain/kotlin/app/aaps/ui/compose/overview/chips/ChipsViewModel.kt"
        ).forEach { path ->
            val matches = trioName.findAll(File(root, path).readText()).map { it.value }.toList()
            assertWithMessage("Trio code in $path").that(matches).isEmpty()
        }
    }

    @Test
    fun `Trio routes are registered in their own graph`() {
        val graph = File(root, "appshell/src/commonMain/kotlin/app/aaps/appshell/navigation/AppNavGraph.kt").readText()
        assertThat(graph).contains("trioNavGraph(")
        assertThat(Regex("""composable\s*\(\s*AppRoute\.Trio""").containsMatchIn(graph)).isFalse()
    }
}
