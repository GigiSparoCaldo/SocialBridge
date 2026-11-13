package com.akemi.socialbridge;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
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
import java.util.Random;

public class MainActivity extends Activity {
    public static final String MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-F731B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

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

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(24, 18, 24, 18);
        bar.setBackgroundColor(Color.parseColor("#111827"));

        TextView pinTv = new TextView(this);
        pinTv.setText("PIN: " + pin);
        pinTv.setTextColor(Color.parseColor("#34D399"));
        pinTv.setTextSize(14f);
        pinTv.setPadding(0, 0, 20, 0);
        bar.addView(pinTv);

        Button btnIg = new Button(this);
        btnIg.setText("Instagram");
        btnIg.setBackgroundColor(Color.parseColor("#DB2777"));
        btnIg.setTextColor(Color.WHITE);

        Button btnRd = new Button(this);
        btnRd.setText("Reddit");
        btnRd.setBackgroundColor(Color.parseColor("#1E293B"));
        btnRd.setTextColor(Color.WHITE);

        Button btnRel = new Button(this);
        btnRel.setText("↻");
        btnRel.setBackgroundColor(Color.parseColor("#1E293B"));
        btnRel.setTextColor(Color.parseColor("#38BDF8"));

        bar.addView(btnIg);
        bar.addView(btnRd);
        bar.addView(btnRel);
        root.addView(bar);

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
