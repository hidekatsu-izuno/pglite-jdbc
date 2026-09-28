package io.github.hidekatsu_izuno.pglite_jdbc.pglite.release;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.*;
import java.util.*;
import java.util.function.Function;

/**
 * Host ABI used by the unmodified Emscripten builds shipped by PGlite.
 * Layouts and return conventions follow @electric-sql/pglite@0.5.3/dist/pglite.js.
 */
final class EmscriptenHost implements AutoCloseable {
    static final class Longjmp extends RuntimeException {
        Longjmp() { super("Emscripten longjmp", null, false, false); }
    }
    private static final class Errno extends IOException {
        final int code;
        Errno(int code) { this.code = code; }
    }
    private static final class Descriptor {
        String path;
        RandomAccessFile file;
        long position;
        int flags;
        int references = 1;
        List<String> entries;
        ArrayDeque<Byte> pipe;
    }
    private final WasmerPostgresMod mod;
    private final Function<String, Path> resolve;
    private final Map<String, String> env;
    private final Map<Integer, Descriptor> descriptors = new HashMap<>();
    private final java.security.SecureRandom random = new java.security.SecureRandom();
    private String cwd = "/";
    private final Map<Integer, Long> timers = new HashMap<>();
    private boolean deliveringTimer;

    EmscriptenHost(WasmerPostgresMod mod, Function<String, Path> resolve, Map<String, String> env) {
        this.mod = mod; this.resolve = resolve; this.env = env;
        try {
            Files.createDirectories(resolve.apply("/dev/shm"));
            Files.createDirectories(resolve.apply("/tmp"));
        } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        for (var i = 0; i < 3; i++) {
            var descriptor = new Descriptor(); descriptor.path = i == 0 ? "/dev/stdin" : i == 1 ? "/dev/stdout" : "/dev/stderr";
            descriptor.flags = i == 0 ? 0 : 1;
            descriptors.put(i, descriptor);
        }
    }

    private int i32(long ptr) { return mod.emMemory().getInt(ptr); }
    private void i32(long ptr, int value) { mod.emMemory().setInt(ptr, value); }
    private void i64(long ptr, long value) { mod.emMemory().setLong(ptr, value); }
    private String string(long ptr) { return mod.emString((int) ptr); }
    private String absolute(String path) {
        return Path.of(path.startsWith("/") ? path : cwd + "/" + path).normalize().toString().replace('\\', '/');
    }
    private String at(long fd, long ptr) throws IOException {
        var path = string(ptr);
        if (path.startsWith("/")) return absolute(path);
        return absolute((fd == -100 ? cwd : descriptor(fd).path) + "/" + path);
    }
    private Descriptor descriptor(long fd) throws Errno {
        var descriptor = descriptors.get((int) fd);
        if (descriptor == null) throw new Errno(8);
        return descriptor;
    }
    private int allocate(Descriptor descriptor, int minimum) {
        var fd = minimum;
        while (descriptors.containsKey(fd)) fd++;
        descriptors.put(fd, descriptor);
        return fd;
    }
    private int error(IOException error) {
        if (error instanceof Errno errno) return errno.code;
        if (error instanceof NoSuchFileException || error instanceof java.io.FileNotFoundException) return 44;
        if (error instanceof FileAlreadyExistsException) return 20;
        if (error instanceof NotLinkException) return 28;
        if (error instanceof DirectoryNotEmptyException) return 55;
        if (error instanceof NotDirectoryException) return 54;
        if (error instanceof AccessDeniedException) return 2;
        return 29;
    }

    long call(String module, String name, long[] args) {
        if (Boolean.getBoolean("pglite.trace_env_calls")) System.err.println("[emscripten] " + name + " " + Arrays.toString(args));
        if (!deliveringTimer && !name.equals("_setitimer_js") && !timers.isEmpty()) {
            var now = System.nanoTime();
            for (var timer : new ArrayList<>(timers.entrySet())) {
                if (now < timer.getValue()) continue;
                timers.remove(timer.getKey());
                deliveringTimer = true;
                try { mod.emCall("_emscripten_timeout", timer.getKey(), Double.doubleToRawLongBits(now / 1_000_000.0)); }
                finally { deliveringTimer = false; }
            }
        }
        if (module.equals("java")) return mod.emCallback(name, args);
        if (name.startsWith("invoke_")) return mod.invokeEmscripten(name.substring(7), args);
        try {
            return module.equals("wasi_snapshot_preview1") ? wasi(name, args) : env(name, args);
        } catch (IOException error) {
            return module.equals("wasi_snapshot_preview1") ? error(error) : -error(error);
        }
    }

