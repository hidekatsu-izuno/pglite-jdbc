<tech_stack>
- Java 21
- Chicory 1.4.1
- Rhino 1.7.1
</tech_stack>

<project_layout>
- lib/src/main/java: Java source code
- lib/test/main/java: JUnit5 test code
</project_layout>

<commands>
- ./gradlew build: Build and run tests
</commands>

<workflow>
- Always run build after update code
- Output temporary code to ./tmp.
- Do not delete files that are not in ./tmp.
- Do not add code to your application just to pass the test.
</workflow>
