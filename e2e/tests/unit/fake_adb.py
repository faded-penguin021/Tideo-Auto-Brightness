"""A minimal adb host server on loopback that records every request it receives.

Enough protocol for adbutils to open transports, run v1 shells and sync STAT; the tests use it
to prove a refused request never reaches the server.
"""

from __future__ import annotations

import socket
import struct
import threading


class FakeAdbServer:
    def __init__(self):
        self.received: list[str] = []
        self._sock = socket.socket()
        self._sock.bind(("127.0.0.1", 0))
        self._sock.listen()
        self.port = self._sock.getsockname()[1]
        threading.Thread(target=self._serve, daemon=True).start()

    def close(self):
        self._sock.close()

    def _serve(self):
        while True:
            try:
                conn, _ = self._sock.accept()
            except OSError:
                return
            threading.Thread(target=self._handle, args=(conn,), daemon=True).start()

    @staticmethod
    def _read(conn, n):
        buf = b""
        while len(buf) < n:
            chunk = conn.recv(n - len(buf))
            if not chunk:
                raise EOFError
            buf += chunk
        return buf

    def _handle(self, conn):
        with conn:
            try:
                while True:
                    req = self._read(conn, int(self._read(conn, 4), 16)).decode()
                    self.received.append(req)
                    if req == "host:version":
                        conn.sendall(b"OKAY" + b"0004" + b"0029")  # server version 41
                        return
                    if req.startswith("host:tport:serial:"):
                        conn.sendall(b"OKAY" + struct.pack("<Q", 1))
                        continue
                    if req.startswith("host:transport:"):
                        conn.sendall(b"OKAY")
                        continue
                    if req == "sync:":
                        conn.sendall(b"OKAY")
                        cmd = self._read(conn, 4).decode()
                        path = self._read(conn, struct.unpack("<I", self._read(conn, 4))[0])
                        self.received.append(f"sync {cmd} {path.decode()}")
                        if cmd == "STAT":
                            conn.sendall(b"STAT" + struct.pack("<III", 0, 0, 0))
                        return
                    conn.sendall(b"OKAY")
                    if req.startswith("shell:"):
                        conn.sendall(b"ok\nX4EXIT:0" if "X4EXIT" in req else b"ok\n")
                    return
            except (EOFError, OSError):
                return
