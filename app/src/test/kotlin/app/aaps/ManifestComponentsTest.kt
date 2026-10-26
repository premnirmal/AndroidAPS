package app.aaps

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Every component in the app manifest must name a class that exists. An `<activity-alias>` is a
 * manifest name, not a class, so its target activity is checked instead.
 *
 * This exists because it already went wrong. Moving `CarbSuggestionReceiver` out of :plugins:aps and
 * into :app left the manifest pointing at `app.aaps.plugins.aps.loop.CarbSuggestionReceiver`, a class
 * that no longer existed, while the real receiver was declared nowhere. Android drops an explicit
 * broadcast to an undeclared receiver without an error, so the "ignore carb suggestion for 5m/15m/30m"
 * notification buttons simply stopped working, and nothing - not the build, not a test, not a lint
 * run - said a word. Moving components between modules is a normal part of the multiplatform
 * migration, so the same mistake is easy to repeat.
 *
 * Only component elements are checked. `android:name` also names permissions, intent-filter actions
 * and metadata keys, none of which are classes.
 */
class ManifestComponentsTest {

    private val componentTags = setOf("activity", "activity-alias", "service", "receiver", "provider")

    @Test
    fun launcherAliasesKeepSeparateNamesAndIcons() {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(findManifest())
        val aliases = document.getElementsByTagName("activity-alias")
        val byName = (0 until aliases.length).associate { index ->
            val alias = aliases.item(index) as Element
            alias.getAttribute("android:name") to alias
        }
        val aaps = byName.getValue("app.aaps.AndroidApsLauncher")
        val trio = byName.getValue("app.aaps.TrioLauncher")

        assertThat(aaps.getAttribute("android:enabled")).isEqualTo("true")
        assertThat(aaps.getAttribute("android:icon")).isEqualTo("\${appIcon}")
        assertThat(aaps.getAttribute("android:label")).isEqualTo("@string/app_name")
        assertThat(trio.getAttribute("android:enabled")).isEqualTo("false")
        assertThat(trio.getAttribute("android:icon")).isEqualTo("@drawable/ic_trio_launcher")
        assertThat(trio.getAttribute("android:label")).isEqualTo("@string/app_name_trio")

        for (alias in listOf(aaps, trio)) {
            assertThat(alias.getAttribute("android:exported")).isEqualTo("true")
            assertThat(alias.getAttribute("android:targetActivity")).isEqualTo("app.aaps.ComposeMainActivity")
            assertThat(alias.getElementsByTagName("action").item(0).attributes.getNamedItem("android:name").nodeValue)
                .isEqualTo("android.intent.action.MAIN")
            assertThat(alias.getElementsByTagName("category").item(0).attributes.getNamedItem("android:name").nodeValue)
                .isEqualTo("android.intent.category.LAUNCHER")
        }
        val activities = document.getElementsByTagName("activity")
        for (index in 0 until activities.length) {
            val categories = (activities.item(index) as Element).getElementsByTagName("category")
            for (category in 0 until categories.length) {
                assertThat((categories.item(category) as Element).getAttribute("android:name"))
                    .isNotEqualTo("android.intent.category.LAUNCHER")
            }
        }
    }

    @Test
    fun everyDeclaredComponentClassExists() {
        val manifest = findManifest()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(manifest)

        val missing = mutableListOf<String>()
        var checked = 0

        for (tag in componentTags) {
            val nodes = document.getElementsByTagName(tag)
            for (i in 0 until nodes.length) {
                val element = nodes.item(i) as? Element ?: continue
                val componentName = element.getAttribute("android:name").takeIf { it.isNotBlank() } ?: continue
                val className = if (tag == "activity-alias") {
                    element.getAttribute("android:targetActivity")
                } else {
                    componentName
                }
                // A leading dot is relative to the manifest package; the merger resolves it. Nothing in
                // this manifest uses that form, and resolving it here would just duplicate the merger.
                if (className.startsWith(".")) continue
                checked++
                val exists = className.isNotBlank() &&
                    runCatching { Class.forName(className, false, javaClass.classLoader) }.isSuccess
                if (!exists) missing.add("<$tag> $componentName -> $className")
            }
        }

        assertThat(checked).isGreaterThan(0)
        assertThat(missing).isEmpty()
    }

    /** Test working directories differ between Gradle and IDE runs, so walk up to find the module. */
    private fun findManifest(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val direct = File(dir, "src/main/AndroidManifest.xml")
            if (direct.isFile) return direct
            val fromRoot = File(dir, "app/src/main/AndroidManifest.xml")
            if (fromRoot.isFile) return fromRoot
            dir = dir.parentFile
        }
        error("Cannot locate the app AndroidManifest.xml from ${File("").absolutePath}")
    }
}
