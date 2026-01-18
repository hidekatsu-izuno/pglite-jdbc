<tech_stack>
- Java 21
- Chicory 1.4.1: WASM engine
- commons-compress: alternative for tinytar
</tech_stack>

<project_layout>
- pglite/src: This Node.js programs are the original sources for the migration and must not be changed.
- lib/src/main/java: Java source code
- lib/test/main/java: JUnit5 test code
</project_layout>

<commands>
- gradle build: Build and run tests
</commands>

<conversion_hints>
- Use io.github.hidekatsu_izuno.pglite_jdbc as the root package.
- Java file name must be a upper camel case based on the ts/js file name.
- Use java.util.concurrent.Semaphore instead of 'async-mutex'.
- Preserve TypeScript control flow and expressions as closely as possible in Java; avoid rewrites unless required by language constraints.
</conversion_hints>

<workflow>
- Output temporary code to ./tmp. And Do not delete files that are not in ./tmp.
- Do not add code to your application just to pass the test.
- Keep pg-protocol buffer reader/writer TypeScript filenames in PascalCase (BufferReader.ts/BufferWriter.ts).
- Use DataView for the pg-protocol BufferWriter buffer view.
- Use var for local variables.
</workflow>
