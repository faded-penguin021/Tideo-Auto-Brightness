plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":domain"))
    testImplementation(kotlin("test"))
}
