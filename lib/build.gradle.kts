plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.dylibso.chicory:runtime:1.1.0")
    implementation("com.dylibso.chicory:wasi:1.1.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

task("runFinalJar", JavaExec::class) {
    mainClass = "io.github.hidekatsu_izuno.pglite_jdbc.Library"
    classpath = files("build/libs/lib.jar") + java.sourceSets["main"].runtimeClasspath
    dependsOn(tasks.named("build"))
}
