export const createAddrInfoFunctions = ({
  _malloc,
  HEAP32,
  HEAPU32,
  UTF8ToString,
  inetNtop6,
  inetNtop4,
  writeSockaddr,
  assert,
  _htonl,
  inetPton4,
  inetPton6,
  DNS,
  readSockaddr,
  stringToUTF8,
}: {
  _malloc: (size: number) => number;
  HEAP32: Int32Array;
  HEAPU32: Uint32Array;
  UTF8ToString: (ptr: number, maxBytesToRead?: number) => string;
  inetNtop6: (ints: number[]) => string;
  inetNtop4: (addr: number) => string;
  writeSockaddr: (...args: any[]) => number;
  assert: (check: any, message?: string) => void;
  _htonl: (value: number) => number;
  inetPton4: (str: string) => number | null;
  inetPton6: (str: string) => number[] | null;
  DNS: any;
  readSockaddr: (...args: any[]) => any;
  stringToUTF8: (str: string, outPtr: number, maxBytesToWrite: number) => number;
}) => {
var _getaddrinfo = (node, service, hint, out) => {
        var addr = 0;
        var port = 0;
        var flags = 0;
        var family = 0;
        var type = 0;
        var proto = 0;
        var ai;
        function allocaddrinfo(family, type, proto, canon, addr, port) {
          var sa, salen, ai;
          var errno;
          salen = family === 10 ? 28 : 16;
          addr = family === 10 ? inetNtop6(addr) : inetNtop4(addr);
          sa = _malloc(salen);
          errno = writeSockaddr(sa, family, addr, port);
          assert(!errno);
          ai = _malloc(32);
          HEAP32[ai + 4 >> 2] = family;
          HEAP32[ai + 8 >> 2] = type;
          HEAP32[ai + 12 >> 2] = proto;
          HEAPU32[ai + 24 >> 2] = canon;
          HEAPU32[ai + 20 >> 2] = sa;
          if (family === 10) { HEAP32[ai + 16 >> 2] = 28 } else { HEAP32[ai + 16 >> 2] = 16 } HEAP32[ai + 28 >> 2] = 0;
          return ai
        }
        if (hint) {
          flags = HEAP32[hint >> 2];
          family = HEAP32[hint + 4 >> 2];
          type = HEAP32[hint + 8 >> 2];
          proto = HEAP32[hint + 12 >> 2]
        }
        if (type && !proto) { proto = type === 2 ? 17 : 6 }
        if (!type && proto) { type = proto === 17 ? 2 : 1 }
        if (proto === 0) { proto = 6 }
        if (type === 0) { type = 1 }
        if (!node && !service) { return -2 }
        if (flags & ~(1 | 2 | 4 | 1024 | 8 | 16 | 32)) { return -1 }
        if (hint !== 0 && HEAP32[hint >> 2] & 2 && !node) { return -1 }
        if (flags & 32) { return -2 }
        if (type !== 0 && type !== 1 && type !== 2) { return -7 }
        if (family !== 0 && family !== 2 && family !== 10) { return -6 }
        if (service) {
          service = UTF8ToString(service);
          port = parseInt(service, 10);
          if (isNaN(port)) { if (flags & 1024) { return -2 } return -8 }
        }
        if (!node) {
          if (family === 0) { family = 2 }
          if ((flags & 1) === 0) { if (family === 2) { addr = _htonl(2130706433) } else { addr = [0, 0, 0, _htonl(1)] } } ai = allocaddrinfo(family, type, proto, null, addr, port);
          HEAPU32[out >> 2] = ai;
          return 0
        } node = UTF8ToString(node);
        addr = inetPton4(node);
        if (addr !== null) {
          if (family === 0 || family === 2) { family = 2 } else if (family === 10 && flags & 8) {
            addr = [0, 0, _htonl(65535), addr];
            family = 10
          } else { return -2 }
        } else {
          addr = inetPton6(node);
          if (addr !== null) { if (family === 0 || family === 10) { family = 10 } else { return -2 } }
        }
        if (addr != null) {
          ai = allocaddrinfo(family, type, proto, node, addr, port);
          HEAPU32[out >> 2] = ai;
          return 0
        }
        if (flags & 4) { return -2 } node = DNS.lookup_name(node);
        addr = inetPton4(node);
        if (family === 0) { family = 2 } else if (family === 10) { addr = [0, 0, _htonl(65535), addr] } ai = allocaddrinfo(family, type, proto, null, addr, port);
        HEAPU32[out >> 2] = ai;
        return 0
      };
      _getaddrinfo.sig = "ipppp";
      var _getnameinfo = (sa, salen, node, nodelen, serv, servlen, flags) => {
        var info = readSockaddr(sa, salen);
        if (info.errno) { return -6 } var port = info.port;
        var addr = info.addr;
        var overflowed = false;
        if (node && nodelen) {
          var lookup;
          if (flags & 1 || !(lookup = DNS.lookup_addr(addr))) { if (flags & 8) { return -2 } } else { addr = lookup } var numBytesWrittenExclNull = stringToUTF8(addr, node, nodelen);
          if (numBytesWrittenExclNull + 1 >= nodelen) { overflowed = true }
        }
        if (serv && servlen) {
          port = "" + port;
          var numBytesWrittenExclNull = stringToUTF8(port, serv, servlen);
          if (numBytesWrittenExclNull + 1 >= servlen) { overflowed = true }
        }
        if (overflowed) { return -12 } return 0
      };
      _getnameinfo.sig = "ipipipii";
      
return { _getaddrinfo, _getnameinfo };
};
