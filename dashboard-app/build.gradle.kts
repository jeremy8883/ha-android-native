// The native dashboard app: Home Assistant dashboards, rendered natively, as an app of its own installed alongside the
// official companion app. Built from :dashboard (UI and data) and :common (server, authentication, connection).
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.homeassistant.android.common)
    alias(libs.plugins.homeassistant.android.compose)
}

android {
    namespace = "net.jeremycasey.homeassistantnative"

    defaultConfig {
        applicationId = "net.jeremycasey.homeassistantnative"
        targetSdk = libs.versions.androidSdk.target.get().toInt()
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        named("debug") {
            applicationIdSuffix = ".debug"
        }
    }
}

dependencies {
    implementation(project(":common"))
    implementation(project(":dashboard"))
}