    private long wasi(String name, long[] a) throws IOException {
        return switch (name) {
            case "environ_sizes_get" -> {
                i32(a[0], env.size());
                i32(a[1], env.entrySet().stream().mapToInt(e -> (e.getKey() + "=" + e.getValue()).getBytes(StandardCharsets.UTF_8).length + 1).sum());
                yield 0;
            }
            case "environ_get" -> {
                var pointer = (int) a[1]; var index = 0;
                for (var entry : env.entrySet()) {
                    var bytes = (entry.getKey() + "=" + entry.getValue() + "\0").getBytes(StandardCharsets.UTF_8);
                    i32(a[0] + 4L * index++, pointer); mod.emWrite(pointer, bytes); pointer += bytes.length;
                }
                yield 0;
            }
            case "clock_time_get" -> { i64(a[2], a[0] == 0 ? System.currentTimeMillis() * 1_000_000L : System.nanoTime()); yield 0; }
            case "random_get" -> { var bytes = new byte[(int) a[1]]; random.nextBytes(bytes); mod.emWrite((int) a[0], bytes); yield 0; }
            case "proc_exit" -> { mod.emExit((int) a[0]); yield 0; }
            case "fd_close" -> { closeFd((int) a[0]); yield 0; }
            case "fd_sync", "fd_datasync" -> { var d = descriptor(a[0]); if (d.file != null) d.file.getFD().sync(); yield 0; }
            case "fd_fdstat_get" -> {
                var d = descriptor(a[0]); var p = a[1];
                mod.emMemory().setMemory(p, 24, (byte) 0);
                mod.emMemory().setByte(p, (byte) (isDevice(d.path) ? 2 : Files.isDirectory(resolve.apply(d.path)) ? 3 : 4));
                mod.emMemory().setShort(p + 2, (short) ((d.flags & 1024) != 0 ? 1 : 0));
                i64(p + 8, -1); i64(p + 16, -1); yield 0;
            }
            case "fd_seek" -> { var d = descriptor(a[0]); d.position = seek(d, a[1], (int) a[2]); i64(a[3], d.position); yield 0; }
            case "fd_read", "fd_write", "fd_pread", "fd_pwrite" -> {
                var d = descriptor(a[0]); var positional = name.equals("fd_pread") || name.equals("fd_pwrite");
                var position = positional ? a[3] : d.position; var count = 0;
                for (var index = 0; index < a[2]; index++) {
                    var pointer = i32(a[1] + index * 8L); var length = i32(a[1] + index * 8L + 4);
                    var transferred = name.endsWith("write") ? write(d, pointer, length, position) : read(d, pointer, length, position);
                    count += transferred; position += transferred;
                    if (transferred < length) break;
                }
                if (!positional) d.position = position;
                i32(a[positional ? 4 : 3], count); yield 0;
            }
            default -> throw new UnsupportedOperationException("Unsupported Emscripten WASI import: " + name);
        };
    }

