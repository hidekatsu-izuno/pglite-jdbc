<tech_stack>
- Java 21
- Chicory 1.6.1: WASM engine
- commons-compress: alternative for tinytar
</tech_stack>

<project_layout>
- pglite/src: This Node.js programs are the original sources for the migration and must not be changed.
- lib/src/main/java: Java source code
- lib/src/test/java: JUnit5 test code
</project_layout>

<commands>
- gradle build: Build and run tests
</commands>

<conversion_hints>
- Use io.github.hidekatsu_izuno.pglite_jdbc as the root package.
- For JavaScript Web Standard classes (excluding built-in classes), create polyfills and store them under io.github.hidekatsu_izuno.pglite_jdbc.polyfills.
- Java file name must be based on the ts/js file name.
- Use java.util.concurrent.Semaphore instead of 'async-mutex'.
- Preserve TypeScript control flow and expressions as closely as possible in Java; avoid rewrites unless required by language constraints.
- Resources such as pglite.wasm and amcheck.tar.gz are placed in src/main/resources, so please retrieve them via the classpath.
</conversion_hints>

<workflow>
- Output temporary code to ./tmp. And Do not delete files that are not in ./tmp.
- Do not add code to your application just to pass the test.
- Use DataView for the pg-protocol BufferWriter buffer view.
- Use var for local variables.
</workflow>
