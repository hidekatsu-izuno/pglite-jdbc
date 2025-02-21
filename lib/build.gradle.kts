import java.net.URL

plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    // api(libs.commons.math3)

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    // implementation(libs.guava)
    implementation("com.github.cretz.asmble:asmble-compiler:0.4.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named("compileJava") {
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

tasks.register<JavaExec>("convertJavaByteCode") {
    mainClass = "asmble.cli.MainKt"
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("compile", "./postgres.wasm", "io.github.hidekatsu_izuno.pglite_jdbc.Postgres")

    doFirst {
        val outFile = file("./postgres.wasm")
        if (!outFile.exists()) {
            URL("https://cdn.jsdelivr.net/npm/@electric-sql/pglite/dist/postgres.wasm")
                .openStream().use { input ->
                    outFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
        }
    }
}
