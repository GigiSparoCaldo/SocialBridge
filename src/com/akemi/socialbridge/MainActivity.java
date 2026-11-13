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
import android.view.View;
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
import java.util.Random;

public class MainActivity extends Activity {
    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";
    private static final int PICK_FILE = 42;

    private WebView igWeb;
    private WebView redditWeb;

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

        // Riga 1: PIN + Permesso Notifiche + Invia File al Watch
        LinearLayout bar1 = new LinearLayout(this);
        bar1.setOrientation(LinearLayout.HORIZONTAL);
        bar1.setGravity(Gravity.CENTER_VERTICAL);
        bar1.setPadding(16, 12, 16, 6);
        bar1.setBackgroundColor(Color.parseColor("#111827"));

        TextView pinTv = new TextView(this);
        pinTv.setText("PIN: " + pin);
        pinTv.setTextColor(Color.parseColor("#34D399"));
        pinTv.setTextSize(13f);
        pinTv.setPadding(0, 0, 12, 0);
        bar1.addView(pinTv);

        Button btnPerm = new Button(this);
        btnPerm.setText("🔔 Permesso");
        btnPerm.setTextSize(11f);
        btnPerm.setBackgroundColor(Color.parseColor("#1E293B"));
        btnPerm.setTextColor(Color.parseColor("#FBBF24"));
        btnPerm.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        bar1.addView(btnPerm);

        Button btnFile = new Button(this);
        btnFile.setText("📎 File→Watch");
        btnFile.setTextSize(11f);
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

        // Riga 2: Instagram | Reddit | Filtro Notifiche | Reload
        LinearLayout bar2 = new LinearLayout(this);
        bar2.setOrientation(LinearLayout.HORIZONTAL);
        bar2.setGravity(Gravity.CENTER_VERTICAL);
        bar2.setPadding(16, 4, 16, 10);
        bar2.setBackgroundColor(Color.parseColor("#111827"));

        Button btnIg = new Button(this);
        btnIg.setText("Instagram");
        btnIg.setTextSize(11f);
        btnIg.setBackgroundColor(Color.parseColor("#DB2777"));
        btnIg.setTextColor(Color.WHITE);

        Button btnRd = new Button(this);
        btnRd.setText("Reddit");
        btnRd.setTextSize(11f);
        btnRd.setBackgroundColor(Color.parseColor("#1E293B"));
        btnRd.setTextColor(Color.WHITE);

        final Button btnFilt = new Button(this);
        btnFilt.setText(NotifService.onlyImportantApps ? "Filtro: Solo Banche/SMS" : "Filtro: Tutte le App");
        btnFilt.setTextSize(10.5f);
        btnFilt.setBackgroundColor(Color.parseColor("#1E293B"));
        btnFilt.setTextColor(Color.parseColor("#38BDF8"));
        btnFilt.setOnClickListener(v -> {
            NotifService.onlyImportantApps = !NotifService.onlyImportantApps;
            prefs.edit().putBoolean("only_imp", NotifService.onlyImportantApps).apply();
            btnFilt.setText(NotifService.onlyImportantApps ? "Filtro: Solo Banche/SMS" : "Filtro: Tutte le App");
        });

        Button btnRel = new Button(this);
        btnRel.setText("↻");
        btnRel.setBackgroundColor(Color.parseColor("#1E293B"));
        btnRel.setTextColor(Color.WHITE);

        bar2.addView(btnIg);
        bar2.addView(btnRd);
        bar2.addView(btnFilt);
        bar2.addView(btnRel);
        root.addView(bar2);

        FrameLayout stack = new FrameLayout(this);
        igWeb = buildWebView("https://www.instagram.com/");
        redditWeb = buildWebView("https://www.reddit.com/");
        redditWeb.setVisibility(View.GONE);

        BridgeService.igWeb = igWeb;
        BridgeService.redditWeb = redditWeb;

        stack.addView(igWeb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        stack.addView(redditWeb, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        root.addView(stack, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        btnIg.setOnClickListener(v -> {
            igWeb.setVisibility(View.VISIBLE);
            redditWeb.setVisibility(View.GONE);
            btnIg.setBackgroundColor(Color.parseColor("#DB2777"));
            btnRd.setBackgroundColor(Color.parseColor("#1E293B"));
        });

        btnRd.setOnClickListener(v -> {
            redditWeb.setVisibility(View.VISIBLE);
            igWeb.setVisibility(View.GONE);
            btnRd.setBackgroundColor(Color.parseColor("#EA580C"));
            btnIg.setBackgroundColor(Color.parseColor("#1E293B"));
        });

        btnRel.setOnClickListener(v -> {
            if (igWeb.getVisibility() == View.VISIBLE) igWeb.reload();
            else redditWeb.reload();
        });

        setContentView(root);
    }

    @Override
    protected void onActivityResult(int reqCode, int resCode, Intent data) {
        super.onActivityResult(reqCode, resCode, data);
        if (reqCode == PICK_FILE && resCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try (InputStream is = getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
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
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setUserAgentString(MOBILE_UA);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);
        wv.setWebViewClient(new WebViewClient());
        wv.loadUrl(startUrl);
        return wv;
    }

    @Override
    public void onBackPressed() {
        WebView active = igWeb.getVisibility() == View.VISIBLE ? igWeb : redditWeb;
        if (active.canGoBack()) active.goBack();
        else moveTaskToBack(true);
    }
}
