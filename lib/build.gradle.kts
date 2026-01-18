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
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter("5.12.1")
        }
    }
}

tasks.named<Jar>("jar") {
    archiveBaseName.set("pglite-jdbc")
}
