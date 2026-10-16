"""Verify Lab 6 with a real Frida server on an explicitly selected training emulator."""
import argparse
from datetime import datetime, timezone
import json
from pathlib import Path
import queue
import re
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default='adb')
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', default='artifacts/evidence/runtime.json')
    args = parser.parse_args()
    try:
        import frida
        import frida_tools
    except ImportError as error:
        raise SystemExit('Install matching frida and frida-tools in your Python environment first.') from error

    results = []
    base = [args.adb, '-s', args.serial]

    def adb(*parts, check=True):
        completed = subprocess.run(base + list(parts), capture_output=True, timeout=45)
        output = completed.stdout.decode(errors='replace')
        if check and completed.returncode:
            raise RuntimeError(output + completed.stderr.decode(errors='replace'))
        return output.strip()

    def expect(name, condition, detail):
        if not condition:
            raise AssertionError(f'{name}: {detail}')
        results.append({'check': name, 'result': 'PASS', 'detail': detail})
        print(f'PASS: {name}', flush=True)

    def wait_for(callback, description, timeout=20):
        until = time.monotonic() + timeout
        while time.monotonic() < until:
            value = callback()
            if value:
                return value
            time.sleep(0.25)
        raise AssertionError(f'Timed out: {description}')

    def ui():
        adb('shell', 'uiautomator', 'dump', '/sdcard/surfacelab-runtime.xml')
        return ET.fromstring(adb('shell', 'cat', '/sdcard/surfacelab-runtime.xml'))

    width, height = map(int, re.findall(r'(\d+)x(\d+)', adb('shell', 'wm', 'size'))[-1])

    def tap(label):
        for _ in range(12):
            for node in ui().iter('node'):
                if node.get('text') == label or node.get('content-desc') == label:
                    bounds = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
                    if len(bounds) == 4 and bounds[2] > bounds[0] and bounds[3] > bounds[1]:
                        adb('shell', 'input', 'tap', str((bounds[0]+bounds[2])//2), str((bounds[1]+bounds[3])//2))
                        return
            adb('shell', 'input', 'swipe', str(width//2), str(height*4//5), str(width//2), str(height//4), '150')
        raise AssertionError(f'Button or input not found: {label}')

    def observation(package):
        raw = adb('shell', 'run-as', package, 'cat', 'files/runtime-challenge.json', check=False)
        return json.loads(raw) if raw.startswith('{') else {}

    def completed(package):
        raw = adb('shell', 'run-as', package, 'cat', 'shared_prefs/lab_progress.xml', check=False)
        if not raw.startswith('<?xml'):
            return False
        return any(node.get('name') == 'lab_6' and node.get('value') == 'true' for node in ET.fromstring(raw))

    def launch(package):
        adb('shell', 'am', 'force-stop', package)
        adb('shell', 'am', 'start', '-W', '-n', package + '/com.example.surfacelab.MainActivity')
        return int(adb('shell', 'pidof', package).split()[0])

    # Frida 17 moved Java out of the core runtime; use the bridge shipped by frida-tools.
    bridge = Path(frida_tools.__file__).parent / 'bridges' / 'java.js'
    prelude = bridge.read_text(encoding='utf-8') + '\nglobalThis.Java = bridge;\n' if bridge.exists() else ''
    adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
    adb('shell', 'wm', 'dismiss-keyguard')
    port = adb('forward', 'tcp:0', 'tcp:27042')
    manager = frida.get_device_manager()
    address = '127.0.0.1:' + port
    session = None
    device = None
    try:
        device = manager.add_remote_device(address)

        def attach(pid, source):
            active = device.attach(pid)
            messages = queue.Queue()
            script = active.create_script(prelude + source)
            def on_message(message, data):
                if message['type'] == 'send':
                    messages.put(message['payload'])
                elif message['type'] == 'error':
                    messages.put({'type': 'error', 'detail': message})
            script.on('message', on_message)
            script.load()
            return active, script, messages

        def message_of(messages, kind):
            try:
                while True:
                    item = messages.get(timeout=20)
                    if item.get('type') == 'error':
                        raise AssertionError(item)
                    if item.get('type') == kind:
                        return item
            except queue.Empty as error:
                raise AssertionError(f'No Frida message: {kind}') from error

        for package in ('com.example.vulnerable', 'com.example.hardened'):
            vulnerable = package.endswith('vulnerable')
            pid = launch(package)
            tap('Reset lab observations')
            # Baseline: the observer reads the real detector result without replacing it.
            session, script, messages = attach(pid, """
Java.perform(function () {
    const Guard = Java.use('com.example.surfacelab.InstrumentationGuard');
    send({type: 'detector', detected: Guard.isInstrumentationPresent()});
});
""")
            detected = message_of(messages, 'detector')
            expect(package + ': real Frida agent detected', detected['detected'], detected)
            tap('Open runtime challenge')
            wait_for(lambda: not adb('shell', 'pidof', package, check=False), 'detector terminates app')
            obs = observation(package)
            expect(package + ': baseline closes before report access', obs.get('status') == 'instrumentation-detected', obs)
            session = None  # The baseline process and its attached session were terminated.

            pid = launch(package)
            session, script, messages = attach(pid, (ROOT/'scripts/frida-runtime-bypass.js').read_text(encoding='utf-8'))
            ready = message_of(messages, 'runtime-ready')
            expect(package + ': bypass observes actual detector first', ready['detectedBeforeHook'], ready)
            tap('Open runtime challenge')
            tap('Run protected operation')
            expected = 'operation-completed' if vulnerable else 'public-receipt-only'
            obs = wait_for(lambda: observation(package) if observation(package).get('status') == expected else None, 'stage-one operation')
            words = ' '.join(node.get('text', '') for node in ui().iter('node'))
            expect(package + ': bypass alone leaves secret hidden', 'FLAG{runtime_hook_' not in words, obs)
            if vulnerable:
                expect(package + ': bypass alone does not complete lab', not completed(package), obs)
            session.detach()
            session = None

            pid = launch(package)
            session, script, messages = attach(pid, (ROOT/'scripts/frida-runtime.js').read_text(encoding='utf-8'))
            message_of(messages, 'runtime-ready')
            tap('Open runtime challenge')
            tap('Run protected operation')
            report = message_of(messages, 'runtime-report')
            flag = report.get('flag')
            expect(package + ': protected return value intercepted', bool(report.get('report')), report)
            if vulnerable:
                expect(package + ': real per-challenge flag recovered', bool(re.fullmatch(r'FLAG\{runtime_hook_[0-9a-f]{32}\}', flag or '')), report)
                tap('Recovered runtime flag')
                adb('shell', 'input', 'text', flag)
                adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
                tap('Verify recovered flag')
                obs = wait_for(lambda: observation(package) if observation(package).get('status') == 'flag-recovered' else None, 'flag verification')
                expect(package + ': recovered flag verifies through UI', completed(package), obs)
            else:
                expect(package + ': same hook recovers no secret', flag is None and 'public runtime receipt' in report['report'], report)
            session.detach()
            session = None
        output = Path(args.output)
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps({'serial': args.serial, 'sdk': int(adb('shell', 'getprop', 'ro.build.version.sdk')),
            'frida_version': frida.__version__, 'validated_utc': datetime.now(timezone.utc).isoformat(), 'checks': results}, indent=2), encoding='utf-8')
        print(f'{len(results)} Frida checks passed. Evidence: {output}', flush=True)
    finally:
        if session is not None:
            try:
                session.detach()
            except frida.InvalidOperationError:
                pass
        if device is not None:
            manager.remove_remote_device(address)
        adb('forward', '--remove', 'tcp:' + port, check=False)


if __name__ == '__main__':
    main()
