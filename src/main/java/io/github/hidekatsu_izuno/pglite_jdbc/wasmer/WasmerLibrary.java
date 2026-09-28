package io.github.hidekatsu_izuno.pglite_jdbc.wasmer;

import com.sun.jna.Callback;
import com.sun.jna.Library;
import com.sun.jna.Pointer;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmByteVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmExporttypeVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmExternVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmImporttypeVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmLimits;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmValVec;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmVal;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerTypes.WasmerNamedExternVec;

public interface WasmerLibrary extends Library {
    interface WasmFuncCallbackWithEnv extends Callback {
        Pointer invoke(Pointer env, WasmValVec.ByReference args, WasmValVec.ByReference results);
    }

    interface WasmEnvFinalizer extends Callback {
        void invoke(Pointer env);
    }

    boolean wasmer_is_headless();

    boolean wasmer_is_backend_available(int backend);

    String wasmer_version();

    Pointer wasm_engine_new();

    Pointer wasm_config_new();

    void wasm_config_set_backend(Pointer config, int backend);

    Pointer wasm_engine_new_with_config(Pointer config);

    void wasm_engine_delete(Pointer engine);

    Pointer wasm_store_new(Pointer engine);

    void wasm_store_delete(Pointer store);

    void wasm_byte_vec_new(WasmByteVec.ByReference out, long size, byte[] data);

    void wasm_byte_vec_new(WasmByteVec.ByReference out, long size, Pointer data);

    void wasm_byte_vec_delete(WasmByteVec.ByReference vec);

    Pointer wasm_module_new(Pointer store, WasmByteVec.ByReference bytes);

    Pointer wasm_module_deserialize(Pointer store, WasmByteVec.ByReference bytes);

    void wasm_module_delete(Pointer module);

    void wasm_module_imports(Pointer module, WasmImporttypeVec.ByReference out);

    void wasm_importtype_vec_delete(WasmImporttypeVec.ByReference vec);

    Pointer wasm_importtype_module(Pointer importType);

    Pointer wasm_importtype_name(Pointer importType);

    Pointer wasm_importtype_type(Pointer importType);

    void wasm_module_exports(Pointer module, WasmExporttypeVec.ByReference out);

    void wasm_exporttype_vec_delete(WasmExporttypeVec.ByReference vec);

    Pointer wasm_exporttype_name(Pointer exportType);

    Pointer wasm_exporttype_type(Pointer exportType);

    byte wasm_externtype_kind(Pointer externtype);

    Pointer wasm_externtype_as_functype_const(Pointer externtype);

    Pointer wasm_externtype_as_memorytype_const(Pointer externtype);

    Pointer wasm_externtype_as_globaltype_const(Pointer externtype);

    Pointer wasm_externtype_as_tabletype_const(Pointer externtype);

    Pointer wasm_functype_copy(Pointer functype);

    void wasm_functype_delete(Pointer functype);

    Pointer wasm_functype_params(Pointer functype);

    Pointer wasm_functype_results(Pointer functype);

    byte wasm_valtype_kind(Pointer valtype);

    Pointer wasm_valtype_new(byte kind);

    void wasm_valtype_delete(Pointer valtype);

    Pointer wasm_memorytype_limits(Pointer memorytype);

    Pointer wasm_memorytype_new(WasmLimits.ByReference limits);

    void wasm_memorytype_delete(Pointer memorytype);

    Pointer wasm_memory_new(Pointer store, Pointer memorytype);

    void wasm_memory_delete(Pointer memory);

    Pointer wasm_memory_data(Pointer memory);

    long wasm_memory_data_size(Pointer memory);

    int wasm_memory_size(Pointer memory);

    boolean wasm_memory_grow(Pointer memory, int delta);

    Pointer wasm_memory_as_extern(Pointer memory);

    Pointer wasm_extern_as_memory(Pointer extern_);

    Pointer wasm_extern_as_global(Pointer extern_);

    Pointer wasm_extern_as_table(Pointer extern_);

    Pointer wasm_global_as_extern(Pointer global);

    Pointer wasm_table_as_extern(Pointer table);

    Pointer wasm_globaltype_content(Pointer globaltype);

    byte wasm_globaltype_mutability(Pointer globaltype);

