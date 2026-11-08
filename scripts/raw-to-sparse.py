#!/usr/bin/env python3
"""Convert a raw super image to a bootloader-friendly Android sparse image.

Motorola's cypfq bootloader reports max-sparse-size=256 MiB and rejects
images whose sparse chunks exceed that. img2simg creates larger chunks;
this converter caps every chunk at 65536 blocks (256 MiB).

Usage: raw-to-sparse.py <raw.img> <sparse.img> [max_blocks]
"""
import os
import struct
import sys

BLK = 4096
RAW = 0xCAC1
DONT = 0xCAC3

src = sys.argv[1]
out = sys.argv[2]
max_blocks = int(sys.argv[3]) if len(sys.argv) > 3 else 65536

size = os.path.getsize(src)
total = size // BLK
chunks = 0

with open(src, "rb") as fi, open(out, "wb") as fo:
    fo.write(b"\0" * 28)

    def emit(ctype, data):
        global chunks
        n = len(data) // BLK
        if not n:
            return
        fo.write(struct.pack("<HHII", ctype, 0, n, 12 + (len(data) if ctype == RAW else 0)))
        if ctype == RAW:
            fo.write(data)
        chunks += 1

    zeros = []
    raw = []
    while True:
        block = fi.read(BLK)
        if not block:
            break
        if len(block) < BLK:
            block += b"\0" * (BLK - len(block))
        if block == b"\0" * BLK:
            if raw:
                emit(RAW, b"".join(raw))
                raw = []
            zeros.append(block)
            if len(zeros) >= max_blocks:
                emit(DONT, b"".join(zeros))
                zeros = []
        else:
            if zeros:
                emit(DONT, b"".join(zeros))
                zeros = []
            raw.append(block)
            if len(raw) >= max_blocks:
                emit(RAW, b"".join(raw))
                raw = []
    if zeros:
        emit(DONT, b"".join(zeros))
    if raw:
        emit(RAW, b"".join(raw))

    fo.seek(0)
    fo.write(struct.pack("<IHHHHIIII", 0xED26FF3A, 1, 0, 28, 12, BLK, total, chunks, 0))

print(f"chunks={chunks} size={os.path.getsize(out)} max_blocks={max_blocks}")
