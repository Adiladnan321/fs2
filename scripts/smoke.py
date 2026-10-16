"""Exercise both installed SurfaceLab flavors and real cross-UID probe interactions."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PROBE = 'com.example.labprobe'
ACTIVITY = 'com.example.surfacelab.'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True, help='Explicit emulator/device serial')
    parser.add_argument('--output', default='artifacts/evidence/smoke.json')
    args = parser.parse_args()
    results = []

    def adb(*parts, check=True):
        proc = subprocess.run([args.adb, '-s', args.serial, *parts], capture_output=True, timeout=40)
        if check and proc.returncode:
            raise RuntimeError(proc.stderr.decode(errors='replace') + proc.stdout.decode(errors='replace'))
        return proc.stdout.decode(errors='replace')

    width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])
    swipe_x = str(width // 2)

    def wait_for(fn, description, timeout=18):
        until = time.monotonic() + timeout
        last = None
        while time.monotonic() < until:
            try:
                last = fn()
                if last:
                    return last
            except (json.JSONDecodeError, ET.ParseError):
                pass
            time.sleep(0.4)
        raise AssertionError(f'Timed out: {description}; last={last!r}')

    def ui():
        adb('shell', 'uiautomator', 'dump', '/sdcard/surfacelab-window.xml')
        return ET.fromstring(adb('shell', 'cat', '/sdcard/surfacelab-window.xml'))

    def tap(text):
        # Always search from the top: the probe scrolls to its result after each request.
        for _ in range(5):
            adb('shell', 'input', 'swipe', swipe_x, str(height//5), swipe_x, str(height*4//5), '100')
        for _ in range(12):
            tree = ui()
            for node in tree.iter('node'):
                if node.get('text') == text:
                    box = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                    if len(box) == 4 and box[3] > box[1] and node.get('enabled') == 'true':
                        adb('shell', 'input', 'tap', str((box[0]+box[2])//2), str((box[1]+box[3])//2))
                        return
            adb('shell', 'input', 'swipe', swipe_x, str(height*4//5), swipe_x, str(height//4), '180')
        raise AssertionError(f'Button not found: {text}')

    def observation():
        raw = adb('shell', 'run-as', PROBE, 'cat', 'files/last-result.json', check=False)
        return json.loads(raw) if raw.strip().startswith('{') else None

    def clear_result():
        adb('shell', 'run-as', PROBE, 'rm', '-f', 'files/last-result.json')

    def launch_probe():
        adb('shell', 'am', 'force-stop', PROBE)
        adb('shell', 'am', 'start', '-W', '-n', f'{PROBE}/.MainActivity')

    def expect(name, condition, detail):
        if not condition:
            raise AssertionError(f'{name}: {detail}')
        results.append({'check': name, 'result': 'PASS', 'detail': detail})
        print(f'PASS: {name}', flush=True)

    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    sdk = int(adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    for target in ('com.example.vulnerable', 'com.example.hardened'):
        vulnerable = target.endswith('vulnerable')
        adb('shell', 'am', 'start', '-W', '-n', f'{target}/{ACTIVITY}MainActivity')
        instrumentation = adb('shell', 'am', 'instrument', '-w', f'{target}.test/{ACTIVITY}LabInstrumentation')
        expect(f'{target}: on-device fixture checks', 'PASS (' in instrumentation, instrumentation.strip())
        # Use the exported link rather than a debug-only helper to exercise authentication.
        scheme = 'myapp' if vulnerable else 'safelab'
        adb('shell', 'am', 'start', '-W', '-a', 'android.intent.action.VIEW', '-d', f"'{scheme}://transfer?to=Smoke&amount=42'", '-p', target)
        tree = ui()
        words = ' '.join(n.get('text', '') for n in tree.iter('node'))
        expect(f'{target}: deep-link gate', ('FLAG{deep_link_without_auth}' in words) if vulnerable else ('BLOCKED' in words), words[:600])
        launch_probe()
        if not vulnerable:
            tap('Switch target')
        clear_result()
        tap('Request private session')
        obs = wait_for(observation, 'file broker result')
        expect(f'{target}: private file grant', obs['status'] == ('FILE READ' if vulnerable else 'BLOCKED'), obs)
        if vulnerable:
            expect(f'{target}: real cross-UID file contents', 'LAB_SESSION_f3a9_synthetic_only' in obs['detail'], obs['detail'])
        clear_result()
        tap('Request public handout')
        obs = wait_for(observation, 'public file broker result')
        expect(f'{target}: public sharing still works', obs['status'] == 'FILE READ' and 'public handout' in obs['detail'], obs)
        clear_result()
        tap('Try direct admin launch')
        obs = wait_for(observation, 'direct admin denial')
        expect(f'{target}: OS blocks direct admin launch', obs['status'] == 'BLOCKED' and 'SecurityException' in obs['detail'], obs)
        clear_result()
        tap('Request ticket')
        obs = wait_for(observation, 'pending ticket result')
        expect(f'{target}: delegated ticket acquired', obs['status'] == 'TICKET RECEIVED', obs)
        tap('Send modified ticket')

        def destination():
            dump = adb('shell', 'dumpsys', 'activity', 'activities')
            expected = 'AdminActivity' if vulnerable else 'ReceiptActivity'
            return next((line.strip() for line in dump.splitlines() if ('mResumedActivity' in line or 'topResumedActivity' in line) and target in line and expected in line), None)

        reached = wait_for(destination, 'pending ticket destination')
        expect(f'{target}: pending ticket destination', True, reached)
        tree = ui()
        words = ' '.join(n.get('text', '') for n in tree.iter('node'))
        expect(f'{target}: audit export behavior', ('FLAG{mutable_ticket_admin_execution}' in words) if vulnerable else ('No audit was exported' in words), words[:700])
    path = Path(args.output)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps({'sdk': sdk, 'serial': args.serial, 'checks': results}, indent=2), encoding='utf-8')
    print(f'{len(results)} checks passed on API {sdk}. Evidence: {path}', flush=True)

if __name__ == '__main__':
    main()
