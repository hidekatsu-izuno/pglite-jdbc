package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import run.endive.runtime.CallResult;
import run.endive.runtime.Instance;
import run.endive.runtime.InterpreterMachine;

/** Restores Endive 1.1's interpreter stacks when a host exception unwinds a WASM call. */
class EndiveInterpreterMachine extends InterpreterMachine {
    EndiveInterpreterMachine(Instance instance) {
        super(instance);
    }

    @Override
    public long[] call(int function, long[] args) {
        var operands = stack().size();
        var frames = callStack.size();
        try {
            return super.call(function, args);
        } catch (RuntimeException | Error error) {
            unwind(operands, frames);
            throw error;
        }
    }

    @Override
    public CallResult callWithRefs(int function, long[] args, Object[] refs) {
        var operands = stack().size();
        var frames = callStack.size();
        try {
            return super.callWithRefs(function, args, refs);
        } catch (RuntimeException | Error error) {
            unwind(operands, frames);
            throw error;
        }
    }

    private void unwind(int operands, int frames) {
        // A host callback may re-enter the same machine. Preserve its caller's
        // operands and frames; only discard state left by the failed invocation.
        stack().clearRefsTo(operands);
        while (stack().size() > operands) stack().pop();
        while (callStack.size() > frames) callStack.pop();
    }
}
