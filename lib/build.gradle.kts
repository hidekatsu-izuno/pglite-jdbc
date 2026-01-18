plugins {
    `java-library`
}

version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.dylibso.chicory:runtime:1.6.1")
    implementation("com.dylibso.chicory:wasm:1.6.1")
    implementation("org.apache.commons:commons-compress:1.28.0")

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("pglite-jdbc")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
