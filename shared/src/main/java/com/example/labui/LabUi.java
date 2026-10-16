package com.example.labui;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Small shared native UI. No web UI or remote assets are required. */
public final class LabUi {
    public static final int BG = Color.rgb(11, 18, 32);
    public static final int PANEL = Color.rgb(21, 33, 51);
    public static final int INK = Color.rgb(238, 244, 255);
    public static final int MUTED = Color.rgb(157, 176, 199);
    public static final int MINT = Color.rgb(85, 230, 188);
    public static final int AMBER = Color.rgb(255, 195, 111);
    private LabUi() {}
    public static int dp(Activity a, int n) { return Math.round(n * a.getResources().getDisplayMetrics().density); }
    public static GradientDrawable bg(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }
    public static LinearLayout page(Activity a, String eyebrow, String title, String subtitle) {
        a.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        ScrollView scroll = new ScrollView(a); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        scroll.setFitsSystemWindows(true);
        LinearLayout root = new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,24), dp(a,24), dp(a,24), dp(a,32)); scroll.addView(root);
        a.setContentView(scroll);
        label(a,root,eyebrow,12,MINT, true);
        label(a,root,title,32,INK,true);
        label(a,root,subtitle,15,MUTED,false);
        return root;
    }
    public static TextView label(Activity a, LinearLayout parent, String text, int sp, int color, boolean bold) {
        TextView t = new TextView(a); t.setText(text); t.setTextSize(sp); t.setTextColor(color);
        t.setLineSpacing(dp(a,3),1); if (bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(a,12);
        parent.addView(t,p); return t;
    }
    public static LinearLayout card(Activity a, LinearLayout parent, String kicker, String title, String body) {
        LinearLayout c = new LinearLayout(a); c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(bg(PANEL,dp(a,18))); c.setPadding(dp(a,20),dp(a,20),dp(a,20),dp(a,12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(a,12); parent.addView(c,p);
        label(a,c,kicker,11,MINT,true); label(a,c,title,21,INK,true); label(a,c,body,14,MUTED,false); return c;
    }
    public static Button button(Activity a, LinearLayout parent, String text, Runnable action) {
        Button b=new Button(a); b.setText(text); b.setAllCaps(false); b.setTextSize(14);
        b.setTextColor(BG); b.setTypeface(Typeface.DEFAULT,Typeface.BOLD); b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(MINT));
        b.setMinHeight(dp(a,48)); b.setPadding(dp(a,14),0,dp(a,14),0);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=dp(a,8); parent.addView(b,p);
        b.setOnClickListener(v -> action.run()); return b;
    }
    public static TextView code(Activity a, LinearLayout p, String text) {
        TextView t=label(a,p,text,12,INK,false); t.setTypeface(Typeface.MONOSPACE); t.setTextIsSelectable(true); return t;
    }
}
