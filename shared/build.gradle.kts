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
        namespace = "com.marcusrosado.skemafordtu.shared"
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
            implementation(libs.compose.icons.lucide)
            implementation(libs.compose.icons.fontawesome)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            // Only for BackHandler: the system back gesture is an Android
            // concept and Compose Multiplatform 1.12 has no common one.
            implementation(libs.androidx.activity.compose)
        }
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
