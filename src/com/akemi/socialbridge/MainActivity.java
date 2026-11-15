package com.akemi.socialbridge;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.provider.Settings;
import android.view.Gravity;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.Random;

public class MainActivity extends Activity {
    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final int PICK_FILE = 42;

    private WebView igWeb, redditWeb, newsWeb, geminiWeb, activeWeb;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences prefs = getSharedPreferences("bridge_prefs", MODE_PRIVATE);
        String pin = prefs.getString("pin", null);
        if (pin == null) {
            pin = String.format("%04d", new Random().nextInt(10000));
            prefs.edit().putString("pin", pin).apply();
        }
        BridgeService.pin = pin;

        Intent svc = new Intent(this, BridgeService.class);
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(svc);
        else startService(svc);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0B1120"));

        LinearLayout bar1 = new LinearLayout(this);
        bar1.setOrientation(LinearLayout.HORIZONTAL);
        bar1.setGravity(Gravity.CENTER_VERTICAL);
        bar1.setPadding(14, 10, 14, 6);
        bar1.setBackgroundColor(Color.parseColor("#111827"));

        TextView infoTv = new TextView(this);
        infoTv.setText("IP: " + getLocalIp() + ":8765 | PIN: " + pin);
        infoTv.setTextColor(Color.parseColor("#34D399"));
        infoTv.setTextSize(11.5f);
        infoTv.setPadding(0, 0, 8, 0);
        bar1.addView(infoTv);

        Button btnPerm = new Button(this);
        btnPerm.setText("🔔");
        btnPerm.setBackgroundColor(Color.parseColor("#1E293B"));
        btnPerm.setTextColor(Color.parseColor("#FBBF24"));
        btnPerm.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        bar1.addView(btnPerm);

