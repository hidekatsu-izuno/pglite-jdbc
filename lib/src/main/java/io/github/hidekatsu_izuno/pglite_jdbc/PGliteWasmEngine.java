package io.github.hidekatsu_izuno.pglite_jdbc;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.dylibso.chicory.wasm.Parser;
import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.Store;
import com.dylibso.chicory.runtime.HostFunction;
import com.dylibso.chicory.runtime.Memory;
import com.dylibso.chicory.wasm.types.ValueType;

public class PGliteWasmEngine {
    private static final int WASI_ERRNO_SUCCESS = 0;
    private static final int WASI_ERRNO_BADF = 8;
    private static final int WASI_ERRNO_INVAL = 28;
    private static final int WASI_ERRNO_NOSYS = 52;
    
    private Instance instance;
    private Memory memory;
    
    public Instance getInstance() {
        if (instance == null) {
            instance = createInstance();
        }
        return instance;
    }
    
    private Instance createInstance() {
        var store = new Store();
        
        // Add minimal host functions
        addMinimalHostFunctions(store);
        
        var imports = store.toImportValues();
        
        try (var in = PGliteWasmEngine.class.getResourceAsStream("/postgres.wasm")) {
            var module = Parser.parse(in);
            var inst = Instance.builder(module)
                .withImportValues(imports)
                .build();
            
            this.memory = inst.memory();
            
            // Call constructors
            var ctors = inst.export("__wasm_call_ctors");
            if (ctors != null) {
                ctors.apply();
            }
            
            return inst;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private void addMinimalHostFunctions(Store store) {
        // exit
        store.addFunction(new HostFunction("env", "exit",
            List.of(ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> {
                int exitCode = (int) args[0];
                System.out.println("WASM Process exited with code: " + exitCode);
                return null;
            }
        ));
        
        // Basic invoke functions - just stubs for now
        addInvokeStubs(store);
        
        // WASI stubs
        addWasiStubs(store);
        
        // Emscripten stubs
        addEmscriptenStubs(store);
        
        // System call stubs
        addSystemStubs(store);
    }
    
    private void addInvokeStubs(Store store) {
        // The invoke functions are trampolines for function table calls
        // The name encodes the signature: invoke_<return><params>
        // where v=void, i=i32, j=i64, d=f64
        
        // invoke_v (i32 funcPtr) -> void
        store.addFunction(new HostFunction("env", "invoke_v",
            List.of(ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_i (i32 funcPtr) -> i32
        store.addFunction(new HostFunction("env", "invoke_i",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_ii (i32 funcPtr, i32 arg1) -> i32
        store.addFunction(new HostFunction("env", "invoke_ii",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iii (i32 funcPtr, i32 arg1, i32 arg2) -> i32
        store.addFunction(new HostFunction("env", "invoke_iii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4, i32 arg5) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_vi (i32 funcPtr, i32 arg1) -> void
        store.addFunction(new HostFunction("env", "invoke_vi",
            List.of(ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_vii (i32 funcPtr, i32 arg1, i32 arg2) -> void
        store.addFunction(new HostFunction("env", "invoke_vii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3) -> void
        store.addFunction(new HostFunction("env", "invoke_viii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4) -> void
        store.addFunction(new HostFunction("env", "invoke_viiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4, i32 arg5) -> void
        store.addFunction(new HostFunction("env", "invoke_viiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiiiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4, i32 arg5, i32 arg6) -> void
        store.addFunction(new HostFunction("env", "invoke_viiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiiiiii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4, i32 arg5, i32 arg6, i32 arg7) -> void
        store.addFunction(new HostFunction("env", "invoke_viiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiiiiiii (i32 funcPtr, 8 i32 args) -> void
        store.addFunction(new HostFunction("env", "invoke_viiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiiiiiiii (i32 funcPtr, 9 i32 args) -> void
        store.addFunction(new HostFunction("env", "invoke_viiiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiiiiiiiiiii (i32 funcPtr, 12 i32 args) -> void
        store.addFunction(new HostFunction("env", "invoke_viiiiiiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // i64 return functions
        // invoke_j (i32 funcPtr) -> i64
        store.addFunction(new HostFunction("env", "invoke_j",
            List.of(ValueType.I32),
            List.of(ValueType.I64),
            (Instance inst, long... args) -> new long[]{0L}
        ));
        
        // invoke_ji (i32 funcPtr, i32 arg1) -> i64
        store.addFunction(new HostFunction("env", "invoke_ji",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I64),
            (Instance inst, long... args) -> new long[]{0L}
        ));
        
        // invoke_jii (i32 funcPtr, i32 arg1, i32 arg2) -> i64
        store.addFunction(new HostFunction("env", "invoke_jii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I64),
            (Instance inst, long... args) -> new long[]{0L}
        ));
        
        // i64 parameter functions
        // invoke_ij (i32 funcPtr, i64 arg1) -> i32
        store.addFunction(new HostFunction("env", "invoke_ij",
            List.of(ValueType.I32, ValueType.I64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_vj (i32 funcPtr, i64 arg1) -> void
        store.addFunction(new HostFunction("env", "invoke_vj",
            List.of(ValueType.I32, ValueType.I64),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_vij (i32 funcPtr, i32 arg1, i64 arg2) -> void
        store.addFunction(new HostFunction("env", "invoke_vij",
            List.of(ValueType.I32, ValueType.I32, ValueType.I64),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_vji (i32 funcPtr, i64 arg1, i32 arg2) -> void
        store.addFunction(new HostFunction("env", "invoke_vji",
            List.of(ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viij (i32 funcPtr, i32 arg1, i32 arg2, i64 arg3) -> void
        store.addFunction(new HostFunction("env", "invoke_viij",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viji (i32 funcPtr, i32 arg1, i64 arg2, i32 arg3) -> void
        store.addFunction(new HostFunction("env", "invoke_viji",
            List.of(ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiji (i32 funcPtr, i32 arg1, i32 arg2, i64 arg3, i32 arg4) -> void
        store.addFunction(new HostFunction("env", "invoke_viiji",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viijii (i32 funcPtr, i32 arg1, i32 arg2, i64 arg3, i32 arg4, i32 arg5) -> void
        store.addFunction(new HostFunction("env", "invoke_viijii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viiij (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i64 arg4) -> void
        store.addFunction(new HostFunction("env", "invoke_viiij",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_vijiji (i32 funcPtr, i32 arg1, i64 arg2, i32 arg3, i64 arg4, i32 arg5) -> void
        store.addFunction(new HostFunction("env", "invoke_vijiji",
            List.of(ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // invoke_viijiiii (i32 funcPtr, i32 arg1, i32 arg2, i64 arg3, 4 i32 args) -> void
        store.addFunction(new HostFunction("env", "invoke_viijiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        // More i64 return/param combinations
        // invoke_iiij (i32 funcPtr, i32 arg1, i32 arg2, i64 arg3) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiij",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiji (i32 funcPtr, i32 arg1, i64 arg2, i32 arg3) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiji",
            List.of(ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiij (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i64 arg4) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiij",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiijii (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i64 arg4, i32 arg5, i32 arg6) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiijii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiiiji (i32 funcPtr, i32 arg1, i32 arg2, i32 arg3, i32 arg4, i64 arg5, i32 arg6) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiiji",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_ijiiiii (i32 funcPtr, i64 arg1, 5 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_ijiiiii",
            List.of(ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_ijiiiiii (i32 funcPtr, i64 arg1, 6 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_ijiiiiii",
            List.of(ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // More i32 return functions
        // invoke_iiiiiii (i32 funcPtr, 6 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiiiiii (i32 funcPtr, 7 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiiiiiii (i32 funcPtr, 8 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiiiiiiii (i32 funcPtr, 9 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_iiiiiiiiiiiiiiiii (i32 funcPtr, 16 i32 args) -> i32
        store.addFunction(new HostFunction("env", "invoke_iiiiiiiiiiiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // More i64 return functions
        // invoke_jiiii (i32 funcPtr, 4 i32 args) -> i64
        store.addFunction(new HostFunction("env", "invoke_jiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I64),
            (Instance inst, long... args) -> new long[]{0L}
        ));
        
        // invoke_jiiiii (i32 funcPtr, 5 i32 args) -> i64
        store.addFunction(new HostFunction("env", "invoke_jiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I64),
            (Instance inst, long... args) -> new long[]{0L}
        ));
        
        // invoke_jiiiiiiii (i32 funcPtr, 8 i32 args) -> i64
        store.addFunction(new HostFunction("env", "invoke_jiiiiiiii",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I64),
            (Instance inst, long... args) -> new long[]{0L}
        ));
        
        // Floating point invoke functions
        // invoke_di (i32 funcPtr, i32 arg1) -> f64
        store.addFunction(new HostFunction("env", "invoke_di",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.F64),
            (Instance inst, long... args) -> new long[]{Double.doubleToRawLongBits(0.0)}
        ));
        
        // invoke_id (i32 funcPtr, f64 arg1) -> i32
        store.addFunction(new HostFunction("env", "invoke_id",
            List.of(ValueType.I32, ValueType.F64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // invoke_vid (i32 funcPtr, i32 arg1, f64 arg2) -> void
        store.addFunction(new HostFunction("env", "invoke_vid",
            List.of(ValueType.I32, ValueType.I32, ValueType.F64),
            List.of(),
            (Instance inst, long... args) -> null
        ));
    }
    
    private void addWasiStubs(Store store) {
        // WASI functions - minimal stubs
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "environ_sizes_get",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "environ_get",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_close",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_read",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_write",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "proc_exit",
            List.of(ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> {
                System.out.println("WASI proc_exit called with code: " + args[0]);
                return null;
            }
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "clock_time_get",
            List.of(ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> {
                // Write current time in nanoseconds
                long nanos = System.nanoTime();
                int ptr = (int) args[2];
                var mem = inst.memory();
                // Write 64-bit time
                for (int i = 0; i < 8; i++) {
                    mem.write(ptr + i, new byte[]{(byte) (nanos >> (i * 8))});
                }
                return new long[]{0};
            }
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_sync",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_fdstat_get",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_seek",
            List.of(ValueType.I32, ValueType.I64, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_pread",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("wasi_snapshot_preview1", "fd_pwrite",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
    }
    
    private void addEmscriptenStubs(Store store) {
        // Emscripten functions
        store.addFunction(new HostFunction("env", "emscripten_force_exit",
            List.of(ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> {
                System.out.println("emscripten_force_exit called");
                return null;
            }
        ));
        
        store.addFunction(new HostFunction("env", "emscripten_get_now",
            List.of(),
            List.of(ValueType.F64),
            (Instance inst, long... args) -> {
                double now = System.currentTimeMillis();
                return new long[]{Double.doubleToRawLongBits(now)};
            }
        ));
        
        store.addFunction(new HostFunction("env", "emscripten_date_now",
            List.of(),
            List.of(ValueType.F64),
            (Instance inst, long... args) -> {
                double now = System.currentTimeMillis();
                return new long[]{Double.doubleToRawLongBits(now)};
            }
        ));
        
        store.addFunction(new HostFunction("env", "emscripten_resize_heap",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("env", "_emscripten_memcpy_js",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "emscripten_asm_const_int",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("env", "emscripten_set_main_loop",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_emscripten_runtime_keepalive_clear",
            List.of(),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_emscripten_throw_longjmp",
            List.of(),
            List.of(),
            (Instance inst, long... args) -> {
                throw new RuntimeException("longjmp not supported");
            }
        ));
        
        store.addFunction(new HostFunction("env", "_emscripten_system",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-1}
        ));
    }
    
    private void addSystemStubs(Store store) {
        // System functions
        store.addFunction(new HostFunction("env", "__assert_fail",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> {
                throw new RuntimeException("Assertion failed in WASM");
            }
        ));
        
        store.addFunction(new HostFunction("env", "__call_sighandler",
            List.of(ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_abort_js",
            List.of(),
            List.of(),
            (Instance inst, long... args) -> {
                throw new RuntimeException("WASM aborted");
            }
        ));
        
        store.addFunction(new HostFunction("env", "is_web_env",
            List.of(),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("env", "getaddrinfo",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-1}
        ));
        
        store.addFunction(new HostFunction("env", "getnameinfo",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-1}
        ));
        
        // TODO: Add required global, memory, and table imports later
        
        // Add syscall stubs with specific signatures
        
        // fcntl64: (fd: i32, cmd: i32, arg: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_fcntl64",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // ioctl: (fd: i32, request: i32, argp: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_ioctl",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // openat: (dirfd: i32, pathname: i32, flags: i32, mode: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_openat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_BADF}
        ));
        
        // fstat64: (fd: i32, statbuf: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_fstat64",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_BADF}
        ));
        
        // stat64: (pathname: i32, statbuf: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_stat64",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // newfstatat: (dirfd: i32, pathname: i32, statbuf: i32, flags: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_newfstatat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // lstat64: (pathname: i32, statbuf: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_lstat64",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // ftruncate64: (fd: i32, length: i64) -> i32
        store.addFunction(new HostFunction("env", "__syscall_ftruncate64",
            List.of(ValueType.I32, ValueType.I64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // truncate64: (pathname: i32, length: i64) -> i32
        store.addFunction(new HostFunction("env", "__syscall_truncate64",
            List.of(ValueType.I32, ValueType.I64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // fadvise64: (fd: i32, offset: i64, len: i64, advice: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_fadvise64",
            List.of(ValueType.I32, ValueType.I64, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // fallocate: (fd: i32, mode: i32, offset: i64, len: i64) -> i32
        store.addFunction(new HostFunction("env", "__syscall_fallocate",
            List.of(ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I64),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // Specific syscall signatures based on actual requirements
        
        // faccessat: (dirfd: i32, pathname: i32, mode: i32, flags: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_faccessat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // chdir: (path: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_chdir",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // chmod: (pathname: i32, mode: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_chmod",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // dup: (oldfd: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_dup",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_BADF}
        ));
        
        // dup3: (oldfd: i32, newfd: i32, flags: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_dup3",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_BADF}
        ));
        
        // fdatasync: (fd: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_fdatasync",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        // getcwd: (buf: i32, size: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_getcwd",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // mkdirat: (dirfd: i32, pathname: i32, mode: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_mkdirat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // pipe: (pipefd: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_pipe",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // poll: (fds: i32, nfds: i32, timeout: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_poll",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // getdents64: (fd: i32, dirp: i32, count: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_getdents64",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // readlinkat: (dirfd: i32, pathname: i32, buf: i32, bufsiz: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_readlinkat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // unlinkat: (dirfd: i32, pathname: i32, flags: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_unlinkat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // rmdir: (pathname: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_rmdir",
            List.of(ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // renameat: (olddirfd: i32, oldpath: i32, newdirfd: i32, newpath: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_renameat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // _newselect: (nfds: i32, readfds: i32, writefds: i32, exceptfds: i32, timeout: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall__newselect",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // symlinkat: (target: i32, newdirfd: i32, linkpath: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_symlinkat",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // Socket syscalls
        // bind: (sockfd: i32, addr: i32, addrlen: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_bind",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // connect: (sockfd: i32, addr: i32, addrlen: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_connect",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // getsockname: (sockfd: i32, addr: i32, addrlen: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_getsockname",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // getsockopt: (sockfd: i32, level: i32, optname: i32, optval: i32, optlen: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_getsockopt",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // recvfrom: (sockfd: i32, buf: i32, len: i32, flags: i32, src_addr: i32, addrlen: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_recvfrom",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // sendto: (sockfd: i32, buf: i32, len: i32, flags: i32, dest_addr: i32, addrlen: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_sendto",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // socket: (domain: i32, type: i32, protocol: i32) -> i32
        store.addFunction(new HostFunction("env", "__syscall_socket",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-WASI_ERRNO_NOSYS}
        ));
        
        // Time functions
        store.addFunction(new HostFunction("env", "_tzset_js",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_localtime_js",
            List.of(ValueType.I64, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_gmtime_js",
            List.of(ValueType.I64, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_setitimer_js",
            List.of(ValueType.I32, ValueType.F64, ValueType.F64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-1}
        ));
        
        // File/memory functions
        store.addFunction(new HostFunction("env", "_dlopen_js",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_dlsym_js",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32),
            List.of(),
            (Instance inst, long... args) -> null
        ));
        
        store.addFunction(new HostFunction("env", "_munmap_js",
            List.of(ValueType.I32, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{0}
        ));
        
        store.addFunction(new HostFunction("env", "_mmap_js",
            List.of(ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I32, ValueType.I64, ValueType.I32),
            List.of(ValueType.I32),
            (Instance inst, long... args) -> new long[]{-1}
        ));
    }
    
    // Utility methods for working with WASM memory
    
    public String readString(int ptr) {
        if (memory == null) {
            throw new IllegalStateException("WASM instance not initialized");
        }
        
        var bytes = new java.util.ArrayList<Byte>();
        int offset = ptr;
        
        while (true) {
            byte b = memory.read(offset++);
            if (b == 0) break;
            bytes.add(b);
        }
        
        byte[] byteArray = new byte[bytes.size()];
        for (int i = 0; i < bytes.size(); i++) {
            byteArray[i] = bytes.get(i);
        }
        
        return new String(byteArray, StandardCharsets.UTF_8);
    }
    
    public int writeString(String str) {
        if (memory == null) {
            throw new IllegalStateException("WASM instance not initialized");
        }
        
        byte[] bytes = str.getBytes(StandardCharsets.UTF_8);
        
        // Allocate memory using malloc
        var malloc = instance.export("malloc");
        if (malloc == null) {
            throw new RuntimeException("malloc not found in WASM exports");
        }
        
        int ptr = (int) malloc.apply(bytes.length + 1)[0];
        
        // Write string to memory
        for (int i = 0; i < bytes.length; i++) {
            memory.write(ptr + i, new byte[]{bytes[i]});
        }
        memory.write(ptr + bytes.length, new byte[]{0}); // null terminator
        
        return ptr;
    }
    
    public void freeString(int ptr) {
        if (ptr == 0) return;
        
        var free = instance.export("free");
        if (free != null) {
            free.apply(ptr);
        }
    }
    
    public static void main(String[] args) {
        var engine = new PGliteWasmEngine();
        var instance = engine.getInstance();
        System.out.println("WASM instance created successfully!");
        System.out.println("WASM exports available including malloc and free");
    }
}