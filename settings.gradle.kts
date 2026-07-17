plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "bookshelf-echo"

include("common", "producers", "enricher", "matcher", "digester", "publisher", "ops")
