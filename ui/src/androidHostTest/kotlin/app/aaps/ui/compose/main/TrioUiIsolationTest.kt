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

    @Test
    fun `Trio preferences open Manage instead of insulin settings`() {
        val preferences = File(root, "ui/src/commonMain/kotlin/app/aaps/ui/compose/preferences/AllPreferencesScreen.kt").readText()
        val manageEntry = preferences.substringAfter("if (trioMode) {").substringBefore("// The extra maintenance entrypoint")
        assertThat(manageEntry).contains("onManageClick?.let")
        assertThat(manageEntry).contains("CoreUiStrings.manage")
        assertThat(preferences).doesNotContain("onInsulinSettingsClick")
        assertThat(preferences).doesNotContain("CoreUiStrings.insulin_management")

        val trioGraph = File(root, "appshell/src/commonMain/kotlin/app/aaps/appshell/navigation/TrioNavGraph.kt").readText()
        assertThat(trioGraph).contains("onManageClick = onManageClick")
        val graph = File(root, "appshell/src/commonMain/kotlin/app/aaps/appshell/navigation/AppNavGraph.kt").readText()
        val allPreferencesRoute = graph.substringAfter("composable(AppRoute.Preferences.route)").substringBefore("maintenanceViewModel?.let")
        assertThat(allPreferencesRoute).contains("onManageClick = onManageClick")
    }
}
