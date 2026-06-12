plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    // Forward the golden-vector regeneration flag to the test JVM (see GoldenVectorGenerator).
    System.getProperty("regenGolden")?.let { systemProperty("regenGolden", it) }
}
