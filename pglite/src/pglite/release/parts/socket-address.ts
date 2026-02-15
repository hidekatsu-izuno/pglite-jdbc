export const createSocketAddressUtils = ({ HEAP16, HEAPU16, HEAP32, _ntohs, _htons, assert, FS, zeroMemory }) => {
var inetNtop4 = addr => (addr & 255) + "." + (addr >> 8 & 255) + "." + (addr >> 16 & 255) + "." + (addr >> 24 & 255);
      var inetNtop6 = ints => {
        var str = "";
        var word = 0;
        var longest = 0;
        var lastzero = 0;
        var zstart = 0;
        var len = 0;
        var i = 0;
        var parts = [ints[0] & 65535, ints[0] >> 16, ints[1] & 65535, ints[1] >> 16, ints[2] & 65535, ints[2] >> 16, ints[3] & 65535, ints[3] >> 16];
        var hasipv4 = true;
        var v4part = "";
        for (i = 0;
          i < 5;
          i++) {
          if (parts[i] !== 0) {
            hasipv4 = false;
            break
          }
        }
        if (hasipv4) {
          v4part = inetNtop4(parts[6] | parts[7] << 16);
          if (parts[5] === -1) {
            str = "::ffff:";
            str += v4part;
            return str
          }
          if (parts[5] === 0) {
            str = "::";
            if (v4part === "0.0.0.0") v4part = "";
            if (v4part === "0.0.0.1") v4part = "1";
            str += v4part;
            return str
          }
        } for (word = 0;
          word < 8;
          word++) {
          if (parts[word] === 0) {
            if (word - lastzero > 1) { len = 0 } lastzero = word;
            len++
          }
          if (len > longest) {
            longest = len;
            zstart = word - longest + 1
          }
        } for (word = 0;
          word < 8;
          word++) {
          if (longest > 1) {
            if (parts[word] === 0 && word >= zstart && word < zstart + longest) {
              if (word === zstart) {
                str += ":";
                if (zstart === 0) str += ":"
              } continue
            }
          } str += Number(_ntohs(parts[word] & 65535)).toString(16);
          str += word < 7 ? ":" : ""
        } return str
      };
      var readSockaddr = (sa, salen) => {
        var family = HEAP16[sa >> 1];
        var port = _ntohs(HEAPU16[sa + 2 >> 1]);
        var addr;
        switch (family) {
          case 2: if (salen !== 16) { return { errno: 28 } } addr = HEAP32[sa + 4 >> 2];
            addr = inetNtop4(addr);
            break;
          case 10: if (salen !== 28) { return { errno: 28 } } addr = [HEAP32[sa + 8 >> 2], HEAP32[sa + 12 >> 2], HEAP32[sa + 16 >> 2], HEAP32[sa + 20 >> 2]];
            addr = inetNtop6(addr);
            break;
          default: return { errno: 5 }
        }return { family, addr, port }
      };
      var inetPton4 = str => {
        var b = str.split(".");
        for (var i = 0;
          i < 4;
          i++) {
          var tmp = Number(b[i]);
          if (isNaN(tmp)) return null;
          b[i] = tmp
        } return (b[0] | b[1] << 8 | b[2] << 16 | b[3] << 24) >>> 0
      };
      var jstoi_q = str => parseInt(str);
      var inetPton6 = str => {
        var words;
        var w, offset, z;
        var valid6regx = /^((?=.*::)(?!.*::.+::)(::)?([\dA-F]{1,4}:(:|\b)|){5}|([\dA-F]{1,4}:){6})((([\dA-F]{1,4}((?!\3)::|:\b|$))|(?!\2\3)){2}|(((2[0-4]|1\d|[1-9])?\d|25[0-5])\.?\b){4})$/i;
        var parts = [];
        if (!valid6regx.test(str)) { return null }
        if (str === "::") { return [0, 0, 0, 0, 0, 0, 0, 0] }
        if (str.startsWith("::")) { str = str.replace("::", "Z:") } else { str = str.replace("::", ":Z:") }
        if (str.indexOf(".") > 0) {
          str = str.replace(new RegExp("[.]", "g"), ":");
          words = str.split(":");
          words[words.length - 4] = jstoi_q(words[words.length - 4]) + jstoi_q(words[words.length - 3]) * 256;
          words[words.length - 3] = jstoi_q(words[words.length - 2]) + jstoi_q(words[words.length - 1]) * 256;
          words = words.slice(0, words.length - 2)
        } else { words = str.split(":") } offset = 0;
        z = 0;
        for (w = 0;
          w < words.length;
          w++) {
          if (typeof words[w] == "string") {
            if (words[w] === "Z") {
              for (z = 0;
                z < 8 - words.length + 1;
                z++) { parts[w + z] = 0 } offset = z - 1
            } else { parts[w + offset] = _htons(parseInt(words[w], 16)) }
          } else { parts[w + offset] = words[w] }
        } return [parts[1] << 16 | parts[0], parts[3] << 16 | parts[2], parts[5] << 16 | parts[4], parts[7] << 16 | parts[6]]
      };
      var DNS = {
        address_map: { id: 1, addrs: {}, names: {} }, lookup_name(name) {
          var res = inetPton4(name);
          if (res !== null) { return name } res = inetPton6(name);
          if (res !== null) { return name } var addr;
          if (DNS.address_map.addrs[name]) { addr = DNS.address_map.addrs[name] } else {
            var id = DNS.address_map.id++;
            assert(id < 65535, "exceeded max address mappings of 65535");
            addr = "172.29." + (id & 255) + "." + (id & 65280);
            DNS.address_map.names[addr] = name;
            DNS.address_map.addrs[name] = addr
          } return addr
        }, lookup_addr(addr) { if (DNS.address_map.names[addr]) { return DNS.address_map.names[addr] } return null }
      };
      var getSocketAddress = (addrp, addrlen) => {
        var info = readSockaddr(addrp, addrlen);
        if (info.errno) throw new FS.ErrnoError(info.errno);
        info.addr = DNS.lookup_addr(info.addr) || info.addr;
        return info
      };
      
var writeSockaddr = (sa, family, addr, port, addrlen) => {
        switch (family) {
          case 2: addr = inetPton4(addr);
            zeroMemory(sa, 16);
            if (addrlen) { HEAP32[addrlen >> 2] = 16 } HEAP16[sa >> 1] = family;
            HEAP32[sa + 4 >> 2] = addr;
            HEAP16[sa + 2 >> 1] = _htons(port);
            break;
          case 10: addr = inetPton6(addr);
            zeroMemory(sa, 28);
            if (addrlen) { HEAP32[addrlen >> 2] = 28 } HEAP32[sa >> 2] = family;
            HEAP32[sa + 8 >> 2] = addr[0];
            HEAP32[sa + 12 >> 2] = addr[1];
            HEAP32[sa + 16 >> 2] = addr[2];
            HEAP32[sa + 20 >> 2] = addr[3];
            HEAP16[sa + 2 >> 1] = _htons(port);
            break;
          default: return 5
        }return 0
      };
      
return { inetNtop4, inetNtop6, readSockaddr, inetPton4, inetPton6, DNS, getSocketAddress, writeSockaddr };
};
