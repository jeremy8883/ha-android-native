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

dependencies {
    implementation(project(":common"))
    implementation(project(":dashboard-core"))

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.activity.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.markdown.renderer.m3)
    implementation(libs.coil.compose)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}
