plugins {
    `java-library`
}

version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    api("org.postgresql:postgresql:42.7.3")
    implementation("net.java.dev.jna:jna:5.18.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
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
