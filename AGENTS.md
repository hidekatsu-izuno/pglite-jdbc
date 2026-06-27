<tech_stack>
- Java 21
- Chricory: WASM engine
- commons-compress: alternative for tinytar
</tech_stack>

<project_layout>
- src/main/java: Java source code
- src/test/java: JUnit5 test code
</project_layout>

<commands>
- mise x -- mvn compile: Build
</commands>

<conversion_hints>
- Use io.github.hidekatsu_izuno.pglite_jdbc as the root package.
- For JavaScript Web Standard classes (excluding built-in classes), create polyfills and store them under io.github.hidekatsu_izuno.pglite_jdbc.polyfills.
- Java file name must be based on the ts/js/java file name.
- Use java.util.concurrent.Semaphore instead of 'async-mutex'.
- Preserve TypeScript control flow and expressions as closely as possible in Java; avoid rewrites unless required by language constraints.
- Resources such as pglite.wasm and amcheck.tar.gz are placed in src/main/resources, so please retrieve them via the classpath.
- Do not use asynchronous execution for code implemented with Promises if the logic can be implemented synchronously in Java.
</conversion_hints>

<workflow>
- Do not add code to your application just to pass the test.
- Do not use simplified or fallback implementations, as they prevent proper evaluation of the final result.
- Use DataView for the pg-protocol BufferWriter buffer view.
- Use var for local variables.
- Remove server-oriented features (network-facing worker/server behavior) from the Java migration target.
- Remove browser-specific features, and keep corresponding source TypeScript snippets as commented references in Java files when removed.
</workflow>
