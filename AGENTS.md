<tech_stack>
- Java 21
- Chicory 1.4.1: WASM engine
- commons-compress: alternative for tinytar
</tech_stack>

<project_layout>
- lib/src/main/java: Java source code
- lib/test/main/java: JUnit5 test code
</project_layout>

<commands>
- gradle build: Build and run tests
</commands>

<conversion_hints>
- Use io.github.hidekatsu_izuno.pglite_jdbc as the root package.
- Use java.util.concurrent.Semaphore instead of 'async-mutex'.
</conversion_hints>

<workflow>
- Output temporary code to ./tmp. And Do not delete files that are not in ./tmp.
- Do not add code to your application just to pass the test.
</workflow>
