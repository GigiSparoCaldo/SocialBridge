package com.akemi.socialbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebView;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONTokener;

public class BridgeService extends Service {
    public static final int PORT = 8765;
    public static volatile WebView igWeb;
    public static volatile WebView redditWeb;
    public static volatile String pin = "1234";

    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        pin = getSharedPreferences("bridge_prefs", MODE_PRIVATE).getString("pin", "1234");
        startForegroundNotif();
        running = true;
        pool.execute(this::listenLoop);
    }

    private void startForegroundNotif() {
        String chId = "social_bridge_ch";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(chId, "SocialBridge", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, chId)
                    .setContentTitle("SocialBridge attivo")
                    .setContentText("Porta " + PORT + " • PIN: " + pin)
                    .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .build();
            startForeground(101, n);
        }
    }

    private void listenLoop() {
        try {
            serverSocket = new ServerSocket(PORT);
            while (running) {
                Socket client = serverSocket.accept();
                pool.execute(() -> handleClient(client));
            }
        } catch (Exception ignored) {}
    }

    private void handleClient(Socket sock) {
        try (Socket s = sock;
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
             OutputStream out = s.getOutputStream()) {

            String reqLine = in.readLine();
            if (reqLine == null || reqLine.isEmpty()) return;
            String[] parts = reqLine.split(" ");
            String fullPath = parts.length > 1 ? parts[1] : "/";

            String reqPin = "";
            String line;
            while ((line = in.readLine()) != null && !line.isEmpty()) {
                if (line.toLowerCase().startsWith("x-pin:")) {
                    reqPin = line.substring(6).trim();
                }
            }

            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;
            Map<String, String> q = parseQuery(fullPath);
            if (reqPin.isEmpty() && q.containsKey("pin")) reqPin = q.get("pin");

            if (!pin.equals(reqPin)) {
                sendJson(out, 401, "{\"ok\":false,\"err\":\"PIN errato\"}");
                return;
            }

            if ("/ping".equals(path)) {
                sendJson(out, 200, "{\"ok\":true,\"ig\":" + (igWeb != null) + ",\"reddit\":" + (redditWeb != null) + "}");
            } else if ("/img".equals(path)) {
                serveCompressedImage(out, q.get("url"));
            } else if ("/ig/feed".equals(path)) {
                sendJson(out, 200, evalJs(igWeb, JsScripts.igFeed()));
            } else if ("/ig/stories".equals(path)) {
                sendJson(out, 200, evalJs(igWeb, JsScripts.igStories()));
            } else if ("/ig/story".equals(path)) {
                sendJson(out, 200, evalJs(igWeb, JsScripts.igStoryMedia(q.getOrDefault("id", ""))));
            } else if ("/ig/like".equals(path)) {
                sendJson(out, 200, evalJs(igWeb, JsScripts.igLike(q.getOrDefault("id", ""))));
            } else if ("/ig/dm".equals(path)) {
                sendJson(out, 200, evalJs(igWeb, JsScripts.igDmInbox()));
            } else if ("/ig/dm_send".equals(path)) {
                sendJson(out, 200, evalJs(igWeb, JsScripts.igDmSend(q.getOrDefault("id", ""), q.getOrDefault("text", ""))));
            } else if ("/reddit/feed".equals(path)) {
                sendJson(out, 200, evalJs(redditWeb, JsScripts.redditFeed(q.getOrDefault("sub", ""), q.getOrDefault("sort", "hot"))));
            } else if ("/reddit/comments".equals(path)) {
                sendJson(out, 200, evalJs(redditWeb, JsScripts.redditComments(q.getOrDefault("url", ""))));
            } else {
                sendJson(out, 404, "{\"ok\":false,\"err\":\"Not found\"}");
            }
        } catch (Exception ignored) {}
    }

    private String evalJs(final WebView wv, final String script) {
        if (wv == null) return "{\"ok\":false,\"err\":\"Apri SocialBridge sullo Z Flip\"}";
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<String> ref = new AtomicReference<>("{\"ok\":false,\"err\":\"Timeout JS\"}");
        mainHandler.post(() -> wv.evaluateJavascript(script, val -> {
            try {
                if (val != null && !"null".equals(val)) {
                    Object parsed = new JSONTokener(val).nextValue();
                    ref.set(parsed instanceof String ? (String) parsed : val);
                }
            } catch (Exception e) {
                ref.set("{\"ok\":false,\"err\":\"Parse JS\"}");
            } finally {
                latch.countDown();
            }
        }));
        try { latch.await(8, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        return ref.get();
    }

    private void serveCompressedImage(OutputStream out, String imgUrl) throws Exception {
        if (imgUrl == null || imgUrl.isEmpty()) { sendJson(out, 400, "{}"); return; }
        HttpURLConnection c = (HttpURLConnection) new URL(imgUrl).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
        String cookie = CookieManager.getInstance().getCookie(imgUrl);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream raw = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) != -1) raw.write(buf, 0, n);
            byte[] bytes = raw.toByteArray();

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int sample = 1;
            while (o.outWidth / sample > 420) sample *= 2;
            o.inJustDecodeBounds = false;
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            ByteArrayOutputStream jpg = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 72, jpg);
            byte[] outBytes = jpg.toByteArray();

            String hdr = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + outBytes.length + "\r\nConnection: close\r\n\r\n";
            out.write(hdr.getBytes(StandardCharsets.UTF_8));
            out.write(outBytes);
            out.flush();
        } finally { c.disconnect(); }
    }

    private void sendJson(OutputStream out, int code, String json) throws Exception {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 " + code + " OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.UTF_8));
        out.write(b);
        out.flush();
    }

    private Map<String, String> parseQuery(String full) {
        Map<String, String> map = new HashMap<>();
        int q = full.indexOf('?');
        if (q < 0) return map;
        for (String pair : full.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try {
                    map.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"),
                            URLDecoder.decode(pair.substring(eq + 1), "UTF-8"));
                } catch (Exception ignored) {}
            }
        }
        return map;
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (Exception ignored) {}
        pool.shutdownNow();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
