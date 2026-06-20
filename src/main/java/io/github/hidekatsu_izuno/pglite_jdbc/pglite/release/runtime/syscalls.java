package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release.runtime;

import java.util.ArrayDeque;
import java.util.List;

public class syscalls {
    public record SyscallRecord(String name, Object[] args, Object result) {}

    public interface SyscallRecorder {
        void record(String name, Object[] args, Object result);

        List<SyscallRecord> recent();
    }

    private syscalls() {}

    public static SyscallRecorder createSyscallRecorder(int maxEntries) {
        var entries = new ArrayDeque<SyscallRecord>();
        return new SyscallRecorder() {
            @Override
            public void record(String name, Object[] args, Object result) {
                entries.addLast(new SyscallRecord(name, args, result));
                while (entries.size() > maxEntries) {
                    entries.removeFirst();
                }
            }

            @Override
            public List<SyscallRecord> recent() {
                return List.copyOf(entries);
            }
        };
    }
}
