plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.serialization)
}

kotlin {
    jvm()
    android {
        namespace = "dk.bandit.skema.shared"
        compileSdk = 37
        minSdk = 26
    }
    // iOS needs a Mac to link, so it is left out. To add it back later:
    //   listOf(iosArm64(), iosSimulatorArm64()).forEach {
    //       it.binaries.framework { baseName = "Shared"; isStatic = true }
    //   }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
    }
}

// Lets the real-feed check find a feed without committing one:
//   ./gradlew :shared:jvmTest -Dskema.feed=/path/to/feed.ics --info
val skemaFeed = providers.systemProperty("skema.feed")
tasks.withType<Test>().configureEach {
    if (skemaFeed.isPresent) systemProperty("skema.feed", skemaFeed.get())
    testLogging { showStandardStreams = true }
}
