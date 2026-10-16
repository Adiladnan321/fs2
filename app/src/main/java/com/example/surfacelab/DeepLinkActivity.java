package com.example.surfacelab;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import com.example.labui.LabUi;

public final class DeepLinkActivity extends Activity {
    private WebView web;
    @Override @SuppressLint({"SetJavaScriptEnabled","JavascriptInterface"}) protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root=LabUi.page(this,"LAB 01 / DEEP LINKS","Trust crosses a URL.",BuildConfig.VULNERABLE?"Untrusted input reaches privileged app functions.":"External routes cannot authorize privileged operations.");
        Uri uri=getIntent().getData();
        if (uri==null) { LabUi.code(this,root,"Missing lab URI."); return; }
        if ("transfer".equals(uri.getHost())) {
            if (!BuildConfig.VULNERABLE) { LabUi.code(this,root,"BLOCKED: external links cannot authorize a transfer."); return; }
            try {
                String recipient=uri.getQueryParameter("to"); if(recipient==null) recipient="Training recipient";
                String amount=uri.getQueryParameter("amount"); int credits=amount==null?42:Integer.parseInt(amount);
                LabUi.code(this,root,LabStore.transfer(this,recipient,credits));
            } catch(Exception e) { LabUi.code(this,root,"Invalid demo transfer: "+e.getMessage()); }
            return;
        }
        String target=uri.getQueryParameter("url");
        if (!BuildConfig.VULNERABLE && target!=null && !"https://appassets.androidplatform.net/assets/demo.html".equals(target)) {
            LabUi.code(this,root,"BLOCKED: URL is outside the exact local asset allowlist."); return;
        }
        web=new WebView(this); web.setBackgroundColor(LabUi.PANEL);
        web.getSettings().setJavaScriptEnabled(BuildConfig.VULNERABLE);
        web.getSettings().setAllowFileAccess(BuildConfig.VULNERABLE);
        web.getSettings().setAllowContentAccess(BuildConfig.VULNERABLE);
        // INTENTIONALLY VULNERABLE: any loaded origin receives this bridge.
        if(BuildConfig.VULNERABLE) web.addJavascriptInterface(new SessionBridge(),"LabSession");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,android.webkit.WebResourceRequest request) {
                return !BuildConfig.VULNERABLE;
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,LabUi.dp(this,420)));
        if(target==null || !BuildConfig.VULNERABLE) {
            String html="<html><meta name='viewport' content='width=device-width'><body style='background:#152133;color:#eef4ff;font:16px sans-serif;padding:22px'><p style='color:#55e6bc'>LOCAL WEBVIEW DEMO</p><h2>A page can ask for a session.</h2><p id='result'>"+
                (BuildConfig.VULNERABLE?"Waiting for the JavaScript bridge…":"BLOCKED: no session bridge exists in this build.")+"</p>"+
                (BuildConfig.VULNERABLE?"<script>document.getElementById('result').textContent=LabSession.readSession();</script>":"")+"</body></html>";
            web.loadDataWithBaseURL("https://appassets.androidplatform.net/assets/demo.html",html,"text/html","UTF-8",null);
        } else web.loadUrl(target);
    }
    public final class SessionBridge {
        @JavascriptInterface public String readSession() {
            LabStore.record(DeepLinkActivity.this,1,"FLAG{untrusted_webview_session_bridge}"); return LabStore.TOKEN;
        }
    }
    @Override protected void onDestroy() { if(web!=null) {web.removeJavascriptInterface("LabSession");web.destroy();} super.onDestroy(); }
}