    private long env(String name, long[] a) throws IOException {
        return switch (name) {
            case "_emscripten_throw_longjmp" -> throw new Longjmp();
            case "getTempRet0" -> mod.emCall("_emscripten_tempret_get");
            case "setTempRet0" -> { mod.emCall("_emscripten_tempret_set", a[0]); yield 0; }
            case "exit" -> { mod.emExit((int) a[0]); yield 0; }
            case "_abort_js" -> throw new IllegalStateException("PGlite aborted");
            case "__assert_fail" -> throw new IllegalStateException(string(a[0]) + " at " + string(a[1]) + ":" + a[2]);
            case "emscripten_date_now" -> Double.doubleToRawLongBits(System.currentTimeMillis());
            case "emscripten_get_now" -> Double.doubleToRawLongBits(System.nanoTime() / 1_000_000.0);
            case "emscripten_get_heap_max" -> 2147483648L;
            case "emscripten_resize_heap" -> mod.emGrow((int) a[0]);
            case "_emscripten_runtime_keepalive_clear" -> 0; // Java owns instance lifetime.
            case "_dlopen_js" -> mod.emDlopen((int) a[0]);
            case "_dlsym_js" -> mod.emDlsym((int) a[0], (int) a[1]);
            case "__syscall_openat" -> open(at(a[0], a[1]), (int) a[2]);
            case "__syscall_chdir" -> chdir(string(a[0]));
            case "__syscall_getcwd" -> {
                var bytes = (cwd + "\0").getBytes(StandardCharsets.UTF_8);
                if (bytes.length > a[1]) throw new Errno(68);
                mod.emWrite((int) a[0], bytes); yield bytes.length;
            }
            case "__syscall_faccessat" -> {
                var path = resolve.apply(at(a[0], a[1]));
                if (!Files.exists(path)) throw new NoSuchFileException(path.toString());
                if (((a[2] & 4) != 0 && !Files.isReadable(path)) || ((a[2] & 2) != 0 && !Files.isWritable(path))) throw new Errno(2);
                yield 0;
            }
            case "__syscall_stat64", "__syscall_lstat64" -> stat(absolute(string(a[0])), a[1], name.contains("lstat"));
            case "__syscall_fstat64" -> stat(descriptor(a[0]).path, a[1], false);
            case "__syscall_newfstatat" -> stat(at(a[0], a[1]), a[2], (a[3] & 256) != 0);
            case "__syscall_mkdirat" -> { Files.createDirectory(resolve.apply(at(a[0], a[1]))); yield 0; }
            case "__syscall_rmdir" -> { Files.delete(resolve.apply(absolute(string(a[0])))); yield 0; }
            case "__syscall_unlinkat" -> { Files.delete(resolve.apply(at(a[0], a[1]))); yield 0; }
            case "__syscall_renameat" -> { Files.move(resolve.apply(at(a[0], a[1])), resolve.apply(at(a[2], a[3])), StandardCopyOption.REPLACE_EXISTING); yield 0; }
            case "__syscall_readlinkat" -> {
                var bytes = Files.readSymbolicLink(resolve.apply(at(a[0], a[1]))).toString().getBytes(StandardCharsets.UTF_8);
                var length = Math.min(bytes.length, (int) a[3]); mod.emWrite((int) a[2], Arrays.copyOf(bytes, length)); yield length;
            }
            case "__syscall_symlinkat" -> { Files.createSymbolicLink(resolve.apply(at(a[1], a[2])), Path.of(string(a[0]))); yield 0; }
            case "__syscall_ftruncate64" -> { var file = descriptor(a[0]).file; if (file == null) throw new Errno(28); file.setLength(a[1]); yield 0; }
            case "__syscall_truncate64" -> {
                if (a[1] < 0) throw new Errno(28);
                // Unlike RandomAccessFile("rw"), POSIX truncate must not create
                // a missing relation segment: PostgreSQL uses ENOENT to stop.
                try (var file = java.nio.channels.FileChannel.open(resolve.apply(absolute(string(a[0]))), StandardOpenOption.WRITE)) {
                    if (a[1] > file.size()) {
                        file.position(a[1] - 1);
                        file.write(java.nio.ByteBuffer.wrap(new byte[1]));
                    } else file.truncate(a[1]);
                }
                yield 0;
            }
            case "__syscall_fdatasync" -> { var d = descriptor(a[0]); if (d.file != null) d.file.getFD().sync(); yield 0; }
            case "__syscall_fallocate" -> { var d = descriptor(a[0]); d.file.setLength(Math.max(d.file.length(), a[2] + a[3])); yield 0; }
            case "__syscall_fadvise64" -> { descriptor(a[0]); yield 0; } // Advisory; no correctness effect.
            case "__syscall_getdents64" -> getdents(descriptor(a[0]), (int) a[1], (int) a[2]);
            case "__syscall_dup" -> { var d = descriptor(a[0]); d.references++; yield allocate(d, 0); }
            case "__syscall_dup3" -> { var d = descriptor(a[0]); if (a[0] == a[1]) throw new Errno(28); if (descriptors.containsKey((int) a[1])) closeFd((int) a[1]); d.references++; descriptors.put((int) a[1], d); yield a[1]; }
            case "__syscall_fcntl64" -> fcntl(a);
            case "__syscall_ioctl" -> -59; // No terminal devices in embedded execution.
            case "__syscall_pipe" -> {
                var queue = new ArrayDeque<Byte>();
                var reader = new Descriptor(); reader.path = "/dev/pipe"; reader.pipe = queue;
                var writer = new Descriptor(); writer.path = "/dev/pipe"; writer.pipe = queue; writer.flags = 1;
                i32(a[0], allocate(reader, 0)); i32(a[0] + 4, allocate(writer, 0)); yield 0;
            }
            case "__syscall_chmod" -> chmod(absolute(string(a[0])), (int) a[1]);
            case "__syscall_fchmod" -> chmod(descriptor(a[0]).path, (int) a[1]);
            case "__syscall_fchmodat2" -> chmod(at(a[0], a[1]), (int) a[2]);
            case "__syscall_fchown32" -> { descriptor(a[0]); yield 0; } // Virtual single-user filesystem, like MEMFS.
            case "__syscall_fchownat" -> { stat(at(a[0], a[1]), 0, false); yield 0; }
            case "__syscall_utimensat" -> {
                var p = resolve.apply(at(a[0], a[1]));
                var millis = a[2] == 0 ? System.currentTimeMillis() : mod.emMemory().getLong(a[2] + 16) * 1000 + i32(a[2] + 24) / 1_000_000;
                if (a[2] != 0 && i32(a[2] + 24) == 1073741823) millis = System.currentTimeMillis();
                if (a[2] == 0 || i32(a[2] + 24) != 1073741822) Files.setLastModifiedTime(p, FileTime.fromMillis(millis)); yield 0;
            }
            case "_gmtime_js", "_localtime_js" -> { time(a[0], (int) a[1], name.equals("_gmtime_js") ? ZoneOffset.UTC : zone()); yield 0; }
            case "_mktime_js" -> {
                var p = a[0]; var date = LocalDateTime.of(i32(p + 20) + 1900, 1, 1, 0, 0).plusMonths(i32(p + 16)).plusDays(i32(p + 12) - 1).plusHours(i32(p + 8)).plusMinutes(i32(p + 4)).plusSeconds(i32(p));
                var epoch = date.atZone(zone()).toEpochSecond(); time(epoch, (int) p, zone()); yield epoch;
            }
            case "_tzset_js" -> {
                var zone = zone(); var now = Instant.now();
                i32(a[0], -zone.getRules().getStandardOffset(now).getTotalSeconds());
                i32(a[1], zone.getRules().isFixedOffset() ? 0 : 1);
                var bytes = (zone.getId() + "\0").getBytes(StandardCharsets.UTF_8);
                mod.emWrite((int) a[2], Arrays.copyOf(bytes, Math.min(bytes.length, 16)));
                mod.emWrite((int) a[3], Arrays.copyOf(bytes, Math.min(bytes.length, 16))); yield 0;
            }
            case "_mmap_js" -> {
                var pointer = (int) mod.emCall("emscripten_builtin_memalign", 65536, a[0]);
                if (pointer == 0) throw new Errno(48);
                mod.emMemory().setMemory(pointer, a[0], (byte) 0);
                if ((a[2] & 32) == 0) read(descriptor(a[3]), pointer, (int) a[0], a[4]);
                i32(a[5], 1); i32(a[6], pointer); yield 0;
            }
            case "_munmap_js" -> { if ((a[2] & 2) != 0 && (a[3] & 2) == 0 && a[4] >= 0) write(descriptor(a[4]), (int) a[0], (int) a[1], a[5]); yield 0; }
            case "_setitimer_js" -> {
                var duration = Double.longBitsToDouble(a[1]);
                if (duration == 0) timers.remove((int) a[0]);
                else timers.put((int) a[0], System.nanoTime() + (long) (duration * 1_000_000));
                yield 0;
            }
            case "__call_sighandler" -> mod.emTableCall((int) a[0], a[1]);
            // Removed network server behavior. Upstream: SOCKFS.createSocket/domain bind/listen.
            case "__syscall_socket", "__syscall_bind", "__syscall_connect", "__syscall_listen", "__syscall_accept4", "__syscall_sendto", "__syscall_recvfrom" -> -52;
            case "getaddrinfo" -> getaddrinfo(a);
            case "getnameinfo" -> getnameinfo(a);
            default -> throw new UnsupportedOperationException("Unsupported Emscripten import: " + name);
        };
    }

