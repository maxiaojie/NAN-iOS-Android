# TB323FU amss20 Extended WLAN Infrastructure experiment

`patch_amss20_ext_wlan.py` only accepts the exact original SHA-256
`2a22e73834cc6af8e563e91fc5ede40de8887351203df3940891a1c9bbb4b761`.
It verifies all six original instruction words and the existing segment digest before writing a new file. The source file is never overwritten.

```bash
python3 tools/firmware/patch_amss20_ext_wlan.py /absolute/path/amss20.bin /absolute/path/amss20-test.bin
```

The patch retains the size query and original strict capacity check, then calls the existing builder with frame base + used length. It accounts for actual returned bytes, including the builder returning zero on a state change. It uses six existing instruction words, removes this branch's overflow diagnostic, requires no code cave, and leaves Beacon's existing fill path unchanged. It updates the main segment SHA-256 in the existing hash table; it does **not** re-sign the image or disable authentication.

This is one firmware-version experiment. Hexagon packet decoding and six synthetic control-flow tests passed; a0807d7 loaded the modified file and Wi-Fi reconnected. This is not proof that the on-air 23-byte defect is fixed. Do not apply to other images or devices by removing the version guard.

Backups, local emulator tests, binary hashes, actual deployment and rollback commands are recorded in [the deployment report](../../docs/amss20-ext-wlan-patch-deployment.md). No firmware binaries, full disassembly or private logs are committed.
