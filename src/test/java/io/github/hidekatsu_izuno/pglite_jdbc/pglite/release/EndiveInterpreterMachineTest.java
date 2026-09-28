package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import run.endive.runtime.HostFunction;
import run.endive.runtime.ImportValues;
import run.endive.runtime.Instance;
import run.endive.wasm.Parser;

class EndiveInterpreterMachineTest {
    // (module (import "env" "fail" (func $fail))
    //   (func (export "run") i32.const 42 call $fail drop))
    private static final byte[] THROWING_MODULE = HexFormat.of().parseHex(
        "0061736d01000000010401600000020c0103656e76046661696c0000"
            + "030201000707010372756e00010a09010700412a10001a0b");

    @Test
    void hostExceptionDoesNotAccumulateOperandsOrFrames() {
        var module = Parser.parse(THROWING_MODULE);
        var failure = new EmscriptenHost.Longjmp();
        var host = new HostFunction("env", "fail", module.typeSection().getType(0),
            (instance, args) -> { throw failure; });
        var instance = Instance.builder(module).withMachineFactory(InspectableMachine::new)
            .withImportValues(ImportValues.builder().addFunction(host).build()).build();
        var machine = (InspectableMachine) instance.getMachine();
        for (var i = 0; i < 10; i++) {
            assertSame(failure, assertThrows(EmscriptenHost.Longjmp.class, () -> instance.export("run").apply()));
            assertEquals(0, machine.operands());
            assertEquals(0, machine.frames());
            assertSame(failure, assertThrows(EmscriptenHost.Longjmp.class,
                () -> machine.callWithRefs(1, new long[0], null)));
            assertEquals(0, machine.operands());
            assertEquals(0, machine.frames());
        }
    }

    @Test
    void reentrantFailurePreservesOuterOperands() {
        var module = Parser.parse(THROWING_MODULE);
        var entered = new boolean[1];
        var host = new HostFunction("env", "fail", module.typeSection().getType(0), (instance, args) -> {
            if (entered[0]) throw new EmscriptenHost.Longjmp();
            entered[0] = true;
            var machine = (InspectableMachine) instance.getMachine();
            var operands = machine.operands();
            var frames = machine.frames();
            assertThrows(EmscriptenHost.Longjmp.class, () -> instance.export("run").apply());
            assertEquals(operands, machine.operands());
            assertEquals(frames, machine.frames());
            entered[0] = false;
            return new long[0];
        });
        var instance = Instance.builder(module).withMachineFactory(InspectableMachine::new)
            .withImportValues(ImportValues.builder().addFunction(host).build()).build();
        assertDoesNotThrow(() -> instance.export("run").apply());
        assertEquals(0, ((InspectableMachine) instance.getMachine()).operands());
    }

    private static final class InspectableMachine extends EndiveInterpreterMachine {
        InspectableMachine(Instance instance) { super(instance); }
        int operands() { return stack().size(); }
        int frames() { return callStack.size(); }
    }
}
