/* Both stages. Restricted to this project's synthetic SurfaceLab packages. */
Java.perform(function () {
    const app = Java.use('android.app.ActivityThread').currentApplication();
    const pkg = app === null ? '' : String(app.getPackageName());
    if (pkg !== 'com.example.vulnerable' && pkg !== 'com.example.hardened') {
        throw new Error('Attach to SurfaceLab after its dashboard has opened.');
    }
    const Guard = Java.use('com.example.surfacelab.InstrumentationGuard');
    const scan = Guard.isInstrumentationPresent.overload();
    const detected = scan.call(Guard);
    console.log('[SurfaceLab] Detector before hook: ' + detected);
    scan.implementation = function () { return false; };
    const Secret = Java.use('com.example.surfacelab.RuntimeSecret');
    const read = Secret.readProtectedReport.overload('java.lang.String');
    read.implementation = function (challenge) {
        const report = read.call(this, challenge);
        const output = String(report);
        console.log('[SurfaceLab] Protected return value:\n' + output);
        const match = output.match(/FLAG\{runtime_hook_[0-9a-f]{32}\}/);
        send({type: 'runtime-report', package: pkg, report: output, flag: match === null ? null : match[0]});
        if (match === null) console.log('[SurfaceLab] No runtime flag: this is the public comparison payload.');
        else console.log('[SurfaceLab] Paste the recovered flag into the app and tap Verify recovered flag.');
        return report;
    };
    console.log('[SurfaceLab] Both hooks installed. Open runtime challenge, then tap Run protected operation.');
    send({type: 'runtime-ready', stage: 'both-hooks', detectedBeforeHook: detected});
});
