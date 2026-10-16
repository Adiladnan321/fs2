/* Only for the locally installed SurfaceLab training package. */
Java.perform(function () {
    const Activity = Java.use('com.example.surfacelab.VaultActivity');
    const Callback = Java.use('com.example.surfacelab.AuthCallback');
    const Crypto = Java.use('com.example.surfacelab.VaultCrypto');
    const Base64 = Java.use('android.util.Base64');
    const unwrap = Crypto.unwrapKey.overload('android.content.Context');
    unwrap.implementation = function (context) {
        const key = unwrap.call(this, context);
        console.log('[SurfaceLab] Synthetic AES data key (base64): ' + Base64.encodeToString(key, 2));
        return key;
    };
    Activity.beginAuthentication.implementation = function () {
        console.log('[SurfaceLab] Supplying a forged success callback.');
        Callback.$new(this).onAuthenticationSucceeded(null);
    };
    console.log('[SurfaceLab] Open the vault and tap Authenticate to unlock.');
    console.log('[SurfaceLab] The hardened build rejects the missing CryptoObject.');
});
