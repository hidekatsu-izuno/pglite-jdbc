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
    implementation("com.dylibso.chicory:wasi:1.6.1")
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

tasks.withType<Test>().configureEach {
    val traceIndirect = System.getProperty("pglite.trace_call_indirect")
    if (!traceIndirect.isNullOrBlank()) {
        systemProperty("pglite.trace_call_indirect", traceIndirect)
    }
    val traceInvoke = System.getProperty("pglite.trace_invoke")
    if (!traceInvoke.isNullOrBlank()) {
        systemProperty("pglite.trace_invoke", traceInvoke)
    }
    val traceExec = System.getProperty("pglite.trace_exec")
    if (!traceExec.isNullOrBlank()) {
        systemProperty("pglite.trace_exec", traceExec)
    }
    val traceHostCalls = System.getProperty("pglite.trace_host_calls")
    if (!traceHostCalls.isNullOrBlank()) {
        systemProperty("pglite.trace_host_calls", traceHostCalls)
    }
    val traceWasmStdio = System.getProperty("pglite.trace_wasm_stdio")
    if (!traceWasmStdio.isNullOrBlank()) {
        systemProperty("pglite.trace_wasm_stdio", traceWasmStdio)
    }
}
