import { ExitStatus } from "./exit-status.ts";

export const createRuntimeUtils = ({
  Module,
  getABORT,
  setABORT,
  getEXITSTATUS,
  setEXITSTATUS,
  getNoExitRuntime,
  setNoExitRuntime,
  quit_,
  ENVIRONMENT_IS_NODE,
  UTF8ToString,
  require,
  bigintToI53Checked,
  HEAP32,
  HEAP64,
  HEAPU32,
  stringToUTF8,
  SYSCALLS,
  FS,
  getEmscriptenTimeout,
}: {
  Module: Record<string, any>;
  getABORT: () => boolean;
  setABORT: (value: boolean) => void;
  getEXITSTATUS: () => number;
  setEXITSTATUS: (value: number) => void;
  getNoExitRuntime: () => boolean;
  setNoExitRuntime: (value: boolean) => void;
  quit_: (status: number, toThrow: any) => never;
  ENVIRONMENT_IS_NODE: boolean;
  UTF8ToString: (ptr: number, maxBytesToRead?: number) => string;
  require?: (id: string) => any;
  bigintToI53Checked: (num: number | bigint) => number;
  HEAP32: Int32Array;
  HEAP64: BigInt64Array;
  HEAPU32: Uint32Array;
  stringToUTF8: (str: string, outPtr: number, maxBytesToWrite: number) => number;
  SYSCALLS: any;
  FS: any;
  getEmscriptenTimeout: () => (...args: any[]) => any;
}) => {
  var runtimeKeepaliveCounter = 0;
  var __emscripten_runtime_keepalive_clear = () => {
    setNoExitRuntime(false);
    runtimeKeepaliveCounter = 0;
  };
  __emscripten_runtime_keepalive_clear.sig = "v";
  var __emscripten_system = (command: any) => {
    if (ENVIRONMENT_IS_NODE) {
      if (!command) return 1;
      var cmdstr = UTF8ToString(command);
      if (!cmdstr.length) return 0;
      var cp = require("child_process");
      var ret = cp.spawnSync(cmdstr, [], { shell: true, stdio: "inherit" });
      var _W_EXITCODE = (ret: any, sig: any) => ret << 8 | sig;
      if (ret.status === null) {
        var signalToNumber = (sig: any) => {
          switch (sig) {
            case "SIGHUP": return 1;
            case "SIGQUIT": return 3;
            case "SIGFPE": return 8;
            case "SIGKILL": return 9;
            case "SIGALRM": return 14;
            case "SIGTERM": return 15;
            default: return 2;
          }
        };
        return _W_EXITCODE(0, signalToNumber(ret.signal));
      }
      return _W_EXITCODE(ret.status, 0);
    }
    if (!command) return 0;
    return -52;
  };
  __emscripten_system.sig = "ip";
  var __emscripten_throw_longjmp = () => {
    throw Infinity;
  };
  __emscripten_throw_longjmp.sig = "v";
  function __gmtime_js(time: any, tmPtr: any) {
    time = bigintToI53Checked(time);
    var date = new Date(time * 1e3);
    HEAP32[tmPtr >> 2] = date.getUTCSeconds();
    HEAP32[tmPtr + 4 >> 2] = date.getUTCMinutes();
    HEAP32[tmPtr + 8 >> 2] = date.getUTCHours();
    HEAP32[tmPtr + 12 >> 2] = date.getUTCDate();
    HEAP32[tmPtr + 16 >> 2] = date.getUTCMonth();
    HEAP32[tmPtr + 20 >> 2] = date.getUTCFullYear() - 1900;
    HEAP32[tmPtr + 24 >> 2] = date.getUTCDay();
    var start = Date.UTC(date.getUTCFullYear(), 0, 1, 0, 0, 0, 0);
    var yday = (date.getTime() - start) / (1e3 * 60 * 60 * 24) | 0;
    HEAP32[tmPtr + 28 >> 2] = yday;
  }
  __gmtime_js.sig = "vjp";
  var isLeapYear = (year: any) => year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0);
  var MONTH_DAYS_LEAP_CUMULATIVE = [0, 31, 60, 91, 121, 152, 182, 213, 244, 274, 305, 335];
  var MONTH_DAYS_REGULAR_CUMULATIVE = [0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334];
  var ydayFromDate = (date: any) => {
    var leap = isLeapYear(date.getFullYear());
    var monthDaysCumulative = leap ? MONTH_DAYS_LEAP_CUMULATIVE : MONTH_DAYS_REGULAR_CUMULATIVE;
    var yday = monthDaysCumulative[date.getMonth()] + date.getDate() - 1;
    return yday;
  };
  function __localtime_js(time: any, tmPtr: any) {
    time = bigintToI53Checked(time);
    var date = new Date(time * 1e3);
    HEAP32[tmPtr >> 2] = date.getSeconds();
    HEAP32[tmPtr + 4 >> 2] = date.getMinutes();
    HEAP32[tmPtr + 8 >> 2] = date.getHours();
    HEAP32[tmPtr + 12 >> 2] = date.getDate();
    HEAP32[tmPtr + 16 >> 2] = date.getMonth();
    HEAP32[tmPtr + 20 >> 2] = date.getFullYear() - 1900;
    HEAP32[tmPtr + 24 >> 2] = date.getDay();
    var yday = ydayFromDate(date) | 0;
    HEAP32[tmPtr + 28 >> 2] = yday;
    HEAP32[tmPtr + 36 >> 2] = -(date.getTimezoneOffset() * 60);
    var start = new Date(date.getFullYear(), 0, 1);
    var summerOffset = new Date(date.getFullYear(), 6, 1).getTimezoneOffset();
    var winterOffset = start.getTimezoneOffset();
    var dst = Number(
      summerOffset != winterOffset &&
        date.getTimezoneOffset() == Math.min(winterOffset, summerOffset),
    );
    HEAP32[tmPtr + 32 >> 2] = dst;
  }
  __localtime_js.sig = "vjp";
  function __mmap_js(len: any, prot: any, flags: any, fd: any, offset: any, allocated: any, addr: any) {
    offset = bigintToI53Checked(offset);
    try {
      if (isNaN(offset)) return 61;
      var stream = SYSCALLS.getStreamFromFD(fd);
      var res = FS.mmap(stream, len, offset, prot, flags);
      var ptr = res.ptr;
      HEAP32[allocated >> 2] = res.allocated;
      HEAPU32[addr >> 2] = ptr;
      return 0;
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno;
    }
  }
  __mmap_js.sig = "ipiiijpp";
  function __munmap_js(addr: any, len: any, prot: any, flags: any, fd: any, offset: any) {
    offset = bigintToI53Checked(offset);
    try {
      var stream = SYSCALLS.getStreamFromFD(fd);
      if (prot & 2) {
        SYSCALLS.doMsync(addr, stream, len, flags, offset);
      }
    } catch (e) {
      if (typeof FS == "undefined" || !(e.name === "ErrnoError")) throw e;
      return -e.errno;
    }
  }
  __munmap_js.sig = "ippiiij";
  var handleException = (e: any) => {
    if (e instanceof ExitStatus || e == "unwind") {
      return getEXITSTATUS();
    }
    quit_(1, e);
  };
  var keepRuntimeAlive = () => getNoExitRuntime() || runtimeKeepaliveCounter > 0;
  var _proc_exit = (code: any) => {
    setEXITSTATUS(code);
    if (!keepRuntimeAlive()) {
      Module["onExit"]?.(code);
      setABORT(true);
    }
    quit_(code, new ExitStatus(code));
  };
  _proc_exit.sig = "vi";
  var exitJS = (status: any, implicit?: any) => {
    setEXITSTATUS(status);
    _proc_exit(status);
  };
  var _exit = exitJS;
  _exit.sig = "vi";
  var maybeExit = () => {
    if (!keepRuntimeAlive()) {
      try {
        _exit(getEXITSTATUS());
      } catch (e) {
        handleException(e);
      }
    }
  };
  var callUserCallback = (func: any) => {
    if (getABORT()) {
      return;
    }
    try {
      func();
      maybeExit();
    } catch (e) {
      handleException(e);
    }
  };
  var _emscripten_get_now = () => performance.now();
  _emscripten_get_now.sig = "d";
  var timers = {};
  var __setitimer_js = (which: any, timeout_ms: any) => {
    if (timers[which]) {
      clearTimeout(timers[which].id);
      delete timers[which];
    }
    if (!timeout_ms) return 0;
    var id = setTimeout(() => {
      delete timers[which];
      callUserCallback(() => getEmscriptenTimeout()(which, _emscripten_get_now()));
    }, timeout_ms);
    timers[which] = { id, timeout_ms };
    return 0;
  };
  __setitimer_js.sig = "iid";
  var __tzset_js = (timezone: any, daylight: any, std_name: any, dst_name: any) => {
    var currentYear = (new Date).getFullYear();
    var winter = new Date(currentYear, 0, 1);
    var summer = new Date(currentYear, 6, 1);
    var winterOffset = winter.getTimezoneOffset();
    var summerOffset = summer.getTimezoneOffset();
    var stdTimezoneOffset = Math.max(winterOffset, summerOffset);
    HEAPU32[timezone >> 2] = stdTimezoneOffset * 60;
    HEAP32[daylight >> 2] = Number(winterOffset != summerOffset);
    var extractZone = (timezoneOffset: any) => {
      var sign = timezoneOffset >= 0 ? "-" : "+";
      var absOffset = Math.abs(timezoneOffset);
      var hours = String(Math.floor(absOffset / 60)).padStart(2, "0");
      var minutes = String(absOffset % 60).padStart(2, "0");
      return `UTC${sign}${hours}${minutes}`;
    };
    var winterName = extractZone(winterOffset);
    var summerName = extractZone(summerOffset);
    if (summerOffset < winterOffset) {
      stringToUTF8(winterName, std_name, 17);
      stringToUTF8(summerName, dst_name, 17);
    } else {
      stringToUTF8(winterName, dst_name, 17);
      stringToUTF8(summerName, std_name, 17);
    }
  };
  __tzset_js.sig = "vpppp";
  var _emscripten_date_now = () => Date.now();
  _emscripten_date_now.sig = "d";
  var nowIsMonotonic = 1;
  var checkWasiClock = (clock_id: any) => clock_id >= 0 && clock_id <= 3;
  function _clock_time_get(clk_id: any, ignored_precision: any, ptime: any) {
    ignored_precision = bigintToI53Checked(ignored_precision);
    if (!checkWasiClock(clk_id)) {
      return 28;
    }
    var now;
    if (clk_id === 0) {
      now = _emscripten_date_now();
    } else if (nowIsMonotonic) {
      now = _emscripten_get_now();
    } else {
      return 52;
    }
    var nsec = Math.round(now * 1e3 * 1e3);
    HEAP64[ptime >> 3] = BigInt(nsec);
    return 0;
  }
  _clock_time_get.sig = "iijp";
  var _emscripten_force_exit = (status: any) => {
    __emscripten_runtime_keepalive_clear();
    _exit(status);
  };
  _emscripten_force_exit.sig = "vi";
  return {
    __emscripten_runtime_keepalive_clear,
    __emscripten_system,
    __emscripten_throw_longjmp,
    __gmtime_js,
    __localtime_js,
    __mmap_js,
    __munmap_js,
    _proc_exit,
    _exit,
    _emscripten_get_now,
    __setitimer_js,
    __tzset_js,
    _emscripten_date_now,
    _clock_time_get,
    _emscripten_force_exit,
    handleException,
  };
};
