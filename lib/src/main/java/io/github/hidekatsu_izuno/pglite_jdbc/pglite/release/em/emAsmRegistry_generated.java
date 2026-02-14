package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.em;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.Map;
import java.util.function.Consumer;

public class emAsmRegistry_generated {
    private emAsmRegistry_generated() {}

    private static Map<String, Object> moduleMap(runtimeTypes.EmResolverContext context) {
        return context.state().moduleArg();
    }

    private static String eventType(Object event) {
        if (event instanceof Map<?, ?> map) {
            var type = map.get("type");
            return type != null ? String.valueOf(type) : "";
        }
        return "";
    }

    private static String eventData(Object event) {
        if (event instanceof Map<?, ?> map) {
            var data = map.get("data");
            return data != null ? String.valueOf(data) : "";
        }
        return "";
    }

    public static final Map<Integer, runtimeTypes.EmAsmHandler> emAsmRegistry = Map.of(
        2537480,
        (context, args) -> {
            var module = moduleMap(context);
            module.put("is_worker", false);
            var fdBufferMax = args.length > 0 && args[0] instanceof Number n ? n.intValue() : 0;
            module.put("FD_BUFFER_MAX", fdBufferMax);
            module.put("emscripten_copy_to", "console.warn");
            return null;
        },
        2537652,
        (context, args) -> {
            var module = moduleMap(context);
            module.put("postMessage", (Consumer<Object>) event -> context
                .state()
                .diagnostics()
                .add("# pg_main_emsdk.c:544: onCustomMessage: " + event));
            return null;
        },
        2537781,
        (context, args) -> {
            var module = moduleMap(context);
            var isWorker = Boolean.TRUE.equals(module.get("is_worker"));
            if (isWorker) {
                module.put("onCustomMessage", (Consumer<Object>) event -> context
                    .state()
                    .diagnostics()
                    .add("onCustomMessage: " + event));
                return null;
            }
            module.put("postMessage", (Consumer<Object>) event -> {
                var type = eventType(event);
                if ("stdin".equals(type)) {
                    var fdBufferMax = module.get("FD_BUFFER_MAX") instanceof Number n ? n.intValue() : 0;
                    var data = eventData(event);
                    context.state().diagnostics().add("stdin:" + data + ":" + fdBufferMax);
                    return;
                }
                if ("raw".equals(type) || "rcon".equals(type)) {
                    return;
                }
                context.state().diagnostics().add("custom_postMessage? " + event);
            });
            return null;
        }
    );
}
