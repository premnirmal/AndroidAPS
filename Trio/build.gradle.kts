import java.text.SimpleDateFormat
import java.util.Date

plugins {
    alias(libs.plugins.ksp)
    alias(libs.plugins.compose.compiler)
    id("com.android.application")
    id("android-app-dependencies")
    id("test-app-dependencies")
    id("jacoco-app-dependencies")
    alias(libs.plugins.metro)
}

repositories {
    mavenCentral()
    google()
}

fun generateGitBuild(): String =
    try {
        val output = File.createTempFile("git-build", "")
        ProcessBuilder("git", "describe", "--always", "--abbrev=7", "--exclude=ios-testflight-*")
            .redirectOutput(output)
            .start()
            .waitFor()
        output.readText().trim()
    } catch (_: Exception) {
        "NoGitSystemAvailable"
    }

fun generateGitRemote(): String =
    try {
        val output = File.createTempFile("git-remote", "")
        ProcessBuilder("git", "remote", "get-url", "origin")
            .redirectOutput(output)
            .start()
            .waitFor()
        output.readText().trim()
    } catch (_: Exception) {
        "NoGitSystemAvailable"
    }

fun generateDate(): String = SimpleDateFormat("yyyy.MM.dd").format(Date())

fun allCommitted(): Boolean =
    try {
        val output = File.createTempFile("git-committed", "")
        ProcessBuilder("git", "status", "-s")
            .redirectOutput(output)
            .start()
            .waitFor()
        output.readText()
            .replace(Regex("""(?m)^\s*(M|A|D|\?\?)\s*.*?\.idea\/codeStyles\/.*?\s*$"""), "")
            .replace(Regex("""(?m)^\s*(\?\?)\s*.*?\s*$"""), "")
            .trim()
            .isEmpty()
    } catch (_: Exception) {
        false
    }

val generateTrioStringOwners = tasks.register<GenerateStringOwnerRegistryTask>("generateTrioStringOwners") {
    owners.set(StringOwnerModules.ALL)
    packageName.set("app.aaps.di")
    objectName.set("GeneratedStringOwners")
    useResourceIds.set(true)
    outputDir.set(layout.buildDirectory.dir("generated/stringOwners"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addGeneratedSourceDirectory(generateTrioStringOwners, GenerateStringOwnerRegistryTask::outputDir)
    }
}

android {
    namespace = "app.aaps"

    defaultConfig {
        applicationId = "info.nightscout.trio"
        minSdk = Versions.minSdk
        targetSdk = Versions.targetSdk
        versionName = Versions.appVersion + "-trio"

        resValue("string", "app_name", "Trio")
        manifestPlaceholders["appIcon"] = "@mipmap/ic_launcher"
        manifestPlaceholders["appIconRound"] = "@mipmap/ic_launcher_round"

        buildConfigField("String", "VERSION", "\"$version\"")
        buildConfigField("String", "BUILDVERSION", "\"${generateGitBuild()}-${generateDate()}\"")
        buildConfigField("String", "REMOTE", "\"${generateGitRemote()}\"")
        buildConfigField("String", "HEAD", "\"${generateGitBuild()}\"")
        buildConfigField("String", "COMMITTED", "\"${allCommitted()}\"")
        buildConfigField("boolean", "TRIO", "true")
        buildConfigField("boolean", "FIREBASE_ENABLED", "false")

        testInstrumentationRunner = "app.aaps.runners.AapsTestRunner"
    }

    useLibrary("org.apache.http.legacy")

    buildFeatures {
        buildConfig = true
        compose = true
        resValues = true
    }

    flavorDimensions += "standard"
    productFlavors {
        create("trio") {
            isDefault = true
            dimension = "standard"
        }
    }

    sourceSets {
        getByName("main") {
            manifest.srcFile("../app/src/main/AndroidManifest.xml")
            kotlin.directories.addAll(
                listOf(
                    "../app/src/main/kotlin",
                    "../app/src/aaps/kotlin",
                    "../app/src/withPumps/kotlin",
                    "src/main/kotlin"
                )
            )
            res.directories.addAll(listOf("../app/src/main/res", "src/main/res"))
        }
        getByName("debug") { kotlin.directories.add("../app/src/debug/kotlin") }
        getByName("release") { kotlin.directories.add("../app/src/release/kotlin") }
    }
}

dependencies {
    implementation(project(":shared:impl"))
    implementation(project(":core:data"))
    implementation(project(":core:objects"))
    implementation(project(":core:interfaces"))
    implementation(project(":core:keys"))
    implementation(project(":core:nssdk"))
    implementation(project(":core:utils"))
    implementation(project(":core:ui"))
    implementation(project(":ui"))
    implementation(project(":appshell"))
    implementation(project(":implementation"))
    implementation(project(":database:impl"))
    implementation(project(":database:persistence"))
    implementation(project(":pump:virtual"))
    implementation(project(":workflow"))

    val pumpExclusions = setOf(":pump:virtual", ":pump:combov2:comboctl")
    rootProject.subprojects
        .filter { it.path.startsWith(":pump:") && it.path !in pumpExclusions && it.buildFile.exists() }
        .forEach { implementation(project(it.path)) }

    implementation(libs.androidx.core)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.com.uber.rxdogtag2.rxdogtag)
    implementation(libs.com.google.firebase.config)
    implementation(libs.androidx.compose.navigation)

    testImplementation(project(":shared:tests"))
    androidTestImplementation(project(":shared:tests"))
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.uiautomator)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.org.skyscreamer.jsonassert)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.org.mozilla.rhino)

    debugImplementation(libs.com.squareup.leakcanary.android)
}