    Pointer wasm_globaltype_new(Pointer content, byte mutability);

    void wasm_globaltype_delete(Pointer globaltype);

    Pointer wasm_global_new(Pointer store, Pointer globaltype, WasmVal.ByReference value);

    void wasm_global_delete(Pointer global);

    void wasm_global_get(Pointer global, WasmVal.ByReference out);

    void wasm_global_set(Pointer global, WasmVal.ByReference value);

    Pointer wasm_tabletype_limits(Pointer tabletype);

    Pointer wasm_tabletype_element(Pointer tabletype);

    Pointer wasm_tabletype_new(Pointer element, WasmLimits.ByReference limits);

    void wasm_tabletype_delete(Pointer tabletype);

    Pointer wasm_table_new(Pointer store, Pointer tabletype, Pointer init);

    void wasm_table_delete(Pointer table);

    int wasm_table_size(Pointer table);

    boolean wasm_table_grow(Pointer table, int delta, Pointer init);

    Pointer wasm_extern_copy(Pointer extern_);
    void wasm_extern_delete(Pointer extern_);

    void wasm_ref_delete(Pointer reference);

    Pointer wasm_extern_as_func(Pointer extern_);

    Pointer wasm_func_as_extern(Pointer func);

    Pointer wasm_func_as_ref(Pointer func);

    boolean wasm_table_set(Pointer table, int index, Pointer reference);

    Pointer wasm_func_new_with_env(
        Pointer store,
        Pointer type,
        WasmFuncCallbackWithEnv callback,
        Pointer env,
        WasmEnvFinalizer finalizer
    );

    void wasm_func_delete(Pointer func);

    Pointer wasm_func_call(Pointer func, WasmValVec.ByReference args, WasmValVec.ByReference results);

    long wasm_func_param_arity(Pointer func);

    long wasm_func_result_arity(Pointer func);

    void wasm_extern_vec_new_uninitialized(WasmExternVec.ByReference out, long size);

    void wasm_extern_vec_delete(WasmExternVec.ByReference vec);

    Pointer wasm_instance_new(
        Pointer store,
        Pointer module,
        WasmExternVec.ByReference imports,
        Pointer[] trapOut
    );

    void wasm_instance_delete(Pointer instance);

    void wasm_instance_exports(Pointer instance, WasmExternVec.ByReference out);

    void wasm_trap_trace(Pointer trap, WasmExternVec.ByReference out);

    int wasm_frame_func_index(Pointer frame);

    long wasm_frame_func_offset(Pointer frame);

    void wasm_frame_vec_delete(WasmExternVec.ByReference frames);

    Pointer wasm_trap_new(Pointer store, WasmByteVec.ByReference message);

    void wasm_trap_message(Pointer trap, WasmByteVec.ByReference out);

    void wasm_trap_delete(Pointer trap);

    int wasmer_last_error_length();

    int wasmer_last_error_message(byte[] buffer, int length);

    Pointer wasi_config_new(String programName);

    void wasi_config_arg(Pointer config, String arg);

    void wasi_config_env(Pointer config, String key, String value);

    void wasi_config_inherit_stdout(Pointer config);

    void wasi_config_inherit_stderr(Pointer config);

    void wasi_config_capture_stdout(Pointer config);

    void wasi_config_capture_stderr(Pointer config);

    void wasi_config_inherit_stdin(Pointer config);

    boolean wasi_config_mapdir(Pointer config, String alias, String dir);

    boolean wasi_config_preopen_dir(Pointer config, String dir);

    Pointer wasi_env_new(Pointer store, Pointer config);

    void wasi_env_delete(Pointer env);

    boolean wasi_env_initialize_instance(Pointer wasiEnv, Pointer store, Pointer instance);

    boolean wasi_get_unordered_imports(
        Pointer wasiEnv,
        Pointer module,
        WasmerNamedExternVec.ByReference imports
    );

    void wasmer_named_extern_vec_new_empty(WasmerNamedExternVec.ByReference out);

    void wasmer_named_extern_vec_delete(WasmerNamedExternVec.ByReference vec);

    Pointer wasmer_named_extern_module(Pointer namedExtern);

    Pointer wasmer_named_extern_name(Pointer namedExtern);

    Pointer wasmer_named_extern_unwrap(Pointer namedExtern);
}
