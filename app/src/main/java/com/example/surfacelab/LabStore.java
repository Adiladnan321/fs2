package com.example.surfacelab;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public final class LabStore {
    public static final String TOKEN="LAB_SESSION_f3a9_synthetic_only";
    private static SQLiteDatabase ledger;
    private LabStore() {}
    public static File evidenceRoot(Context c) { return BuildConfig.VULNERABLE ? c.getFilesDir() : c.getNoBackupFilesDir(); }
    public static File session(Context c) { return new File(evidenceRoot(c),"session.json"); }
    public static void write(File file,String content) throws Exception {
        File parent=file.getParentFile(); if (parent!=null && !parent.exists() && !parent.mkdirs()) throw new java.io.IOException("Cannot create " + parent);
        Files.write(file.toPath(),content.getBytes(StandardCharsets.UTF_8));
    }
    public static String read(File file) throws Exception { return new String(Files.readAllBytes(file.toPath()),StandardCharsets.UTF_8); }
    public static SharedPreferences progress(Context c) { return c.getSharedPreferences("lab_progress",Context.MODE_PRIVATE); }
    public static synchronized void record(Context c,int lab,String result) {
        String event="lab="+lab+" "+result;
        Log.i("SurfaceLab",event);
        progress(c).edit().putBoolean("lab_"+lab,true).apply();
        try (FileOutputStream out=new FileOutputStream(new File(c.getFilesDir(),"events.log"),true)) {
            out.write((System.currentTimeMillis()+" "+event+"\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { Log.e("SurfaceLab","Evidence log unavailable",e); }
    }
    public static synchronized void seed(Context c) throws Exception {
        if (!session(c).exists()) {
            JSONObject s=new JSONObject().put("owner","Avery / training account").put("session_token",TOKEN)
                .put("account","DEMO-0042-8001").put("flag","FLAG{provider_grant_crosses_app_sandbox}")
                .put("synthetic",true);
            write(session(c),s.toString(2));
        }
        if (BuildConfig.VULNERABLE) c.getSharedPreferences("session",Context.MODE_PRIVATE).edit()
            .putString("session_token",TOKEN).putString("account","DEMO-0042-8001").commit();
        File publicDoc=new File(c.getCacheDir(),"published/public-guide.txt");
        write(publicDoc,"SurfaceLab public handout. No session data is shared here.\n");
        if (ledger==null) {
            File db=BuildConfig.VULNERABLE ? c.getDatabasePath("ledger.db") : new File(c.getNoBackupFilesDir(),"ledger.db");
            db.getParentFile().mkdirs(); ledger=SQLiteDatabase.openOrCreateDatabase(db,null);
            ledger.enableWriteAheadLogging();
            ledger.execSQL("CREATE TABLE IF NOT EXISTS transactions (id INTEGER PRIMARY KEY, recipient TEXT, amount INTEGER, note TEXT)");
            ledger.execSQL("INSERT OR IGNORE INTO transactions VALUES (1, 'Training merchant', 4200, 'SYNTHETIC invoice INV-1042')");
        }
        File invoice=invoice(c);
        if (!invoice.exists()) {
            invoice.getParentFile().mkdirs();
            PdfDocument doc=new PdfDocument(); PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(595,842,1).create());
            Paint p=new Paint(); p.setTextSize(22); page.getCanvas().drawText("SurfaceLab / SYNTHETIC INVOICE",40,70,p);
            p.setTextSize(15); String[] lines={"Invoice INV-1042", "Account DEMO-0042-8001", "Avery / training account", "Amount 42.00 demo credits", "FLAG{external_invoice_artifact}","No real customer or payment data."};
            int y=120; for (String l:lines) { page.getCanvas().drawText(l,40,y,p); y+=32; }
            doc.finishPage(page); try (FileOutputStream out=new FileOutputStream(invoice)) { doc.writeTo(out); } finally { doc.close(); }
        }
        if (BuildConfig.VULNERABLE) VaultCrypto.ensureFixture(c);
    }
    public static File invoice(Context c) {
        File dir=BuildConfig.VULNERABLE ? c.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) : c.getNoBackupFilesDir();
        if (dir==null) dir=c.getFilesDir(); return new File(dir,"invoice-1042.pdf");
    }
    public static String transfer(Context c,String recipient,int amount) {
        ContentValues v=new ContentValues(); v.put("recipient",recipient); v.put("amount",amount); v.put("note","Unauthenticated deep-link lab transfer");
        ledger.insertOrThrow("transactions",null,v); record(c,1,"FLAG{deep_link_without_auth} recipient="+recipient+" amount="+amount);
        return "FLAG{deep_link_without_auth}\nRecorded "+amount+" demo credits for "+recipient+" without authentication.";
    }
    public static String exportAudit(Context c) throws Exception {
        String text="SurfaceLab / synthetic audit\nFLAG{mutable_ticket_admin_execution}\nCreator package: "+c.getPackageName()+"\nAccount: DEMO-0042-8001\nTraining transaction: 42.00 credits\n";
        if (android.os.Build.VERSION.SDK_INT>=29) {
            ContentValues v=new ContentValues(); v.put(MediaStore.Downloads.DISPLAY_NAME,"leaked_audit.txt");
            v.put(MediaStore.Downloads.MIME_TYPE,"text/plain"); v.put(MediaStore.Downloads.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/SurfaceLab");
            v.put(MediaStore.Downloads.IS_PENDING,1);
            Uri uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);
            if (uri==null) throw new java.io.IOException("Download insert failed");
            try (OutputStream out=c.getContentResolver().openOutputStream(uri)) {
                if (out==null) throw new java.io.IOException("Download stream unavailable"); out.write(text.getBytes(StandardCharsets.UTF_8));
            }
            v.clear(); v.put(MediaStore.Downloads.IS_PENDING,0); c.getContentResolver().update(uri,v,null,null);
            return uri+"\nDownloads/SurfaceLab/leaked_audit.txt";
        }
        File out=new File(c.getExternalFilesDir(null),"leaked_audit.txt"); write(out,text); return out.toString();
    }
}
