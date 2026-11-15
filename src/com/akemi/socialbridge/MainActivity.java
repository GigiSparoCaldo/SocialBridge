package com.akemi.socialbridge;

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
import android.webkit.WebView;
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

    private FrameLayout webSlot;
    private String activeKey = "ig";

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
        bar2.setPadding(6, 4, 6, 8);
        bar2.setBackgroundColor(Color.parseColor("#111827"));

        String[][] tabs = {{"IG", "ig", "#DB2777"}, {"FB", "fb", "#1D4ED8"}, {"Reddit", "reddit", "#EA580C"}, {"News", "news", "#0284C7"}, {"Gemini", "gemini", "#7C3AED"}};
        final Button[] btns = new Button[tabs.length];
        for (int i = 0; i < tabs.length; i++) {
            final int idx = i;
            final String key = tabs[i][1], color = tabs[i][2];
            Button b = new Button(this);
            b.setText(tabs[i][0]); b.setTextSize(10.5f);
            b.setPadding(8, 6, 8, 6); b.setMinimumWidth(0); b.setMinWidth(0);
            b.setTextColor(Color.WHITE);
            b.setBackgroundColor(Color.parseColor(i == 0 ? color : "#1E293B"));
            b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            b.setOnClickListener(v -> {
                for (Button x : btns) x.setBackgroundColor(Color.parseColor("#1E293B"));
                btns[idx].setBackgroundColor(Color.parseColor(color));
                switchTab(key);
            });
            btns[i] = b;
            bar2.addView(b);
        }
        root.addView(bar2);

        webSlot = new FrameLayout(this);
        root.addView(webSlot, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        BridgeService.uiVisible = true;
        switchTab(activeKey);
    }

    @Override
    protected void onPause() {
        BridgeService.uiVisible = false;
        webSlot.removeAllViews();
        BridgeService.detachAllFromUi(getApplicationContext());
        super.onPause();
    }

    private void switchTab(String key) {
        activeKey = key;
        webSlot.removeAllViews();
        WebView wv = BridgeService.getOrCreateWebView(this, key);
        if (wv.getParent() instanceof ViewGroup) ((ViewGroup) wv.getParent()).removeView(wv);
        wv.onResume(); wv.resumeTimers();
        webSlot.addView(wv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
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
            try (InputStream is = getContentResolver().openInputStream(uri); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                NotifService.sharedFileBytes = out.toByteArray();
                NotifService.sharedFileName = queryName(uri);
                Toast.makeText(this, "Pronto per Watch: " + NotifService.sharedFileName, Toast.LENGTH_LONG).show();
            } catch (Exception e) { Toast.makeText(this, "Errore file: " + e.getMessage(), Toast.LENGTH_SHORT).show(); }
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

    @Override
    public void onBackPressed() {
        WebView wv = BridgeService.getOrCreateWebView(this, activeKey);
        if (wv.canGoBack()) wv.goBack();
        else moveTaskToBack(true);
    }
}
