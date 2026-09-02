plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
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
        versionCode = 4
        versionName = "0.4.0"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
}
