package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.wasm;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class dylib {
    public interface DynamicLibrarySymbolTable {
        void register(String name, Object value);

        Object resolve(String name);

        boolean has(String name);
    }

    private dylib() {}

    public static DynamicLibrarySymbolTable createDynamicLibrarySymbolTable() {
        Map<String, Object> symbols = new ConcurrentHashMap<>();
        return new DynamicLibrarySymbolTable() {
            @Override
            public void register(String name, Object value) {
                symbols.put(name, value);
            }

            @Override
            public Object resolve(String name) {
                if (!symbols.containsKey(name)) {
                    throw new IllegalArgumentException("Undefined dynamic library symbol: " + name);
                }
                return symbols.get(name);
            }

            @Override
            public boolean has(String name) {
                return symbols.containsKey(name);
            }
        };
    }
}
