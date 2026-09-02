plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.marcusrosado.skemafordtu"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.marcusrosado.skemafordtu"
        minSdk = 26
        targetSdk = 37
        // Bumped on every build that leaves this machine, without exception.
        // Two debug builds shipped as versionCode 1, so Android saw them as
        // the same app and an install was a silent no-op unless it happened to
        // replace the file. versionName is what the owner reads; versionCode
        // is the only number Android compares.
        versionCode = 10
        versionName = "0.7.0"
    }

    buildFeatures { compose = true }

    // The UI tests run on the host JVM. They touch Compose's plain value
    // classes, Dp and Color, and nothing that needs a device; the framework
    // stubs answer zero for anything that does.
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlinx.coroutines.core)

    val bom = platform(libs.compose.bom)
    implementation(bom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.windowsize)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    // Icon packs, published as Compose ImageVectors, so no drawable folder.
    implementation(libs.compose.icons.lucide)
    implementation(libs.compose.icons.fontawesome)

    // test-junit, not test: a plain Android module does not pick the JUnit
    // binding on its own the way a multiplatform one does.
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.junit)
}
