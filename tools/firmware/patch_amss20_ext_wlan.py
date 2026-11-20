#!/usr/bin/env python3
"""Experimental patch for ONE known amss20 image. Updates segment hash; DOES NOT re-sign."""
import argparse
import hashlib
import json
from pathlib import Path
import struct

EXPECTED = '2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761'
# Six words replacing success branch and old overflow diagnostic. No code cave.
WORDS = [
    (0x0184fd3c, 0x2202f10e, 0x2202f108),  # capacity > new length -> fill at fd48
    (0x0184fd40, 0x784e4a41, 0x70754013),  # fallback: R19 = R21 (old length)
    (0x0184fd44, 0x0f204cd1, 0x5800c00a),  # jump fd54; preserve no-append behavior
    (0x0184fd48, 0x69002800, 0x5a3f7cc8),  # call 01a4f6d8, after capacity check
    (0x0184fd4c, 0x5ae26918, 0xf312c200),  # same packet: R0 = frame base R18 + used R2
    (0x0184fd50, 0x70db2812, 0xf300d513),  # actual length: R19 = written R0 + used R21
]
SEGMENT_OFFSET = 0x13a000
SEGMENT_VA = 0x017f1000
SEGMENT_SIZE = 0x2f6000
HASH_OFFSET = 0x80660


def build(original):
    if hashlib.sha256(original).hexdigest() != EXPECTED:
        raise ValueError('Firmware version/hash mismatch; refusing to patch')
    output = bytearray(original)
    before_digest = hashlib.sha256(original[SEGMENT_OFFSET:SEGMENT_OFFSET + SEGMENT_SIZE]).digest()
    if original[HASH_OFFSET:HASH_OFFSET + 32] != before_digest:
        raise ValueError('Unexpected segment hash layout')
    for va, old, new in WORDS:
        offset = SEGMENT_OFFSET + va - SEGMENT_VA
        if struct.unpack_from('<I', original, offset)[0] != old:
            raise ValueError('Instruction mismatch at ' + hex(va))
        struct.pack_into('<I', output, offset, new)
    after_digest = hashlib.sha256(output[SEGMENT_OFFSET:SEGMENT_OFFSET + SEGMENT_SIZE]).digest()
    output[HASH_OFFSET:HASH_OFFSET + 32] = after_digest
    return bytes(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('original', type=Path)
    parser.add_argument('output', type=Path)
    args = parser.parse_args()
    original = args.original.read_bytes()
    try:
        patched = build(original)
    except ValueError as error:
        parser.error(str(error))
    if args.output.exists() or args.output.with_suffix('.patch.json').exists():
        parser.error('Output already exists; refusing overwrite')
    with args.output.open('xb') as file:
        file.write(patched)
    metadata = dict(original_sha256=EXPECTED, patched_sha256=hashlib.sha256(patched).hexdigest(),
                    size=len(patched), signature='NOT RE-SIGNED; device authentication may reject',
                    code_words=[dict(va=hex(v), old=hex(o), new=hex(n)) for v, o, n in WORDS],
                    updated_segment_hash_offset=hex(HASH_OFFSET),
                    limitation='Replaces this branch overflow log; preserves no-append fallback. Experimental.')
    with args.output.with_suffix('.patch.json').open('x') as file:
        json.dump(metadata, file, indent=2)
        file.write('\n')
    print(json.dumps(metadata, indent=2))


if __name__ == '__main__':
    main()
