plugins {
    // AGP 9.0+ has built-in Kotlin support — no separate org.jetbrains.kotlin.android plugin needed.
    id("com.android.application") version "9.0.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0" apply false
}
