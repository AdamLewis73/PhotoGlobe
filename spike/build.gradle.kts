// Versions pinned to what is already in the local Gradle cache on this machine,
// so the build does not depend on resolving anything new.
plugins {
    id("com.android.application") version "8.11.0" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
}
