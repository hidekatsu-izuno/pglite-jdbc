# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

PGlite-JDBC is a Java library that provides a JDBC interface for PGlite (a WebAssembly-based PostgreSQL). The project uses Chicory as the WASM runtime to execute postgres.wasm within the JVM. The JDBC interface is designed to be compatible with pgjdbc.

## Build Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "io.github.hidekatsu_izuno.pglite_jdbc.PGliteJdbcTest"

# Clean build artifacts
./gradlew clean

# Generate JAR file
./gradlew jar
```

## Architecture

The project follows a simple architecture:

1. **WASM Integration**: The core functionality is in `PGliteWasmEngine.java` which loads and instantiates `postgres.wasm` using the Chicory runtime. The WASM module is embedded as a resource in `/lib/src/main/resources/postgres.wasm`.

2. **JDBC Compatibility**: The project depends on `org.postgresql:postgresql:42.7.7` to provide JDBC interface compatibility with pgjdbc.

3. **Host Functions**: The Library class implements host functions required by the WASM module:
   - `env.exit`: Handles WASM process termination
   - `env.invoke_i`: Additional WASM interface function

## Key Files

- `lib/src/main/java/io/github/hidekatsu_izuno/pglite_jdbc/PGliteWasmEngine.java`: Sample program that handles WASM instantiation
- `lib/src/main/resources/postgres.wasm`: The PostgreSQL WebAssembly module
- `lib/build.gradle.kts`: Build configuration with dependencies and Java 21 toolchain

## Dependencies

- [Chicory](https://github.com/dylibso/chicory): Wasm Runtime Engine

## Development Notes

- The project uses Java 21 as specified in the toolchain configuration
- JUnit 5 (Jupiter) is used for testing
- The JAR artifact is named `pglite-jdbc` as configured in the build script

## Development Policies

- Create any temporary files under tmp/ in the workspace.