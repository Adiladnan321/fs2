package com.example.surfacelab;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.content.FileProvider;
import java.io.File;

/** INTENTIONALLY VULNERABLE grant broker in the vulnerable flavor. */
public final class ShareActivity extends Activity {
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            if(getCallingActivity()==null) throw new SecurityException("A result recipient is required");
            String requested=getIntent().getStringExtra("path");
            File f=requested==null?LabStore.session(this):new File(requested);
            if(!BuildConfig.VULNERABLE) {
                File allowed=new File(getCacheDir(),"published/public-guide.txt").getCanonicalFile();
                if(!allowed.equals(f.getCanonicalFile())) throw new SecurityException("Only the public handout may be shared");
            }
            Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",f);
            Intent result=new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            result.setClipData(ClipData.newRawUri("Lab evidence",uri));
            setResult(RESULT_OK,result);
            if(BuildConfig.VULNERABLE) LabStore.record(this,2,"FLAG{provider_grant_crosses_app_sandbox} "+f.getName());
        } catch(Exception e) { setResult(RESULT_CANCELED,new Intent().putExtra("error",e.getMessage())); }
        finish();
    }
}
