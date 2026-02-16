export const createFdWasiFunctions = ({
  SYSCALLS,
  FS,
  HEAPU32,
  HEAP64,
  HEAP16,
  HEAP8,
  bigintToI53Checked,
}: {
  SYSCALLS: any;
  FS: any;
  HEAPU32: Uint32Array;
  HEAP64: BigInt64Array;
  HEAP16: Int16Array;
  HEAP8: Int8Array;
  bigintToI53Checked: (num: number | bigint) => number;
}) => {
function _fd_close(fd: any) {
        try {
          var stream = SYSCALLS.getStreamFromFD(fd);
          FS.close(stream);
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      } _fd_close.sig = "ii";
      function _fd_fdstat_get(fd: any, pbuf: any) {
        try {
          var rightsBase = 0;
          var rightsInheriting = 0;
          var flags = 0;
            var stream = SYSCALLS.getStreamFromFD(fd);
            var type = stream.tty ? 2 : FS.isDir(stream.mode) ? 3 : FS.isLink(stream.mode) ? 7 : 4HEAP8[pbuf] = type;
          HEAP16[pbuf + 2 >> 1] = flags;
          HEAP64[pbuf + 8 >> 3] = BigInt(rightsBase);
          HEAP64[pbuf + 16 >> 3] = BigInt(rightsInheriting);
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      } _fd_fdstat_get.sig = "iip";
      var doReadv = (stream: any, iov: any, iovcnt: any, offset?: any) => {
        var ret = 0;
        for (var i = 0;
          i < iovcnt;
          i++) {
          var ptr = HEAPU32[iov >> 2];
          var len = HEAPU32[iov + 4 >> 2];
          iov += 8;
          var curr = FS.read(stream, HEAP8, ptr, len, offset);
          if (curr < 0) return -1;
          ret += curr;
          if (curr < len) break;
          if (typeof offset != "undefined") { offset += curr }
        } return ret
      };
      function _fd_pread(fd: any, iov: any, iovcnt: any, offset: any, pnum: any) {
        offset = bigintToI53Checked(offset);
        try {
          if (isNaN(offset)) return 61;
          var stream = SYSCALLS.getStreamFromFD(fd);
          var num = doReadv(stream, iov, iovcnt, offset);
          HEAPU32[pnum >> 2] = num;
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      } _fd_pread.sig = "iippjp";
      var doWritev = (stream: any, iov: any, iovcnt: any, offset?: any) => {
        var ret = 0;
        for (var i = 0;
          i < iovcnt;
          i++) {
          var ptr = HEAPU32[iov >> 2];
          var len = HEAPU32[iov + 4 >> 2];
          iov += 8;
          var curr = FS.write(stream, HEAP8, ptr, len, offset);
          if (curr < 0) return -1;
          ret += curr;
          if (curr < len) { break }
          if (typeof offset != "undefined") { offset += curr }
        } return ret
      };
      function _fd_pwrite(fd: any, iov: any, iovcnt: any, offset: any, pnum: any) {
        offset = bigintToI53Checked(offset);
        try {
          if (isNaN(offset)) return 61;
          var stream = SYSCALLS.getStreamFromFD(fd);
          var num = doWritev(stream, iov, iovcnt, offset);
          HEAPU32[pnum >> 2] = num;
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      }
      _fd_pwrite.sig = "iippjp";
      function _fd_read(fd: any, iov: any, iovcnt: any, pnum: any) {
        try {
          var stream = SYSCALLS.getStreamFromFD(fd);
          var num = doReadv(stream, iov, iovcnt);
          HEAPU32[pnum >> 2] = num;
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      }
      _fd_read.sig = "iippp";
      function _fd_seek(fd: any, offset: any, whence: any, newOffset: any) {
        offset = bigintToI53Checked(offset);
        try {
          if (isNaN(offset)) return 61;
          var stream = SYSCALLS.getStreamFromFD(fd);
          FS.llseek(stream, offset, whence);
          HEAP64[newOffset >> 3] = BigInt(stream.position);
          if (stream.getdents && offset === 0 && whence === 0) stream.getdents = null;
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      } _fd_seek.sig = "iijip";
      function _fd_sync(fd: any) {
        try {
          var stream = SYSCALLS.getStreamFromFD(fd);
          if (stream.stream_ops?.fsync) { return stream.stream_ops.fsync(stream) } return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      } _fd_sync.sig = "ii";
      function _fd_write(fd: any, iov: any, iovcnt: any, pnum: any) {
        try {
          var stream = SYSCALLS.getStreamFromFD(fd);
          var num = doWritev(stream, iov, iovcnt);
          HEAPU32[pnum >> 2] = num;
          return 0
        } catch (e) {
          if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
          return e.errno
        }
      } _fd_write.sig = "iippp";
      
return { _fd_close, _fd_fdstat_get, _fd_pread, _fd_pwrite, _fd_read, _fd_seek, _fd_sync, _fd_write };
};
