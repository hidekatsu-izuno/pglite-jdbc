package io.github.hidekatsu_izuno.pglite_jdbc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.wasm.types.ValueType;

public class Library {
    public static void main(String[] args) {
        var library = new Library();
        var instance = library.newInstance();
        System.out.println(instance.imports());
    }

    public Instance newInstance() {
        var store = new Store();
        store.addFunction(new HostFunction("env", "exit",
            List.of(ValueType.I32),
            List.of(),
            (Instance instance, long... args) -> {
                int exitCode = (int) args[0];
                System.out.println("WASM Process exited with code: " + exitCode);
                return null;
            }
        ));
        store.addFunction(new HostFunction("env", "invoke_i",
            List.of(ValueType.I32),
            List.of(),
            (Instance instance, long... args) -> {
                int exitCode = (int) args[0];
                System.out.println("WASM Process exited with code: " + exitCode);
                return null;
            }
        ));
        var imports = store.toImportValues();

        try (var in = Library.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            return Instance.builder(module)
                .withImportValues(imports)
                .build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
