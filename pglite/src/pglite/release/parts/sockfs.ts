export const createSOCKFS = ({
  FS,
  Module,
  ENVIRONMENT_IS_NODE,
  require,
  assert,
  HEAP32,
  TextEncoder
}: {
  FS: any;
  Module: Record<string, any>;
  ENVIRONMENT_IS_NODE: boolean;
  require?: (id: string) => any;
  assert: (check: any, message?: string) => void;
  HEAP32: Int32Array;
  TextEncoder: { new (): { encode(input?: string): Uint8Array } };
}) => {
  var SOCKFS: any = {
    websocketArgs: {}, callbacks: {}, on(event, callback) { SOCKFS.callbacks[event] = callback }, emit(event, param) { SOCKFS.callbacks[event]?.(param) }, mount(mount) {
      SOCKFS.websocketArgs = Module["websocket"] || {};
      (Module["websocket"] ??= {})["on"] = SOCKFS.on;
      return FS.createNode(null, "/", 16895, 0)
    }, createSocket(family, type, protocol) {
      type &= ~526336;
      var streaming = type == 1;
      if (streaming && protocol && protocol != 6) { throw new FS.ErrnoError(66) } var sock: any = { family, type, protocol, server: null, error: null, peers: {}, pending: [], recv_queue: [], sock_ops: SOCKFS.websocket_sock_ops };
      var name = SOCKFS.nextname();
      var node = FS.createNode(SOCKFS.root, name, 49152, 0);
      node.sock = sock;
      var stream = FS.createStream({ path: name, node, flags: 2, seekable: false, stream_ops: SOCKFS.stream_ops });
      sock.stream = stream;
      return sock
    }, getSocket(fd) {
      var stream = FS.getStream(fd);
      if (!stream || !FS.isSocket(stream.node.mode)) { return null } return stream.node.sock
    }, stream_ops: {
      poll(stream) {
        var sock = stream.node.sock;
        return sock.sock_ops.poll(sock)
      }, ioctl(stream, request, varargs) {
        var sock = stream.node.sock;
        return sock.sock_ops.ioctl(sock, request, varargs)
      }, read(stream, buffer, offset, length, position) {
        var sock = stream.node.sock;
        var msg = sock.sock_ops.recvmsg(sock, length);
        if (!msg) { return 0 } buffer.set(msg.buffer, offset);
        return msg.buffer.length
      }, write(stream, buffer, offset, length, position) {
        var sock = stream.node.sock;
        return sock.sock_ops.sendmsg(sock, buffer, offset, length)
      }, close(stream) {
        var sock = stream.node.sock;
        sock.sock_ops.close(sock)
      }
    }, nextname() { if (!SOCKFS.nextname.current) { SOCKFS.nextname.current = 0 } return `socket[${SOCKFS.nextname.current++}]` }, websocket_sock_ops: {
      createPeer(sock, addr, port?) {
        var ws;
        if (typeof addr == "object") {
          ws = addr;
          addr = null;
          port = null
        }
        if (ws) {
          if (ws._socket) {
            addr = ws._socket.remoteAddress;
            port = ws._socket.remotePort
          } else {
            var result = /ws[s]?:\/\/([^:]+):(\d+)/.exec(ws.url);
            if (!result) { throw new Error("WebSocket URL must be in the format ws(s)://address:port") } addr = result[1];
            port = parseInt(result[2], 10)
          }
        } else {
          try {
            var url = "ws:#".replace("#", "//");
            var subProtocols: any = "binary";
            var opts = undefined;
            if (SOCKFS.websocketArgs["url"]) { url = SOCKFS.websocketArgs["url"] }
            if (SOCKFS.websocketArgs["subprotocol"]) { subProtocols = SOCKFS.websocketArgs["subprotocol"] } else if (SOCKFS.websocketArgs["subprotocol"] === null) { subProtocols = "null" }
            if (url === "ws://" || url === "wss://") {
              var parts = addr.split("/");
              url = url + parts[0] + ":" + port + "/" + parts.slice(1).join("/")
            }
            if (subProtocols !== "null") {
              subProtocols = subProtocols.replace(/^ +| +$/g, "").split(/ *, */);
              opts = subProtocols
            } var WebSocketConstructor;
            if (ENVIRONMENT_IS_NODE) { WebSocketConstructor = require("ws") } else { WebSocketConstructor = WebSocket } ws = new WebSocketConstructor(url, opts);
            ws.binaryType = "arraybuffer"
          } catch (e) { throw new FS.ErrnoError(23) }
        } var peer = { addr, port, socket: ws, msg_send_queue: [] };
        SOCKFS.websocket_sock_ops.addPeer(sock, peer);
        SOCKFS.websocket_sock_ops.handlePeerEvents(sock, peer);
        if (sock.type === 2 && typeof sock.sport != "undefined") { peer.msg_send_queue.push(new Uint8Array([255, 255, 255, 255, "p".charCodeAt(0), "o".charCodeAt(0), "r".charCodeAt(0), "t".charCodeAt(0), (sock.sport & 65280) >> 8, sock.sport & 255])) } return peer
      }, getPeer(sock, addr, port) { return sock.peers[addr + ":" + port] }, addPeer(sock, peer) { sock.peers[peer.addr + ":" + peer.port] = peer }, removePeer(sock, peer) { delete sock.peers[peer.addr + ":" + peer.port] }, handlePeerEvents(sock, peer) {
        var first = true;
        var handleOpen = function () {
          sock.connecting = false;
          SOCKFS.emit("open", sock.stream.fd);
          try {
            var queued = peer.msg_send_queue.shift();
            while (queued) {
              peer.socket.send(queued);
              queued = peer.msg_send_queue.shift()
            }
          } catch (e) { peer.socket.close() }
        };
        function handleMessage(data: any) {
          if (typeof data == "string") {
            var encoder = new TextEncoder;
            data = encoder.encode(data)
          } else {
            assert(data.byteLength !== undefined);
            if (data.byteLength == 0) { return } data = new Uint8Array(data)
          } var wasfirst = first;
          first = false;
          if (wasfirst && data.length === 10 && data[0] === 255 && data[1] === 255 && data[2] === 255 && data[3] === 255 && data[4] === "p".charCodeAt(0) && data[5] === "o".charCodeAt(0) && data[6] === "r".charCodeAt(0) && data[7] === "t".charCodeAt(0)) {
            var newport = data[8] << 8 | data[9];
            SOCKFS.websocket_sock_ops.removePeer(sock, peer);
            peer.port = newport;
            SOCKFS.websocket_sock_ops.addPeer(sock, peer);
            return
          } sock.recv_queue.push({ addr: peer.addr, port: peer.port, data });
          SOCKFS.emit("message", sock.stream.fd)
        }
        if (ENVIRONMENT_IS_NODE) {
          peer.socket.on("open", handleOpen);
          peer.socket.on("message", function (data, isBinary) { if (!isBinary) { return } handleMessage(new Uint8Array(data).buffer) });
          peer.socket.on("close", function () { SOCKFS.emit("close", sock.stream.fd) });
          peer.socket.on("error", function (error) {
            sock.error = 14;
            SOCKFS.emit("error", [sock.stream.fd, sock.error, "ECONNREFUSED: Connection refused"])
          })
        } else {
          peer.socket.onopen = handleOpen;
          peer.socket.onclose = function () { SOCKFS.emit("close", sock.stream.fd) };
          peer.socket.onmessage = function peer_socket_onmessage(event: any) { handleMessage(event.data) };
          peer.socket.onerror = function (error) {
            sock.error = 14;
            SOCKFS.emit("error", [sock.stream.fd, sock.error, "ECONNREFUSED: Connection refused"])
          }
        }
      }, poll(sock) {
        if (sock.type === 1 && sock.server) { return sock.pending.length ? 64 | 1 : 0 } var mask = 0;
        var dest = sock.type === 1 ? SOCKFS.websocket_sock_ops.getPeer(sock, sock.daddr, sock.dport) : null;
        if (sock.recv_queue.length || !dest || dest && dest.socket.readyState === dest.socket.CLOSING || dest && dest.socket.readyState === dest.socket.CLOSED) { mask |= 64 | 1 }
        if (!dest || dest && dest.socket.readyState === dest.socket.OPEN) { mask |= 4 }
        if (dest && dest.socket.readyState === dest.socket.CLOSING || dest && dest.socket.readyState === dest.socket.CLOSED) { if (sock.connecting) { mask |= 4 } else { mask |= 16 } } return mask
      }, ioctl(sock, request, arg) {
        switch (request) {
          case 21531: var bytes = 0;
            if (sock.recv_queue.length) { bytes = sock.recv_queue[0].data.length } HEAP32[arg >> 2] = bytes;
            return 0;
          default: return 28
        }
      }, close(sock) {
        if (sock.server) { try { sock.server.close() } catch (e) { } sock.server = null } var peers = Object.keys(sock.peers);
        for (var i = 0;
          i < peers.length;
          i++) {
          var peer = sock.peers[peers[i]];
          try { peer.socket.close() } catch (e) { } SOCKFS.websocket_sock_ops.removePeer(sock, peer)
        } return 0
      }, bind(sock, addr, port) {
        if (typeof sock.saddr != "undefined" || typeof sock.sport != "undefined") { throw new FS.ErrnoError(28) } sock.saddr = addr;
        sock.sport = port;
        if (sock.type === 2) {
          if (sock.server) {
            sock.server.close();
            sock.server = null
          } try { sock.sock_ops.listen(sock, 0) } catch (e) {
            if (!(e.name === "ErrnoError")) throw e;
            if (e.errno !== 138) throw e
          }
        }
      }, connect(sock, addr, port) {
        if (sock.server) { throw new FS.ErrnoError(138) }
        if (typeof sock.daddr != "undefined" && typeof sock.dport != "undefined") {
          var dest = SOCKFS.websocket_sock_ops.getPeer(sock, sock.daddr, sock.dport);
          if (dest) { if (dest.socket.readyState === dest.socket.CONNECTING) { throw new FS.ErrnoError(7) } else { throw new FS.ErrnoError(30) } }
        } var peer = SOCKFS.websocket_sock_ops.createPeer(sock, addr, port);
        sock.daddr = peer.addr;
        sock.dport = peer.port;
        sock.connecting = true
      }, listen(sock, backlog) {
        if (!ENVIRONMENT_IS_NODE) { throw new FS.ErrnoError(138) }
        if (sock.server) { throw new FS.ErrnoError(28) } var WebSocketServer = require("ws").Server;
        var host = sock.saddr;
        sock.server = new WebSocketServer({ host, port: sock.sport });
        SOCKFS.emit("listen", sock.stream.fd);
        sock.server.on("connection", function (ws) {
          if (sock.type === 1) {
            var newsock = SOCKFS.createSocket(sock.family, sock.type, sock.protocol);
            var peer = SOCKFS.websocket_sock_ops.createPeer(newsock, ws);
            newsock.daddr = peer.addr;
            newsock.dport = peer.port;
            sock.pending.push(newsock);
            SOCKFS.emit("connection", newsock.stream.fd)
          } else {
            SOCKFS.websocket_sock_ops.createPeer(sock, ws);
            SOCKFS.emit("connection", sock.stream.fd)
          }
        });
        sock.server.on("close", function () {
          SOCKFS.emit("close", sock.stream.fd);
          sock.server = null
        });
        sock.server.on("error", function (error) {
          sock.error = 23;
          SOCKFS.emit("error", [sock.stream.fd, sock.error, "EHOSTUNREACH: Host is unreachable"])
        })
      }, accept(listensock) {
        if (!listensock.server || !listensock.pending.length) { throw new FS.ErrnoError(28) } var newsock = listensock.pending.shift();
        newsock.stream.flags = listensock.stream.flags;
        return newsock
      }, getname(sock, peer) {
        var addr, port;
        if (peer) {
          if (sock.daddr === undefined || sock.dport === undefined) { throw new FS.ErrnoError(53) } addr = sock.daddr;
          port = sock.dport
        } else {
          addr = sock.saddr || 0;
          port = sock.sport || 0
        } return { addr, port }
      }, sendmsg(sock, buffer, offset, length, addr, port) {
        if (sock.type === 2) {
          if (addr === undefined || port === undefined) {
            addr = sock.daddr;
            port = sock.dport
          }
          if (addr === undefined || port === undefined) { throw new FS.ErrnoError(17) }
        } else {
          addr = sock.daddr;
          port = sock.dport
        } var dest = SOCKFS.websocket_sock_ops.getPeer(sock, addr, port);
        if (sock.type === 1) { if (!dest || dest.socket.readyState === dest.socket.CLOSING || dest.socket.readyState === dest.socket.CLOSED) { throw new FS.ErrnoError(53) } }
        if (ArrayBuffer.isView(buffer)) {
          offset += buffer.byteOffset;
          buffer = buffer.buffer
        } var data = buffer.slice(offset, offset + length);
        if (!dest || dest.socket.readyState !== dest.socket.OPEN) {
          if (sock.type === 2) { if (!dest || dest.socket.readyState === dest.socket.CLOSING || dest.socket.readyState === dest.socket.CLOSED) { dest = SOCKFS.websocket_sock_ops.createPeer(sock, addr, port) } } dest.msg_send_queue.push(data);
          return length
        } try {
          dest.socket.send(data);
          return length
        } catch (e) { throw new FS.ErrnoError(28) }
      }, recvmsg(sock, length) {
        if (sock.type === 1 && sock.server) { throw new FS.ErrnoError(53) } var queued = sock.recv_queue.shift();
        if (!queued) {
          if (sock.type === 1) {
            var dest = SOCKFS.websocket_sock_ops.getPeer(sock, sock.daddr, sock.dport);
            if (!dest) { throw new FS.ErrnoError(53) }
            if (dest.socket.readyState === dest.socket.CLOSING || dest.socket.readyState === dest.socket.CLOSED) { return null } throw new FS.ErrnoError(6)
          } throw new FS.ErrnoError(6)
        } var queuedLength = queued.data.byteLength || queued.data.length;
        var queuedOffset = queued.data.byteOffset || 0;
        var queuedBuffer = queued.data.buffer || queued.data;
        var bytesRead = Math.min(length, queuedLength);
        var res = { buffer: new Uint8Array(queuedBuffer, queuedOffset, bytesRead), addr: queued.addr, port: queued.port };
        if (sock.type === 1 && bytesRead < queuedLength) {
          var bytesRemaining = queuedLength - bytesRead;
          queued.data = new Uint8Array(queuedBuffer, queuedOffset + bytesRead, bytesRemaining);
          sock.recv_queue.unshift(queued)
        } return res
      }
    }
  };
  return SOCKFS;
};