        Button btnFile = new Button(this);
        btnFile.setText("📎");
        btnFile.setBackgroundColor(Color.parseColor("#0284C7"));
        btnFile.setTextColor(Color.WHITE);
        btnFile.setOnClickListener(v -> {
            Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);
            pick.setType("*/*");
            startActivityForResult(pick, PICK_FILE);
        });
        bar1.addView(btnFile);
        root.addView(bar1);

        LinearLayout bar2 = new LinearLayout(this);
        bar2.setOrientation(LinearLayout.HORIZONTAL);
        bar2.setGravity(Gravity.CENTER_VERTICAL);
        bar2.setPadding(8, 4, 8, 8);
        bar2.setBackgroundColor(Color.parseColor("#111827"));

        final Button btnIg = new Button(this); btnIg.setText("Insta"); btnIg.setTextSize(10.5f);
        btnIg.setBackgroundColor(Color.parseColor("#DB2777")); btnIg.setTextColor(Color.WHITE);

        final Button btnRd = new Button(this); btnRd.setText("Reddit"); btnRd.setTextSize(10.5f);
        btnRd.setBackgroundColor(Color.parseColor("#1E293B")); btnRd.setTextColor(Color.WHITE);

        final Button btnNw = new Button(this); btnNw.setText("News"); btnNw.setTextSize(10.5f);
        btnNw.setBackgroundColor(Color.parseColor("#1E293B")); btnNw.setTextColor(Color.WHITE);

        final Button btnGm = new Button(this); btnGm.setText("Gemini"); btnGm.setTextSize(10.5f);
        btnGm.setBackgroundColor(Color.parseColor("#1E293B")); btnGm.setTextColor(Color.WHITE);

        bar2.addView(btnIg); bar2.addView(btnRd); bar2.addView(btnNw); bar2.addView(btnGm);
        root.addView(bar2);

        FrameLayout stack = new FrameLayout(this);
        geminiWeb = buildWebView("https://gemini.google.com/app");
        newsWeb = buildWebView("https://news.google.com/?hl=it&gl=IT&ceid=IT:it");
        redditWeb = buildWebView("https://www.reddit.com/");
        igWeb = buildWebView("https://www.instagram.com/");
        activeWeb = igWeb;

        BridgeService.igWeb = igWeb;
        BridgeService.redditWeb = redditWeb;
        BridgeService.newsWeb = newsWeb;
        BridgeService.geminiWeb = geminiWeb;

        FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        stack.addView(geminiWeb, fp); stack.addView(newsWeb, fp); stack.addView(redditWeb, fp); stack.addView(igWeb, fp);
        root.addView(stack, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        btnIg.setOnClickListener(v -> {
            activeWeb = igWeb; igWeb.bringToFront();
            btnIg.setBackgroundColor(Color.parseColor("#DB2777"));
            btnRd.setBackgroundColor(Color.parseColor("#1E293B"));
            btnNw.setBackgroundColor(Color.parseColor("#1E293B"));
            btnGm.setBackgroundColor(Color.parseColor("#1E293B"));
        });
        btnRd.setOnClickListener(v -> {
            activeWeb = redditWeb; redditWeb.bringToFront();
            btnRd.setBackgroundColor(Color.parseColor("#EA580C"));
            btnIg.setBackgroundColor(Color.parseColor("#1E293B"));
            btnNw.setBackgroundColor(Color.parseColor("#1E293B"));
            btnGm.setBackgroundColor(Color.parseColor("#1E293B"));
        });
        btnNw.setOnClickListener(v -> {
            activeWeb = newsWeb; newsWeb.bringToFront();
            btnNw.setBackgroundColor(Color.parseColor("#0284C7"));
            btnIg.setBackgroundColor(Color.parseColor("#1E293B"));
            btnRd.setBackgroundColor(Color.parseColor("#1E293B"));
            btnGm.setBackgroundColor(Color.parseColor("#1E293B"));
        });
        btnGm.setOnClickListener(v -> {
            activeWeb = geminiWeb; geminiWeb.bringToFront();
            btnGm.setBackgroundColor(Color.parseColor("#7C3AED"));
            btnIg.setBackgroundColor(Color.parseColor("#1E293B"));
            btnRd.setBackgroundColor(Color.parseColor("#1E293B"));
            btnNw.setBackgroundColor(Color.parseColor("#1E293B"));
        });

        setContentView(root);
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (igWeb != null) { igWeb.onResume(); igWeb.resumeTimers(); }
            if (redditWeb != null) { redditWeb.onResume(); redditWeb.resumeTimers(); }
            if (newsWeb != null) { newsWeb.onResume(); newsWeb.resumeTimers(); }
            if (geminiWeb != null) { geminiWeb.onResume(); geminiWeb.resumeTimers(); }
        } catch (Exception ignored) {}
    }

    private String getLocalIp() {
        try {
            String fallback = "127.0.0.1";
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip != null && (ip.startsWith("192.168.") || ip.startsWith("10."))) return ip;
                        if (ip != null) fallback = ip;
                    }
                }
            }
            return fallback;
        } catch (Exception e) { return "127.0.0.1"; }
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == PICK_FILE && resCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (InputStream is = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                NotifService.sharedFileBytes = out.toByteArray();
                NotifService.sharedFileName = queryName(uri);
                Toast.makeText(this, "Pronto per Watch: " + NotifService.sharedFileName, Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Errore file: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private String queryName(Uri uri) {
        try (Cursor c = getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return c.getString(idx);
            }
        } catch (Exception ignored) {}
        return "allegato_telefono.bin";
    }

    @SuppressLint("SetJavaScriptEnabled")
    private WebView buildWebView(String startUrl) {
        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.parseColor("#0B1120"));
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(MOBILE_UA);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        wv.addJavascriptInterface(new BridgeService.JsCallback(), "AndroidBridge");
        wv.setWebViewClient(new WebViewClient());
        wv.loadUrl(startUrl);
        return wv;
    }

    @Override
    public void onBackPressed() {
        if (activeWeb != null && activeWeb.canGoBack()) activeWeb.goBack();
        else moveTaskToBack(true);
    }
}
