#!/usr/bin/env python3
"""
Photo Editor Demo - Host-side screenshot receiver
==================================================
Listens on port 8888 for the emulator app, receives each screenshot
(protocol: 8-byte big-endian file size, then raw file bytes), saves it
to ./received_screenshots/, and prints a line per file so the audience
sees them arriving in real time.

Usage:
    python screenshot_server.py              # default port 8888
    python screenshot_server.py 9000         # custom port
"""

import os
import socket
import struct
import sys
import threading
from datetime import datetime

DEFAULT_PORT = 8888
BUFFER_SIZE = 4096
SAVE_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "received_screenshots")

UDP_DISCOVERY_PORT = 8889
DISCOVERY_MAGIC = b"PHOTO_EDITOR_DISCOVERY_PROBE"
SERVER_RESPONSE_MAGIC = b"PHOTO_EDITOR_SERVER"
DISCOVERY_BUFFER_SIZE = 1024


def discovery_listener(udp_port):
    """Answer UDP probes from the emulator app so it learns this machine's IP."""
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", udp_port))
    print(f"[+] UDP discovery listening on 0.0.0.0:{udp_port}")
    while True:
        try:
            data, addr = sock.recvfrom(DISCOVERY_BUFFER_SIZE)
            if data == DISCOVERY_MAGIC:
                sock.sendto(SERVER_RESPONSE_MAGIC, addr)
                print(f"[+] Discovery probe from {addr[0]}:{addr[1]} -> responded")
        except OSError:
            break
        except Exception as e:
            print(f"[!] Discovery error: {e}")


def receive_exact(conn, size):
    """Read exactly `size` bytes from the socket, returning bytes or None."""
    chunks = bytearray()
    remaining = size
    while remaining > 0:
        chunk = conn.recv(min(BUFFER_SIZE, remaining))
        if not chunk:
            return None
        chunks.extend(chunk)
        remaining -= len(chunk)
    return bytes(chunks)


def handle_client(conn, addr, save_dir):
    print(f"[+] Connection from {addr[0]}:{addr[1]}")
    try:
        header = receive_exact(conn, 8)
        if header is None:
            print(f"[-] {addr[0]} disconnected before sending the size header")
            return

        file_size = struct.unpack(">q", header)[0]
        if file_size <= 0:
            print(f"[!] {addr[0]} sent invalid file size: {file_size}")
            return

        data = receive_exact(conn, file_size)
        if data is None or len(data) != file_size:
            received = len(data) if data else 0
            print(f"[-] {addr[0]} disconnected mid-transfer ({received}/{file_size} bytes)")
            return

        stamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
        filename = f"{stamp}.jpg"
        path = os.path.join(save_dir, filename)
        with open(path, "wb") as f:
            f.write(data)

        print(f"[OK] {filename}  ({file_size:,} bytes) from {addr[0]}  -> {path}")
    except Exception as e:
        print(f"[!] Error handling {addr[0]}: {e}")
    finally:
        conn.close()


def main():
    port = DEFAULT_PORT
    if len(sys.argv) > 1:
        port = int(sys.argv[1])

    os.makedirs(SAVE_DIR, exist_ok=True)

    threading.Thread(target=discovery_listener, args=(UDP_DISCOVERY_PORT,), daemon=True).start()

    server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("0.0.0.0", port))
    server.listen(5)

    print("=" * 60)
    print("  Photo Editor Demo - Screenshot Receiver")
    print(f"  Listening on 0.0.0.0:{port}")
    print(f"  Saving to: {SAVE_DIR}")
    print("  Waiting for the emulator app...")
    print("=" * 60)
    try:
        host_name = socket.gethostname()
        for info in socket.getaddrinfo(host_name, None, socket.AF_INET):
            print(f"  Host IP: {info[4][0]}")
    except Exception:
        pass

    while True:
        conn, addr = server.accept()
        handle_client(conn, addr, SAVE_DIR)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        print("\n[!] Server stopped by user")
        sys.exit(0)
