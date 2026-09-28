package io.github.hidekatsu_izuno.pglite_jdbc.wasmer;

import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.Union;

public final class WasmerTypes {
    public static final byte WASM_I32 = 0;
    public static final byte WASM_I64 = 1;
    public static final byte WASM_F32 = 2;
    public static final byte WASM_F64 = 3;

    public static final byte WASM_EXTERN_FUNC = 0;
    public static final byte WASM_EXTERN_GLOBAL = 1;
    public static final byte WASM_EXTERN_TABLE = 2;
    public static final byte WASM_EXTERN_MEMORY = 3;
    public static final byte WASM_EXTERN_TAG = 4;

    public static final byte WASM_CONST = 0;
    public static final byte WASM_VAR = 1;

    private WasmerTypes() {
    }

    @Structure.FieldOrder({"kind", "of"})
    public static class WasmVal extends Structure {
        public byte kind;
        public WasmValOf of;

        public WasmVal() {
            super();
        }

        public WasmVal(Pointer p) {
            super(p);
            read();
        }

        public static class ByValue extends WasmVal implements Structure.ByValue {
        }

        public static class ByReference extends WasmVal implements Structure.ByReference {
        }
    }

    public static class WasmValOf extends Union {
        public int i32;
        public long i64;
        public float f32;
        public double f64;
        public Pointer ref;

        public WasmValOf() {
            super();
        }

        public WasmValOf(Pointer p) {
            super(p);
            read();
        }
    }

    @Structure.FieldOrder({"size", "data"})
    public static class WasmValVec extends Structure {
        public long size;
        public Pointer data;

        public WasmValVec() {
            super();
        }

        public WasmValVec(Pointer p) {
            super(p);
            read();
        }

        public static class ByReference extends WasmValVec implements Structure.ByReference {
        }
    }

    @Structure.FieldOrder({"size", "data"})
    public static class WasmByteVec extends Structure {
        public long size;
        public Pointer data;

        public WasmByteVec() {
            super();
        }

        public static class ByReference extends WasmByteVec implements Structure.ByReference {
        }
    }

    @Structure.FieldOrder({"size", "data"})
    public static class WasmExternVec extends Structure {
        public long size;
        public Pointer data;

        public WasmExternVec() {
            super();
        }

        public static class ByReference extends WasmExternVec implements Structure.ByReference {
        }
    }

    @Structure.FieldOrder({"size", "data"})
    public static class WasmImporttypeVec extends Structure {
        public long size;
        public Pointer data;

        public WasmImporttypeVec() {
            super();
        }

        public static class ByReference extends WasmImporttypeVec implements Structure.ByReference {
        }
    }

    @Structure.FieldOrder({"size", "data"})
    public static class WasmExporttypeVec extends Structure {
        public long size;
        public Pointer data;

        public WasmExporttypeVec() {
            super();
        }

        public static class ByReference extends WasmExporttypeVec implements Structure.ByReference {
        }
    }

    @Structure.FieldOrder({"size", "data"})
    public static class WasmerNamedExternVec extends Structure {
        public long size;
        public Pointer data;

        public WasmerNamedExternVec() {
            super();
        }

        public static class ByReference extends WasmerNamedExternVec implements Structure.ByReference {
        }
    }

    @Structure.FieldOrder({"min", "max"})
    public static class WasmLimits extends Structure {
        public int min;
        public int max;

        public WasmLimits() {
            super();
        }

        public WasmLimits(Pointer p) {
            super(p);
            read();
        }

        public WasmLimits(int min, int max) {
            this.min = min;
            this.max = max;
        }

        public static class ByValue extends WasmLimits implements Structure.ByValue {
            public ByValue() {
                super();
            }

            public ByValue(int min, int max) {
                super(min, max);
            }
        }

        public static class ByReference extends WasmLimits implements Structure.ByReference {
            public ByReference() {
                super();
            }
        }
    }
}
