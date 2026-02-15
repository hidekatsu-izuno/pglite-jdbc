import { createPIPEFS } from "./pipefs.ts";
import { createSocketAddressUtils } from "./socket-address.ts";
import { createSOCKFS } from "./sockfs.ts";

export const createSyscallImplementations = ({
  FS,
  SYSCALLS,
  HEAP32,
  HEAP16,
  HEAPU16,
  HEAP8,
  HEAPU8,
  HEAP64,
  lengthBytesUTF8,
  stringToUTF8Array,
  Module,
  ENVIRONMENT_IS_NODE,
  require,
  TextEncoder,
  _ntohs,
  _htons,
  zeroMemory,
  assert,
  abort
}: {
  FS: any;
  SYSCALLS: any;
  HEAP32: Int32Array;
  HEAP16: Int16Array;
  HEAPU16: Uint16Array;
  HEAP8: Int8Array;
  HEAPU8: Uint8Array;
  HEAP64: BigInt64Array;
  lengthBytesUTF8: (str: string) => number;
  stringToUTF8Array: (str: string, heap: any, outIdx: number, maxBytesToWrite: number) => number;
  Module: Record<string, any>;
  ENVIRONMENT_IS_NODE: boolean;
  require?: (id: string) => any;
  TextEncoder: { new (): { encode(input?: string): Uint8Array } };
  _ntohs: (value: number) => number;
  _htons: (value: number) => number;
  zeroMemory: (address: number, size: number) => void;
  assert: (check: any, message?: string) => void;
  abort: (message?: string) => never;
}) => {
  var ___syscall__newselect = function (nfds, readfds, writefds, exceptfds, timeout) {
    try {
      var total = 0;
      var srcReadLow = readfds ? HEAP32[readfds >> 2] : 0, srcReadHigh = readfds ? HEAP32[readfds + 4 >> 2] : 0;
      var srcWriteLow = writefds ? HEAP32[writefds >> 2] : 0, srcWriteHigh = writefds ? HEAP32[writefds + 4 >> 2] : 0;
      var srcExceptLow = exceptfds ? HEAP32[exceptfds >> 2] : 0, srcExceptHigh = exceptfds ? HEAP32[exceptfds + 4 >> 2] : 0;
      var dstReadLow = 0, dstReadHigh = 0;
      var dstWriteLow = 0, dstWriteHigh = 0;
      var dstExceptLow = 0, dstExceptHigh = 0;
      var allLow = (readfds ? HEAP32[readfds >> 2] : 0) | (writefds ? HEAP32[writefds >> 2] : 0) | (exceptfds ? HEAP32[exceptfds >> 2] : 0);
      var allHigh = (readfds ? HEAP32[readfds + 4 >> 2] : 0) | (writefds ? HEAP32[writefds + 4 >> 2] : 0) | (exceptfds ? HEAP32[exceptfds + 4 >> 2] : 0);
      var check = (fd, low, high, val) => fd < 32 ? low & val : high & val;
      for (var fd = 0;
        fd < nfds;
        fd++) {
        var mask = 1 << fd % 32;
        if (!check(fd, allLow, allHigh, mask)) { continue } var stream = SYSCALLS.getStreamFromFD(fd);
        var flags = SYSCALLS.DEFAULT_POLLMASK;
        if (stream.stream_ops.poll) {
          var timeoutInMillis = -1;
          if (timeout) {
            var tv_sec = readfds ? HEAP32[timeout >> 2] : 0, tv_usec = readfds ? HEAP32[timeout + 4 >> 2] : 0;
            timeoutInMillis = (tv_sec + tv_usec / 1e6) * 1e3
          } flags = stream.stream_ops.poll(stream, timeoutInMillis)
        }
        if (flags & 1 && check(fd, srcReadLow, srcReadHigh, mask)) {
          fd < 32 ? dstReadLow = dstReadLow | mask : dstReadHigh = dstReadHigh | mask;
          total++
        }
        if (flags & 4 && check(fd, srcWriteLow, srcWriteHigh, mask)) {
          fd < 32 ? dstWriteLow = dstWriteLow | mask : dstWriteHigh = dstWriteHigh | mask;
          total++
        }
        if (flags & 2 && check(fd, srcExceptLow, srcExceptHigh, mask)) {
          fd < 32 ? dstExceptLow = dstExceptLow | mask : dstExceptHigh = dstExceptHigh | mask;
          total++
        }
      }
      if (readfds) {
        HEAP32[readfds >> 2] = dstReadLow;
        HEAP32[readfds + 4 >> 2] = dstReadHigh
      }
      if (writefds) {
        HEAP32[writefds >> 2] = dstWriteLow;
        HEAP32[writefds + 4 >> 2] = dstWriteHigh
      }
      if (exceptfds) {
        HEAP32[exceptfds >> 2] = dstExceptLow;
        HEAP32[exceptfds + 4 >> 2] = dstExceptHigh
      } return total
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  };
  ___syscall__newselect.sig = "iipppp";
  var SOCKFS = createSOCKFS({ FS, Module, ENVIRONMENT_IS_NODE, require, assert, HEAP32, TextEncoder });
  var getSocketFromFD = fd => {
    var socket = SOCKFS.getSocket(fd);
    if (!socket) throw new FS.ErrnoError(8);
    return socket
  };
  var { inetNtop4, inetNtop6, readSockaddr, inetPton4, inetPton6, DNS, getSocketAddress, writeSockaddr } = createSocketAddressUtils({ HEAP16, HEAPU16, HEAP32, _ntohs, _htons, assert, FS, zeroMemory });
  function ___syscall_bind(fd, addr, addrlen, d1, d2, d3) {
    try {
      var sock = getSocketFromFD(fd);
      var info = getSocketAddress(addr, addrlen);
      sock.sock_ops.bind(sock, info.addr, info.port);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_bind.sig = "iippiii";
  function ___syscall_chdir(path) {
    try {
      path = SYSCALLS.getStr(path);
      FS.chdir(path);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_chdir.sig = "ip";
  function ___syscall_chmod(path, mode) {
    try {
      path = SYSCALLS.getStr(path);
      FS.chmod(path, mode);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_chmod.sig = "ipi";
  function ___syscall_dup(fd) {
    try {
      var old = SYSCALLS.getStreamFromFD(fd);
      return FS.dupStream(old).fd
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_dup.sig = "ii";
  function ___syscall_dup3(fd, newfd, flags) {
    try {
      var old = SYSCALLS.getStreamFromFD(fd);
      if (old.fd === newfd) return -28;
      if (newfd < 0 || newfd >= FS.MAX_OPEN_FDS) return -8;
      var existing = FS.getStream(newfd);
      if (existing) FS.close(existing);
      return FS.dupStream(old, newfd).fd
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_dup3.sig = "iiii";
  function ___syscall_faccessat(dirfd, path, amode, flags) {
    try {
      path = SYSCALLS.getStr(path);
      path = SYSCALLS.calculateAt(dirfd, path);
      if (amode & ~7) { return -28 } var lookup = FS.lookupPath(path, { follow: true });
      var node = lookup.node;
      if (!node) { return -44 } var perms = "";
      if (amode & 4) perms += "r";
      if (amode & 2) perms += "w";
      if (amode & 1) perms += "x";
      if (perms && FS.nodePermissions(node, perms)) { return -2 } return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_faccessat.sig = "iipii";
  var ___syscall_fadvise64 = (fd, offset, len, advice) => 0;
  ___syscall_fadvise64.sig = "iijji";
  var INT53_MAX = 9007199254740992;
  var INT53_MIN = -9007199254740992;
  var bigintToI53Checked = num => num < INT53_MIN || num > INT53_MAX ? NaN : Number(num);
  function ___syscall_fallocate(fd, mode, offset, len) {
    offset = bigintToI53Checked(offset);
    len = bigintToI53Checked(len);
    try {
      if (isNaN(offset)) return 61;
      var stream = SYSCALLS.getStreamFromFD(fd);
      FS.allocate(stream, offset, len);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_fallocate.sig = "iiijj";
  var syscallGetVarargI = () => {
    var ret = HEAP32[+SYSCALLS.varargs >> 2];
    SYSCALLS.varargs += 4;
    return ret
  };
  var syscallGetVarargP = syscallGetVarargI;
  function ___syscall_fcntl64(fd, cmd, varargs) {
    SYSCALLS.varargs = varargs;
    try {
      var stream = SYSCALLS.getStreamFromFD(fd);
      switch (cmd) {
        case 0: {
          var arg = syscallGetVarargI();
          if (arg < 0) { return -28 } while (FS.streams[arg]) { arg++ } var newStream;
          newStream = FS.dupStream(stream, arg);
          return newStream.fd
        } case 1: case 2: return 0;
        case 3: return stream.flags;
        case 4: {
          var arg = syscallGetVarargI();
          stream.flags |= arg;
          return 0
        } case 12: {
          var arg = syscallGetVarargP();
          var offset = 0;
          HEAP16[arg + offset >> 1] = 2;
          return 0
        } case 13: case 14: return 0
      }return -28
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_fcntl64.sig = "iiip";
  function ___syscall_fdatasync(fd) {
    try {
      var stream = SYSCALLS.getStreamFromFD(fd);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_fdatasync.sig = "ii";
  function ___syscall_fstat64(fd, buf) {
    try {
      var stream = SYSCALLS.getStreamFromFD(fd);
      return SYSCALLS.doStat(FS.stat, stream.path, buf)
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_fstat64.sig = "iip";
  function ___syscall_ftruncate64(fd, length) {
    length = bigintToI53Checked(length);
    try {
      if (isNaN(length)) return 61;
      FS.ftruncate(fd, length);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_ftruncate64.sig = "iij";
  var stringToUTF8 = (str, outPtr, maxBytesToWrite) => stringToUTF8Array(str, HEAPU8, outPtr, maxBytesToWrite);
  function ___syscall_getcwd(buf, size) {
    try {
      if (size === 0) return -28;
      var cwd = FS.cwd();
      var cwdLengthInBytes = lengthBytesUTF8(cwd) + 1;
      if (size < cwdLengthInBytes) return -68;
      stringToUTF8(cwd, buf, size);
      return cwdLengthInBytes
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_getcwd.sig = "ipp";
  function ___syscall_getdents64(fd, dirp, count) {
    try {
      var stream = SYSCALLS.getStreamFromFD(fd);
      stream.getdents ||= FS.readdir(stream.path);
      var struct_size = 280;
      var pos = 0;
      var off = FS.llseek(stream, 0, 1);
      var startIdx = Math.floor(off / struct_size);
      var endIdx = Math.min(stream.getdents.length, startIdx + Math.floor(count / struct_size));
      for (var idx = startIdx;
        idx < endIdx;
        idx++) {
        var id;
        var type;
        var name = stream.getdents[idx];
        if (name === ".") {
          id = stream.node.id;
          type = 4
        } else if (name === "..") {
          var lookup = FS.lookupPath(stream.path, { parent: true });
          id = lookup.node.id;
          type = 4
        } else {
          var child;
          try { child = FS.lookupNode(stream.node, name) } catch (e) { if (e?.errno === 28) { continue } throw e } id = child.id;
          type = FS.isChrdev(child.mode) ? 2 : FS.isDir(child.mode) ? 4 : FS.isLink(child.mode) ? 10 : 8
        } HEAP64[dirp + pos >> 3] = BigInt(id);
        HEAP64[dirp + pos + 8 >> 3] = BigInt((idx + 1) * struct_size);
        HEAP16[dirp + pos + 16 >> 1] = 280;
        HEAP8[dirp + pos + 18] = type;
        stringToUTF8(name, dirp + pos + 19, 256);
        pos += struct_size
      } FS.llseek(stream, idx * struct_size, 0);
      return pos
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_getdents64.sig = "iipp";
  function ___syscall_ioctl(fd, op, varargs) {
    SYSCALLS.varargs = varargs;
    try {
      var stream = SYSCALLS.getStreamFromFD(fd);
      switch (op) {
        case 21509: {
          if (!stream.tty) return -59;
          return 0
        } case 21505: {
          if (!stream.tty) return -59;
          if (stream.tty.ops.ioctl_tcgets) {
            var termios = stream.tty.ops.ioctl_tcgets(stream);
            var argp = syscallGetVarargP();
            HEAP32[argp >> 2] = termios.c_iflag || 0;
            HEAP32[argp + 4 >> 2] = termios.c_oflag || 0;
            HEAP32[argp + 8 >> 2] = termios.c_cflag || 0;
            HEAP32[argp + 12 >> 2] = termios.c_lflag || 0;
            for (var i = 0;
              i < 32;
              i++) { HEAP8[argp + i + 17] = termios.c_cc[i] || 0 } return 0
          } return 0
        } case 21510: case 21511: case 21512: {
          if (!stream.tty) return -59;
          return 0
        } case 21506: case 21507: case 21508: {
          if (!stream.tty) return -59;
          if (stream.tty.ops.ioctl_tcsets) {
            var argp = syscallGetVarargP();
            var c_iflag = HEAP32[argp >> 2];
            var c_oflag = HEAP32[argp + 4 >> 2];
            var c_cflag = HEAP32[argp + 8 >> 2];
            var c_lflag = HEAP32[argp + 12 >> 2];
            var c_cc = [];
            for (var i = 0;
              i < 32;
              i++) { c_cc.push(HEAP8[argp + i + 17]) } return stream.tty.ops.ioctl_tcsets(stream.tty, op, { c_iflag, c_oflag, c_cflag, c_lflag, c_cc })
          } return 0
        } case 21519: {
          if (!stream.tty) return -59;
          var argp = syscallGetVarargP();
          HEAP32[argp >> 2] = 0;
          return 0
        } case 21520: {
          if (!stream.tty) return -59;
          return -28
        } case 21531: {
          var argp = syscallGetVarargP();
          return FS.ioctl(stream, op, argp)
        } case 21523: {
          if (!stream.tty) return -59;
          if (stream.tty.ops.ioctl_tiocgwinsz) {
            var winsize = stream.tty.ops.ioctl_tiocgwinsz(stream.tty);
            var argp = syscallGetVarargP();
            HEAP16[argp >> 1] = winsize[0];
            HEAP16[argp + 2 >> 1] = winsize[1]
          } return 0
        } case 21524: {
          if (!stream.tty) return -59;
          return 0
        } case 21515: {
          if (!stream.tty) return -59;
          return 0
        } default: return -28
      }
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_ioctl.sig = "iiip";
  function ___syscall_lstat64(path, buf) {
    try {
      path = SYSCALLS.getStr(path);
      return SYSCALLS.doStat(FS.lstat, path, buf)
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_lstat64.sig = "ipp";
  function ___syscall_mkdirat(dirfd, path, mode) {
    try {
      path = SYSCALLS.getStr(path);
      path = SYSCALLS.calculateAt(dirfd, path);
      FS.mkdir(path, mode, 0);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_mkdirat.sig = "iipi";
  function ___syscall_newfstatat(dirfd, path, buf, flags) {
    try {
      path = SYSCALLS.getStr(path);
      var nofollow = flags & 256;
      var allowEmpty = flags & 4096;
      flags = flags & ~6400;
      path = SYSCALLS.calculateAt(dirfd, path, allowEmpty);
      return SYSCALLS.doStat(nofollow ? FS.lstat : FS.stat, path, buf)
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_newfstatat.sig = "iippi";
  function ___syscall_openat(dirfd, path, flags, varargs) {
    SYSCALLS.varargs = varargs;
    try {
      path = SYSCALLS.getStr(path);
      path = SYSCALLS.calculateAt(dirfd, path);
      var mode = varargs ? syscallGetVarargI() : 0;
      return FS.open(path, flags, mode).fd
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_openat.sig = "iipip";
  var PIPEFS = createPIPEFS({ getFS: () => FS, assert, getHEAP32: () => HEAP32 });
  function ___syscall_pipe(fdPtr) {
    try {
      if (fdPtr == 0) { throw new FS.ErrnoError(21) } var res = PIPEFS.createPipe();
      HEAP32[fdPtr >> 2] = res.readable_fd;
      HEAP32[fdPtr + 4 >> 2] = res.writable_fd;
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_pipe.sig = "ip";
  function ___syscall_readlinkat(dirfd, path, buf, bufsize) {
    try {
      path = SYSCALLS.getStr(path);
      path = SYSCALLS.calculateAt(dirfd, path);
      if (bufsize <= 0) return -28;
      var ret = FS.readlink(path);
      var len = Math.min(bufsize, lengthBytesUTF8(ret));
      var endChar = HEAP8[buf + len];
      stringToUTF8(ret, buf, bufsize + 1);
      HEAP8[buf + len] = endChar;
      return len
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_readlinkat.sig = "iippp";
  function ___syscall_recvfrom(fd, buf, len, flags, addr, addrlen) {
    try {
      var sock = getSocketFromFD(fd);
      var msg = sock.sock_ops.recvmsg(sock, len);
      if (!msg) return 0;
      if (addr) { var errno = writeSockaddr(addr, sock.family, DNS.lookup_name(msg.addr), msg.port, addrlen) } HEAPU8.set(msg.buffer, buf);
      return msg.buffer.byteLength
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_recvfrom.sig = "iippipp";
  function ___syscall_renameat(olddirfd, oldpath, newdirfd, newpath) {
    try {
      oldpath = SYSCALLS.getStr(oldpath);
      newpath = SYSCALLS.getStr(newpath);
      oldpath = SYSCALLS.calculateAt(olddirfd, oldpath);
      newpath = SYSCALLS.calculateAt(newdirfd, newpath);
      FS.rename(oldpath, newpath);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_renameat.sig = "iipip";
  function ___syscall_rmdir(path) {
    try {
      path = SYSCALLS.getStr(path);
      FS.rmdir(path);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_rmdir.sig = "ip";
  function ___syscall_sendto(fd, message, length, flags, addr, addr_len) {
    try {
      var sock = getSocketFromFD(fd);
      if (!addr) { return FS.write(sock.stream, HEAP8, message, length) } var dest = getSocketAddress(addr, addr_len);
      return sock.sock_ops.sendmsg(sock, HEAP8, message, length, dest.addr, dest.port)
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_sendto.sig = "iippipp";
  function ___syscall_socket(domain, type, protocol) {
    try {
      var sock = SOCKFS.createSocket(domain, type, protocol);
      return sock.stream.fd
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_socket.sig = "iiiiiii";
  function ___syscall_stat64(path, buf) {
    try {
      path = SYSCALLS.getStr(path);
      return SYSCALLS.doStat(FS.stat, path, buf)
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_stat64.sig = "ipp";
  function ___syscall_symlinkat(target, dirfd, linkpath) {
    try {
      target = SYSCALLS.getStr(target);
      linkpath = SYSCALLS.getStr(linkpath);
      linkpath = SYSCALLS.calculateAt(dirfd, linkpath);
      FS.symlink(target, linkpath);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_symlinkat.sig = "ipip";
  function ___syscall_truncate64(path, length) {
    length = bigintToI53Checked(length);
    try {
      if (isNaN(length)) return 61;
      path = SYSCALLS.getStr(path);
      FS.truncate(path, length);
      return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_truncate64.sig = "ipj";
  function ___syscall_unlinkat(dirfd, path, flags) {
    try {
      path = SYSCALLS.getStr(path);
      path = SYSCALLS.calculateAt(dirfd, path);
      if (flags === 0) { FS.unlink(path) } else if (flags === 512) { FS.rmdir(path) } else { abort("Invalid flags passed to unlinkat") } return 0
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno
    }
  } ___syscall_unlinkat.sig = "iipi";
  return { ___syscall__newselect, ___syscall_bind, ___syscall_chdir, ___syscall_chmod, ___syscall_dup, ___syscall_dup3, ___syscall_faccessat, ___syscall_fadvise64, ___syscall_fallocate, ___syscall_fcntl64, ___syscall_fdatasync, ___syscall_fstat64, ___syscall_ftruncate64, ___syscall_getcwd, ___syscall_getdents64, ___syscall_ioctl, ___syscall_lstat64, ___syscall_mkdirat, ___syscall_newfstatat, ___syscall_openat, ___syscall_pipe, ___syscall_readlinkat, ___syscall_recvfrom, ___syscall_renameat, ___syscall_rmdir, ___syscall_sendto, ___syscall_socket, ___syscall_stat64, ___syscall_symlinkat, ___syscall_truncate64, ___syscall_unlinkat, bigintToI53Checked, stringToUTF8, PIPEFS, SOCKFS, inetNtop4, inetNtop6, readSockaddr, inetPton4, inetPton6, DNS, getSocketAddress, writeSockaddr };
};
