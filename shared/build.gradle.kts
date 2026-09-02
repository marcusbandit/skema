plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.serialization)
}

// Data and logic only: the ICS parser, merging, terms, the repository, the
// settings and the session. The UI is native Android and lives in androidApp.
// The jvm target stays so the parser tests run on the host without an
// emulator, and because the real feed check reads a file off the host.
kotlin {
    jvm()
    android {
        namespace = "com.marcusrosado.skemafordtu.shared"
        compileSdk = 37
        minSdk = 26
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            // api, not implementation: every event carries a LocalDateTime,
            // so anyone holding an event needs the type.
            api(libs.kotlinx.datetime)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies { implementation(kotlin("test")) }
        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
    }
}

// Lets the real-feed check find a feed without committing one:
//   scripts/build.sh :shared:jvmTest -Dskema.feed=/path/to/feed.ics --info
val skemaFeed = providers.systemProperty("skema.feed")
tasks.withType<Test>().configureEach {
    if (skemaFeed.isPresent) systemProperty("skema.feed", skemaFeed.get())
    testLogging { showStandardStreams = true }
}
