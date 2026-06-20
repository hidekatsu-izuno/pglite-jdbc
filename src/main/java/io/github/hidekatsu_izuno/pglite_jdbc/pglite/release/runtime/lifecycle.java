package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.runtime;

import io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.core.runtimeTypes;
import java.util.ArrayList;

public class lifecycle {
    private lifecycle() {}

    public static void mergePreRunHooks(runtimeTypes.RuntimeModule moduleArg, Runnable hook) {
        var hooks = new ArrayList<>(moduleArg.preRun());
        hooks.add(hook);
        moduleArg.setPreRun(hooks);
    }

    public static void mergePostRunHooks(runtimeTypes.RuntimeModule moduleArg, Runnable hook) {
        var hooks = new ArrayList<>(moduleArg.postRun());
        hooks.add(hook);
        moduleArg.setPostRun(hooks);
    }
}
