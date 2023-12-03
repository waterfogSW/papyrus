import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
    kotlin("plugin.jpa") version "1.9.21"

}
group = "com.example"

version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web:3.2.0")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa:3.2.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.14.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // coroutine
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0-RC")

    // development support
    developmentOnly("org.springframework.boot:spring-boot-devtools:3.2.0")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose:3.2.0")

    // mysql
    runtimeOnly("com.mysql:mysql-connector-j:8.0.32")

    // kotest
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.0")

    // mockk
    testImplementation("io.mockk:mockk:1.13.8")
    testImplementation("com.ninja-squad:springmockk:4.0.2")

    // test containers
    testImplementation("org.springframework.boot:spring-boot-testcontainers:3.2.0")
    testImplementation("org.testcontainers:junit-jupiter:1.17.6")
    testImplementation("org.testcontainers:mysql:1.17.6")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
