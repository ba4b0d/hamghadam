// Top-level build file: plugin versions are declared here (applied false) and
// resolved per-module via the version catalog (gradle/libs.versions.toml).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.google.gms.google.services) apply false
}
