import { PATH } from "./path.ts";

export const createSYSCALLS = ({
  getFS,
  getHEAP32,
  getHEAPU32,
  getHEAP64,
  getHEAP8,
  getHEAPU8,
  UTF8ToString,
}: {
  getFS: () => any;
  getHEAP32: () => Int32Array;
  getHEAPU32: () => Uint32Array;
  getHEAP64: () => BigInt64Array;
  getHEAP8: () => Int8Array;
  getHEAPU8: () => Uint8Array;
  UTF8ToString: (ptr: number, maxBytesToRead?: number) => string;
}) => {
  const FS = new Proxy({}, {
    get: (_: any, prop: any) => getFS()[prop],
  });
  const createHeap = (getter: any) => new Proxy({}, {
    get: (_: any, prop: any) => getter()[prop],
    set: (_: any, prop: any, value: any) => { getter()[prop] = value; return true; },
  });
  const HEAP32 = createHeap(getHEAP32);
  const HEAPU32 = createHeap(getHEAPU32);
  const HEAP64 = createHeap(getHEAP64);
  const HEAP8 = createHeap(getHEAP8);
  const HEAPU8 = createHeap(getHEAPU8);
  const SYSCALLS = {DEFAULT_POLLMASK:5,calculateAt(dirfd,path,allowEmpty){if(PATH.isAbs(path)){return path}var dir;if(dirfd===-100){dir=FS.cwd()}else{var dirstream=SYSCALLS.getStreamFromFD(dirfd);dir=dirstream.path}if(path.length==0){if(!allowEmpty){throw new FS.ErrnoError(44)}return dir}return dir+"/"+path},doStat(func,path,buf){var stat=func(path);HEAP32[buf>>2]=stat.dev;HEAP32[buf+4>>2]=stat.mode;HEAPU32[buf+8>>2]=stat.nlink;HEAP32[buf+12>>2]=stat.uid;HEAP32[buf+16>>2]=stat.gid;HEAP32[buf+20>>2]=stat.rdev;HEAP64[buf+24>>3]=BigInt(stat.size);HEAP32[buf+32>>2]=4096;HEAP32[buf+36>>2]=stat.blocks;var atime=stat.atime.getTime();var mtime=stat.mtime.getTime();var ctime=stat.ctime.getTime();HEAP64[buf+40>>3]=BigInt(Math.floor(atime/1e3));HEAPU32[buf+48>>2]=atime%1e3*1e3*1e3;HEAP64[buf+56>>3]=BigInt(Math.floor(mtime/1e3));HEAPU32[buf+64>>2]=mtime%1e3*1e3*1e3;HEAP64[buf+72>>3]=BigInt(Math.floor(ctime/1e3));HEAPU32[buf+80>>2]=ctime%1e3*1e3*1e3;HEAP64[buf+88>>3]=BigInt(stat.ino);return 0},doMsync(addr,stream,len,flags,offset){if(!FS.isFile(stream.node.mode)){throw new FS.ErrnoError(43)}if(flags&2){return 0}var buffer=HEAPU8.slice(addr,addr+len);FS.msync(stream,buffer,offset,len,flags)},getStreamFromFD(fd){var stream=FS.getStreamChecked(fd);return stream},varargs:undefined,getStr(ptr){var ret=UTF8ToString(ptr);return ret}};
  return SYSCALLS;
};
