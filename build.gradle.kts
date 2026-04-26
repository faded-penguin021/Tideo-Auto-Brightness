plugins {
    kotlin("android") version "1.9.24" apply false
    kotlin("jvm") version "1.9.24" apply false
    id("com.android.application") version "8.5.2" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
