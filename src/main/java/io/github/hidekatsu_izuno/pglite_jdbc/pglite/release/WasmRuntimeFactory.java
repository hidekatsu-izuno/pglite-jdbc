package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.initdbModFactory;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.PartialPostgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.pglite.postgresMod.PostgresMod;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerNativeLoader;
import io.github.hidekatsu_izuno.pglite_jdbc.wasmer.WasmerArtifacts;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class WasmRuntimeFactory {
    private WasmRuntimeFactory() {
    }

    public static PostgresMod create(PartialPostgresMod overrides, URL moduleUrl) {
        return createInitdb(overrides, moduleUrl);
    }

    public static initdbModFactory.InitdbMod createInitdb(PartialPostgresMod overrides, URL moduleUrl) {
        var forceEndive = Boolean.getBoolean("pglite.force_endive");
        var forceWasmer = Boolean.getBoolean("pglite.force_wasmer");
        if (forceEndive && forceWasmer) {
            throw new IllegalStateException("pglite.force_endive and pglite.force_wasmer cannot both be true");
        }
        if (forceEndive) {
            return createEndive(overrides, moduleUrl);
        }
        if (forceWasmer && !WasmerNativeLoader.isAvailable()) {
            throw new IllegalStateException("Wasmer was forced but its native library is unavailable", WasmerNativeLoader.loadError());
        }
        if (!WasmerNativeLoader.isAvailable()) {
            return createEndive(overrides, moduleUrl);
        }
        var library = WasmerNativeLoader.get();
        if (library.wasmer_is_headless()) {
            byte[] wasm;
            if (overrides != null && overrides.wasmModule != null) {
                wasm = overrides.wasmModule;
            } else {
                try (var input = moduleUrl.openStream()) {
                    wasm = input.readAllBytes();
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Cannot read pglite.wasm", e);
                }
            }
            if (!WasmerArtifacts.isAvailable(wasm, library.wasmer_version())) {
                if (forceWasmer) {
                    throw new IllegalStateException("Wasmer headless was forced but no matching compiled artifact exists");
                }
                return createEndive(overrides, moduleUrl);
            }
        }
        return new WasmerPostgresMod(overrides, moduleUrl);
    }

    private static initdbModFactory.InitdbMod createEndive(PartialPostgresMod overrides, URL moduleUrl) {
        return new EndivePostgresMod(overrides, moduleUrl);
    }

    public static boolean hasExceptionHandling(byte[] wasm) {
        if (wasm == null || wasm.length < 8) {
            return false;
        }
        var offset = 8;
        while (offset + 1 < wasm.length) {
            var id = wasm[offset] & 0xff;
            offset++;
            var size = 0;
            var shift = 0;
            while (offset < wasm.length) {
                var b = wasm[offset++] & 0xff;
                size |= (b & 0x7f) << shift;
                if ((b & 0x80) == 0) {
                    break;
                }
                shift += 7;
            }
            var end = Math.min(wasm.length, offset + size);
            if (id == 13) {
                return true;
            }
            if (id == 0) {
                var nameLen = 0;
                var shift2 = 0;
                var pos = offset;
                while (pos < end) {
                    var b = wasm[pos++] & 0xff;
                    nameLen |= (b & 0x7f) << shift2;
                    if ((b & 0x80) == 0) {
                        break;
                    }
                    shift2 += 7;
                }
                if (pos + nameLen <= end) {
                    var name = new String(wasm, pos, nameLen, StandardCharsets.UTF_8);
                    if ("target_features".equals(name)) {
                        var payload = new String(
                            wasm,
                            pos + nameLen,
                            end - (pos + nameLen),
                            StandardCharsets.UTF_8
                        );
                        if (payload.contains("exception-handling")) {
                            return true;
                        }
                    }
                }
            }
            offset = end;
        }
        return false;
    }

}
