// Android side of the native dashboard: data from the server, local cache and the Compose UI.
// Pure logic lives in :dashboard-core.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.homeassistant.android.common)
    alias(libs.plugins.homeassistant.android.compose)
}

android {
    namespace = "io.homeassistant.companion.android.dashboard"
}

// Sizes come from the frontend's CSS (ui/theme/Css.kt): forbid the companion app's presets here.
detekt {
    config.from(file("detekt.yml"))
}

dependencies {
    implementation(project(":common"))
    implementation(project(":dashboard-core"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.coil.compose)
    implementation(libs.coil.okhttp)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.media3.ui.compose)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
