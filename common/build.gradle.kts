plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines)
    api(libs.kotlinx.serialization.json)
    api(libs.amqp.client)
    api(libs.hikari)
    api(libs.logback.classic)
    api(libs.micrometer.prometheus)
    runtimeOnly(libs.postgresql)

    testImplementation(libs.kotlin.test)
    // The SQL tests run against a real Postgres — every query in ItemRepository
    // is raw JDBC against a schema the compiler cannot see, so a mock would only
    // assert that the string is the string we wrote.
    testImplementation(libs.testcontainers.postgresql)
    testRuntimeOnly(libs.postgresql)
}

tasks.test {
    useJUnitPlatform()
    // No Docker (some CI runners, an offline laptop) means the SQL tests skip
    // rather than fail; see PostgresFixture.
    systemProperty("java.awt.headless", "true")
}
