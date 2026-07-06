plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass = "wiki.nplus.airadar.publisher.MainKt"
}

dependencies {
    implementation(project(":common"))

    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
