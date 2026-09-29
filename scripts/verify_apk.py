"""Host-side APK inventory and 16 KiB ELF checks; not an Android runtime test."""
import json
from pathlib import Path
import struct
import sys
import zipfile

apk = Path(sys.argv[1])
report = {"apk": apk.name, "bytes": apk.stat().st_size, "libraries": []}
with zipfile.ZipFile(apk) as archive:
    names = archive.namelist()
    assert not any(name.endswith((".jks", ".keystore", "keystore.properties", "ggml-tiny.bin")) for name in names)
    assert "assets/licenses/whisper-MIT.txt" in names
    assert "assets/licenses/NDK-NOTICE.txt" in names
    for abi in ("arm64-v8a", "x86_64"):
        assert f"lib/{abi}/libclavier_whisper.so" in names
    for name in names:
        if not name.endswith(".so"):
            continue
        data = archive.read(name)
        assert data[:6] == b"\x7fELF\x02\x01", f"Expected 64-bit little-endian ELF: {name}"
        phoff = struct.unpack_from("<Q", data, 32)[0]
        phsize, phcount = struct.unpack_from("<HH", data, 54)
        aligns = []
        for index in range(phcount):
            header = phoff + phsize * index
            kind = struct.unpack_from("<I", data, header)[0]
            if kind == 1:
                offset, vaddr = struct.unpack_from("<QQ", data, header + 8)
                align = struct.unpack_from("<Q", data, header + 48)[0]
                assert align >= 16384 and (vaddr - offset) % 16384 == 0, f"Not 16 KiB aligned: {name}"
                aligns.append(align)
        assert aligns, name
        report["libraries"].append({"name": name, "load_segment_alignment": aligns})
print(json.dumps(report, indent=2))