    private long getaddrinfo(long[] a) {
        var flags = a[2] == 0 ? 0 : i32(a[2]);
        var family = a[2] == 0 ? 0 : i32(a[2] + 4);
        var type = a[2] == 0 ? 0 : i32(a[2] + 8);
        var protocol = a[2] == 0 ? 0 : i32(a[2] + 12);
        if (family != 0 && family != 2 && family != 10) return -6;
        if (type != 0 && type != 1 && type != 2) return -7;
        if (a[0] == 0 && a[1] == 0) return -2;
        var port = 0;
        if (a[1] != 0) {
            try { port = Integer.parseInt(string(a[1])); }
            catch (NumberFormatException error) { return -8; }
        }
        var host = a[0] == 0 ? (family == 10 ? ((flags & 1) == 0 ? "::1" : "::") : ((flags & 1) == 0 ? "127.0.0.1" : "0.0.0.0")) : string(a[0]);
        if (host.equals("localhost") && (flags & 4) == 0) host = family == 10 ? "::1" : "127.0.0.1";
        if (!host.matches("[0-9.]+") && !host.contains(":")) return -2;
        byte[] address;
        try { address = java.net.InetAddress.getByName(host).getAddress(); }
        catch (java.net.UnknownHostException error) { return -2; }
        var actualFamily = address.length == 16 ? 10 : 2;
        if (family != 0 && family != actualFamily) return -2;
        family = actualFamily;
        if (type == 0) type = protocol == 17 ? 2 : 1;
        if (protocol == 0) protocol = type == 2 ? 17 : 6;
        var size = family == 10 ? 28 : 16;
        var socket = (int) mod.emCall("malloc", size);
        var info = (int) mod.emCall("malloc", 32);
        mod.emMemory().setMemory(socket, size, (byte) 0);
        mod.emMemory().setMemory(info, 32, (byte) 0);
        mod.emMemory().setShort(socket, (short) family);
        mod.emMemory().setShort(socket + 2, Short.reverseBytes((short) port));
        mod.emWrite(socket + (family == 10 ? 8 : 4), address);
        i32(info + 4, family); i32(info + 8, type); i32(info + 12, protocol);
        i32(info + 16, size); i32(info + 20, socket); i32(a[3], info);
        return 0;
    }

