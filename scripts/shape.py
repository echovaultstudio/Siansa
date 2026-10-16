#!/usr/bin/env python3
"""A TCP proxy that shares one bandwidth budget across all connections, like a phone's link to a home server.

Usage: shape.py BYTES_PER_SECOND [LISTEN_PORT=5533] [TARGET_PORT=4533]
Only the server-to-phone direction is limited (that's where songs and covers flow).
"""
import socket
import sys
import threading
import time

RATE = int(sys.argv[1])
LISTEN = int(sys.argv[2]) if len(sys.argv) > 2 else 5533
TARGET = int(sys.argv[3]) if len(sys.argv) > 3 else 4533

lock = threading.Lock()
state = {"tokens": 0.0, "last": time.monotonic()}


def take(n):
    """Waits until up to n bytes may be sent, sharing RATE between all connections."""
    while True:
        with lock:
            now = time.monotonic()
            state["tokens"] = min(RATE * 0.05, state["tokens"] + (now - state["last"]) * RATE)
            state["last"] = now
            if state["tokens"] >= 1024:
                got = min(n, int(state["tokens"]))
                state["tokens"] -= got
                return got
        time.sleep(0.002)


def pipe(src, dst, shaped):
    try:
        while True:
            data = src.recv(16384)
            if not data:
                break
            i = 0
            while i < len(data):
                n = take(len(data) - i) if shaped else len(data) - i
                dst.sendall(data[i:i + n])
                i += n
    except OSError:
        pass
    finally:
        for s in (src, dst):
            try:
                s.shutdown(socket.SHUT_RDWR)
            except OSError:
                pass


def handle(client):
    try:
        server = socket.create_connection(("127.0.0.1", TARGET))
    except OSError:
        client.close()
        return
    threading.Thread(target=pipe, args=(client, server, False), daemon=True).start()
    threading.Thread(target=pipe, args=(server, client, True), daemon=True).start()


listener = socket.socket()
listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
listener.bind(("0.0.0.0", LISTEN))
listener.listen(64)
while True:
    conn, _ = listener.accept()
    threading.Thread(target=handle, args=(conn,), daemon=True).start()
