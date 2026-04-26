plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":data"))
    implementation(project(":platform"))
    testImplementation(kotlin("test"))
}

application {
    mainClass.set("com.tideo.autobrightness.app.MainKt")
}
