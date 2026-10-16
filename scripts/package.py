"""Copy built APKs and create a portable source archive without machine-specific state."""
from pathlib import Path
import hashlib
import shutil
import zipfile

ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT/'artifacts'
APKS = {
    'app/build/outputs/apk/vulnerable/debug/app-vulnerable-debug.apk': 'surfacelab-vulnerable.apk',
    'app/build/outputs/apk/hardened/debug/app-hardened-debug.apk': 'surfacelab-hardened.apk',
    'probe/build/outputs/apk/debug/probe-debug.apk': 'surfacelab-probe.apk',
}

def main():
    OUTPUT.mkdir(exist_ok=True)
    for src, dest in APKS.items():
        shutil.copy2(ROOT/src, OUTPUT/dest)
    archive = OUTPUT/'surfacelab-source.zip'
    excluded = {'.gradle', '.git', '.idea', 'build', 'artifacts', '__pycache__'}
    with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
        for path in sorted(ROOT.rglob('*')):
            rel = path.relative_to(ROOT)
            if not path.is_file() or any(p in excluded for p in rel.parts) or rel.name == 'local.properties':
                continue
            z.write(path, Path('SurfaceLab')/rel)
    hashes = []
    for name in [*APKS.values(), archive.name]:
        path = OUTPUT/name
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        hashes.append(f'{digest}  {name}')
        print(f'{name}: {path.stat().st_size:,} bytes')
    (OUTPUT/'SHA256SUMS.txt').write_text('\n'.join(hashes)+'\n', encoding='ascii')

if __name__ == '__main__':
    main()
