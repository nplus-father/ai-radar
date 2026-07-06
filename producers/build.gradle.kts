plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "wiki.nplus.airadar.producers.MainKt"
}

dependencies {
    implementation(project(":common"))
}

dependencies {
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
