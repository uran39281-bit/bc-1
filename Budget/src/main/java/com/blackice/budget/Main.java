package com.blackice.budget;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.opengl.GLSurfaceView;
import android.view.*;
import android.webkit.*;
import android.widget.LinearLayout;
import com.blackice.bc1.engine.*;
import org.json.*;

public final class Main extends Activity {
    GLSurfaceView graph;
    BcRenderer renderer;
    WebView web;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(0xff0a111b);
        layout.setOnApplyWindowInsetsListener((v, insets) -> {
            v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        graph = new GLSurfaceView(this);
        graph.setEGLContextClientVersion(3);
        renderer = new BcRenderer(this, EngineSettings.defaults(), (gpu,fps,w,h) -> {});
        graph.setRenderer(renderer);
        graph.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        layout.addView(graph,-1,Math.round(52*getResources().getDisplayMetrics().density));
        web = new WebView(this);
        web.setBackgroundColor(0xff0a111b);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setBlockNetworkLoads(true);
        web.getSettings().setAllowContentAccess(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView w, WebResourceRequest req) { return true; }
        });
        web.addJavascriptInterface(new Bridge(), "Ice");
        layout.addView(web, -1, -1);
        setContentView(layout);
        web.loadUrl("file:///android_asset/index.html");
        Reminders.schedule(this);
    }

    public final class Bridge {
        @JavascriptInterface public String load() { return getSharedPreferences("budget",0).getString("data",""); }
        @JavascriptInterface public boolean save(String json) {
            return getSharedPreferences("budget",0).edit().putString("data",json).commit();
        }
        @JavascriptInterface public void notifyAccess() {
            if (Build.VERSION.SDK_INT >= 33) runOnUiThread(() -> requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1));
        }
        @JavascriptInterface public void chart(String json,String month) throws JSONException {
            JSONArray values = new JSONArray(json);
            Bitmap bmp = Bitmap.createBitmap(900,112,Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(bmp); c.drawColor(0xff101d2c);
            Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
            pen.setColor(0xffa5c4d7); pen.setTextSize(25);
            c.drawText(month + "  /  SCHEDULED BILLS",22,38,pen);
            double peak=1;
            for(int i=0;i<values.length();i++) peak=Math.max(peak,values.optDouble(i));
            for(int i=0;i<values.length();i++){
                int x=22+i*27;
                double amount=values.optDouble(i);
                pen.setColor(amount>0?0xff76dcf6:0xff334b60);
                c.drawRoundRect(x,(float)(96-43*amount/peak)-3,x+19,96,3,3,pen);
            }
            graph.queueEvent(() -> renderer.setInput(0,bmp));
            graph.requestRender();
        }
    }
    @Override protected void onPause(){ super.onPause(); graph.onPause(); }
    @Override protected void onResume(){ super.onResume(); if(graph!=null) graph.onResume(); }
    @Override public void onBackPressed(){ web.evaluateJavascript("closeSheet()",v->{ if(!"true".equals(v)) super.onBackPressed(); }); }
}
