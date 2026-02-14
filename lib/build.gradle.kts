plugins {
    `java-library`
}

version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api("org.postgresql:postgresql:42.7.3")
    implementation("com.dylibso.chicory:runtime:1.6.1")
    implementation("com.dylibso.chicory:wasm:1.6.1")
    implementation("com.dylibso.chicory:wasi:1.6.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

sourceSets {
    named("main") {
        java {
            exclude("com/dylibso/chicory/runtime/InterpreterMachine.java")
        }
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

tasks.withType<Test>().configureEach {
    maxHeapSize = "2g"
    System.getProperties().stringPropertyNames()
        .asSequence()
        .filter { it.startsWith("pglite.") }
        .forEach { key ->
            val value = System.getProperty(key)
            if (!value.isNullOrBlank()) {
                systemProperty(key, value)
            }
        }
}
