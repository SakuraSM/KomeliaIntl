import struct
import sys
import zipfile

apk, abi = sys.argv[1:]
with open(apk, "rb") as raw, zipfile.ZipFile(apk) as archive:
    for entry in archive.infolist():
        if not entry.filename.startswith(f"lib/{abi}/") or not entry.filename.endswith(".so"):
            continue
        raw.seek(entry.header_offset)
        header = struct.unpack("<IHHHHHIIIHH", raw.read(30))
        if header[0] != 0x04034B50:
            raise ValueError(f"invalid local ZIP header: {entry.filename}")
        start = entry.header_offset + 30 + header[-2] + header[-1]
        print(f"{start:#x}-{start + entry.compress_size:#x} compression={entry.compress_type} {entry.filename}")
