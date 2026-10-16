/* Stage 1 only. Targets the locally installed SurfaceLab training app. */
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
    console.log('[SurfaceLab] Stage 1 installed. Open runtime challenge and run the protected operation.');
    console.log('[SurfaceLab] The report remains hidden until you install a return-value hook.');
    send({type: 'runtime-ready', stage: 'bypass-only', detectedBeforeHook: detected});
});
