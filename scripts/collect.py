"""Acquire only SurfaceLab's debug sandbox into a tar with SHA-256 provenance."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from datetime import datetime, timezone

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', default='adb')
    p.add_argument('--serial', required=True)
    p.add_argument('--package', choices=['com.example.vulnerable', 'com.example.hardened', 'com.example.labprobe'], default='com.example.vulnerable')
    p.add_argument('--output', default='artifacts/evidence')
    a = p.parse_args()
    base = [a.adb, '-s', a.serial]
    out = Path(a.output)
    out.mkdir(parents=True, exist_ok=True)
    package = a.package
    # Stop this lab so a copied SQLite DB + WAL set is stable; do not stop other apps.
    subprocess.run(base+['shell', 'am', 'force-stop', package], check=True, capture_output=True)
    archive = out/f'{package}.tar'
    with archive.open('wb') as stream:
        subprocess.run(base+['exec-out', 'run-as', package, 'tar', '-cf', '-', '.'], stdout=stream, check=True)
    meta = {
        'package': package, 'device_serial': a.serial,
        'collected_utc': datetime.now(timezone.utc).isoformat(),
        'method': 'ADB debug run-as; NOT an OS sandbox bypass or adb backup',
        'sha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'bytes': archive.stat().st_size,
    }
    (out/f'{package}.json').write_text(json.dumps(meta, indent=2), encoding='utf-8')
    if package != 'com.example.labprobe':
        dest = out/f'{package}-external'
        pulled = subprocess.run(base+['pull', f'/sdcard/Android/data/{package}/files', str(dest)], capture_output=True, text=True)
        print(pulled.stdout or pulled.stderr)
    print(json.dumps(meta, indent=2))

if __name__ == '__main__':
    main()
