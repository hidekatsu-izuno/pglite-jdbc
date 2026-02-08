package io.github.hidekatsu_izuno.pglite_jdbc.pglite.runtime;

import com.dylibso.chicory.runtime.Instance;
import com.dylibso.chicory.runtime.PgliteInterpreterMachineFork;

/**
 * Project-local machine entrypoint.
 *
 * Keeping this under the project package avoids overriding Chicory classes under
 * com.dylibso.* while still allowing explicit machine injection via Instance.Builder.
 */
public final class PgliteInterpreterMachine extends PgliteInterpreterMachineFork {
    public PgliteInterpreterMachine(Instance instance) {
        super(instance);
    }
}
