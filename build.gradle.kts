// Plugins are declared once here and applied per module. AGP 9 compiles Kotlin in Android
// modules itself, so org.jetbrains.kotlin.android is not used (same as the sibling projects).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.jvm) apply false
}