    private long getnameinfo(long[] a) {
        var family = mod.emMemory().getShort(a[0]);
        if (family != 2 && family != 10) return -6;
        try {
            var address = mod.emRead((int) a[0] + (family == 10 ? 8 : 4), family == 10 ? 16 : 4);
            var host = java.net.InetAddress.getByAddress(address).getHostAddress();
            var port = Short.toUnsignedInt(Short.reverseBytes(mod.emMemory().getShort(a[0] + 2)));
            if (a[2] != 0 && !putString((int) a[2], (int) a[3], host)) return -12;
            if (a[4] != 0 && !putString((int) a[4], (int) a[5], Integer.toString(port))) return -12;
            return 0;
        } catch (java.net.UnknownHostException error) { return -6; }
    }

    private boolean putString(int pointer, int capacity, String value) {
        var bytes = (value + "\0").getBytes(StandardCharsets.UTF_8);
        if (bytes.length > capacity) return false;
        mod.emWrite(pointer, bytes); return true;
    }

    int chdir(String path) {
        var absolute = absolute(path);
        if (!Files.isDirectory(resolve.apply(absolute))) return -54;
        cwd = absolute; return 0;
    }
    private ZoneId zone() { return ZoneId.of(env.getOrDefault("TZ", "UTC")); }
    private void time(long seconds, int ptr, ZoneId zone) {
        var date = Instant.ofEpochSecond(seconds).atZone(zone);
        var fields = new int[] {date.getSecond(), date.getMinute(), date.getHour(), date.getDayOfMonth(), date.getMonthValue() - 1, date.getYear() - 1900, date.getDayOfWeek().getValue() % 7, date.getDayOfYear() - 1, zone.getRules().isDaylightSavings(date.toInstant()) ? 1 : 0, date.getOffset().getTotalSeconds()};
        for (var i = 0; i < fields.length; i++) i32(ptr + 4L * i, fields[i]);
    }
    private boolean isDevice(String path) {
        return mod.emDevice(path) != null || Set.of("/dev/stdin", "/dev/stdout", "/dev/stderr", "/dev/null", "/dev/random", "/dev/urandom").contains(path);
    }
    private int open(String path, int flags) throws IOException {
        var target = resolve.apply(path); var d = new Descriptor(); d.path = path; d.flags = flags;
        if (mod.emDevice(path) == null && !isDevice(path)) {
            if ((flags & 64) != 0 && (flags & 128) != 0) Files.createFile(target);
            if (!Files.exists(target) && (flags & 64) == 0) throw new NoSuchFileException(path);
            if ((flags & 65536) != 0 && !Files.isDirectory(target)) throw new NotDirectoryException(path);
            if (!Files.isDirectory(target)) {
                d.file = new RandomAccessFile(target.toFile(), (flags & 3) == 0 ? "r" : "rw");
                if ((flags & 512) != 0) d.file.setLength(0);
                if ((flags & 1024) != 0) d.position = d.file.length();
            }
        }
        return allocate(d, 0);
    }
    private void closeFd(int fd) throws IOException {
        var d = descriptor(fd); descriptors.remove(fd);
        if (--d.references == 0 && d.file != null) d.file.close();
    }
    private long seek(Descriptor d, long offset, int whence) throws IOException {
        if (d.pipe != null) throw new Errno(70);
        var device = mod.emDevice(d.path);
        if (device != null) return device.llseek((int) offset, whence, (int) d.position);
        var position = switch (whence) { case 0 -> offset; case 1 -> d.position + offset; case 2 -> (d.file == null ? 0 : d.file.length()) + offset; default -> throw new Errno(28); };
        if (position < 0) throw new Errno(28); return position;
    }
    private int read(Descriptor d, int ptr, int length, long position) throws IOException {
        if ((d.flags & 3) == 1) throw new Errno(8);
        var bytes = new byte[length]; var count = 0; var device = mod.emDevice(d.path);
        if (device != null) count = device.read(bytes, 0, length, (int) position);
        else if (d.pipe != null) { while (count < length && !d.pipe.isEmpty()) bytes[count++] = d.pipe.removeFirst(); }
        else if (d.path.equals("/dev/random") || d.path.equals("/dev/urandom")) { random.nextBytes(bytes); count = length; }
        else if (d.file != null) { d.file.seek(position); count = Math.max(0, d.file.read(bytes)); }
        else if (!d.path.startsWith("/dev/")) throw new Errno(31);
        mod.emWrite(ptr, Arrays.copyOf(bytes, count)); return count;
    }
    private int write(Descriptor d, int ptr, int length, long position) throws IOException {
        if ((d.flags & 3) == 0) throw new Errno(8);
        var bytes = mod.emRead(ptr, length); var device = mod.emDevice(d.path);
        if (device != null) return device.write(bytes, 0, length, (int) position);
        if (d.pipe != null) { for (var value : bytes) d.pipe.addLast(value); }
        else if (d.file != null) { d.file.seek((d.flags & 1024) != 0 ? d.file.length() : position); d.file.write(bytes); }
        else if (d.path.equals("/dev/stdout")) mod.print(new String(bytes, StandardCharsets.UTF_8));
        else if (d.path.equals("/dev/stderr")) mod.printErr(new String(bytes, StandardCharsets.UTF_8));
        else if (!d.path.equals("/dev/null")) throw new Errno(8);
        return length;
    }
    private long stat(String path, long pointer, boolean nofollow) throws IOException {
        var target = resolve.apply(path);
        var device = isDevice(path);
        var attributes = device ? null : Files.readAttributes(target, BasicFileAttributes.class, nofollow ? new LinkOption[] {LinkOption.NOFOLLOW_LINKS} : new LinkOption[0]);
        if (pointer == 0) return 0;
        mod.emMemory().setMemory(pointer, 96, (byte) 0);
        var size = device ? 0 : attributes.size();
        var mode = device ? 0020666 : attributes.isDirectory() ? 0040777 : attributes.isSymbolicLink() ? 0120777 : 0100666;
        i32(pointer, 1); i32(pointer + 4, mode); i32(pointer + 8, 1); i32(pointer + 12, 1000); i32(pointer + 16, 1000);
        i64(pointer + 24, size); i32(pointer + 32, 4096); i32(pointer + 36, (int) ((size + 511) / 512));
        var times = device ? new long[3] : new long[] {attributes.lastAccessTime().toMillis(), attributes.lastModifiedTime().toMillis(), attributes.creationTime().toMillis()};
        for (var i = 0; i < 3; i++) { i64(pointer + 40 + 16L * i, times[i] / 1000); i32(pointer + 48 + 16L * i, (int) (times[i] % 1000) * 1_000_000); }
        i64(pointer + 88, Integer.toUnsignedLong(path.hashCode())); return 0;
    }
    private int chmod(String path, int mode) throws IOException {
        var target = resolve.apply(path);
        if (!Files.exists(target)) throw new NoSuchFileException(path);
        if (Files.getFileStore(target).supportsFileAttributeView("posix")) {
            var permissions = EnumSet.noneOf(java.nio.file.attribute.PosixFilePermission.class);
            var values = java.nio.file.attribute.PosixFilePermission.values();
            for (var i = 0; i < 9; i++) if ((mode & (1 << (8 - i))) != 0) permissions.add(values[i]);
            Files.setPosixFilePermissions(target, permissions);
        }
        return 0;
    }
    private int getdents(Descriptor d, int pointer, int size) throws IOException {
        if (d.entries == null) {
            d.entries = new ArrayList<>(List.of(".", ".."));
            try (var files = Files.list(resolve.apply(d.path))) { d.entries.addAll(files.map(p -> p.getFileName().toString()).sorted().toList()); }
        }
        var count = 0;
        while (d.position / 280 < d.entries.size() && count + 280 <= size) {
            var name = d.entries.get((int) (d.position / 280)); var path = absolute(d.path + "/" + name);
            mod.emMemory().setMemory(pointer + count, 280, (byte) 0);
            i64(pointer + count, Integer.toUnsignedLong(path.hashCode())); i64(pointer + count + 8, d.position + 280);
            mod.emMemory().setShort(pointer + count + 16, (short) 280);
            mod.emMemory().setByte(pointer + count + 18, (byte) (Files.isDirectory(resolve.apply(path)) ? 4 : 8));
            mod.emWrite(pointer + count + 19, (name + "\0").getBytes(StandardCharsets.UTF_8));
            d.position += 280; count += 280;
        }
        return count;
    }
    private long fcntl(long[] a) throws IOException {
        var d = descriptor(a[0]);
        return switch ((int) a[1]) {
            case 0, 1030 -> { d.references++; yield allocate(d, i32(a[2])); }
            case 1, 2 -> 0;
            case 3 -> d.flags;
            case 4 -> { d.flags |= i32(a[2]); yield 0; }
            case 5 -> { mod.emMemory().setShort(i32(a[2]), (short) 2); yield 0; }
            case 6, 7 -> 0; // Emscripten MEMFS does not implement process locks.
            default -> -28;
        };
    }
    @Override public void close() {
        for (var fd : new ArrayList<>(descriptors.keySet())) {
            try { closeFd(fd); } catch (IOException error) { throw new java.io.UncheckedIOException(error); }
        }
    }
}
