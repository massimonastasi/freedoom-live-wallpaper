// ponytail: pinned to AGP 8.9 / Kotlin 2.0.21. AGP 9.x compiles fine, but its Maven-fetched
// aapt2 is blocked by Smart App Control on this machine (a binary with no reputation yet).
// Upgrade to AGP 9.x once that binary is trusted, or before publishing.
plugins {
    id("com.android.application") version "8.9.0" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
}
