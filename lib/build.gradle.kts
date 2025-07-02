plugins {
    `java-library`
}

version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.postgresql:postgresql:42.7.7")
    implementation("com.dylibso.chicory:runtime:1.4.1")
    implementation("com.dylibso.chicory:wasi:1.4.1")

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

tasks.register<JavaExec>("inspectWasm") {
    group = "application"
    description = "Inspect postgres.wasm exports"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.hidekatsu_izuno.pglite_jdbc.WasmInspector")
}

tasks.register<JavaExec>("debugWasm") {
    group = "application"
    description = "Debug WASM function calls"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("io.github.hidekatsu_izuno.pglite_jdbc.WasmDebugger")
}
