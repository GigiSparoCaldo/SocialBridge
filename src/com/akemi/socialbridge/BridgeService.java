package com.akemi.socialbridge;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.webkit.CookieManager;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
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
import org.json.JSONObject;
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
        NotifService.onlyImportantApps = getSharedPreferences("bridge_prefs", MODE_PRIVATE).getBoolean("only_imp", false);
        startForegroundNotif();
        running = true;
        pool.execute(this::listenLoop);
    }

    private void startForegroundNotif() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("sb_ch", "SocialBridge", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, "sb_ch")
                    .setContentTitle("SocialBridge + WristDrop")
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
        try (Socket s = sock; InputStream rawIn = s.getInputStream(); OutputStream out = s.getOutputStream()) {
            String reqLine = readLine(rawIn);
            if (reqLine == null || reqLine.isEmpty()) return;
            String[] parts = reqLine.split(" ");
            String method = parts[0];
            String fullPath = parts.length > 1 ? parts[1] : "/";

            String reqPin = "";
            int contentLen = 0;
            String fileNameHdr = "watch_file.bin";
            String line;
            while ((line = readLine(rawIn)) != null && !line.isEmpty()) {
                String low = line.toLowerCase();
                if (low.startsWith("x-pin:")) reqPin = line.substring(6).trim();
                else if (low.startsWith("content-length:")) contentLen = Integer.parseInt(line.substring(15).trim());
                else if (low.startsWith("x-filename:")) fileNameHdr = URLDecoder.decode(line.substring(11).trim(), "UTF-8");
            }

            String path = fullPath.contains("?") ? fullPath.substring(0, fullPath.indexOf('?')) : fullPath;
            Map<String, String> q = parseQuery(fullPath);
            if (reqPin.isEmpty() && q.containsKey("pin")) reqPin = q.get("pin");

            if (!pin.equals(reqPin)) {
                sendJson(out, 401, "{\"ok\":false,\"err\":\"PIN errato\"}");
                return;
            }

            if ("/notif".equals(path)) {
                sendJson(out, 200, NotifService.getListJson());
            } else if ("/notif/reply".equals(path)) {
                boolean ok = NotifService.sendReply(this, q.getOrDefault("key", ""), q.getOrDefault("text", ""));
                sendJson(out, 200, "{\"ok\":" + ok + "}");
            } else if ("/clip".equals(path)) {
                if ("POST".equalsIgnoreCase(method) && contentLen > 0) {
                    String txt = new String(readExact(rawIn, contentLen), StandardCharsets.UTF_8);
                    NotifService.sharedClip = txt;
                    setPhoneClipboard(txt);
                    sendJson(out, 200, "{\"ok\":true}");
                } else {
                    String phoneClip = getPhoneClipboard();
                    String active = !phoneClip.isEmpty() ? phoneClip : NotifService.sharedClip;
                    sendJson(out, 200, new JSONObject().put("ok", true).put("text", active).toString());
                }
            } else if ("/file".equals(path)) {
                if ("POST".equalsIgnoreCase(method) && contentLen > 0) {
                    byte[] data = readExact(rawIn, contentLen);
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    if (!dir.exists()) dir.mkdirs();
                    File dest = new File(dir, "Watch_" + fileNameHdr.replaceAll("[^a-zA-Z0-9._-]", "_"));
                    try (FileOutputStream fos = new FileOutputStream(dest)) { fos.write(data); }
                    sendJson(out, 200, new JSONObject().put("ok", true).put("saved", dest.getName()).toString());
                } else {
                    byte[] fb = NotifService.sharedFileBytes;
                    if (fb == null || fb.length == 0) { sendJson(out, 404, "{\"ok\":false,\"err\":\"Nessun file pronto\"}"); return; }
                    String encName = java.net.URLEncoder.encode(NotifService.sharedFileName, "UTF-8");
                    String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nX-Filename: " + encName
                            + "\r\nContent-Length: " + fb.length + "\r\nConnection: close\r\n\r\n";
                    out.write(hdr.getBytes(StandardCharsets.UTF_8));
                    out.write(fb);
                    out.flush();
                }
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
                sendJson(out, 404, "{\"ok\":false}");
            }
        } catch (Exception ignored) {}
    }

    private String getPhoneClipboard() {
        final CountDownLatch l = new CountDownLatch(1);
        final AtomicReference<String> ref = new AtomicReference<>("");
        mainHandler.post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) {
                    CharSequence cs = cm.getPrimaryClip().getItemAt(0).coerceToText(this);
                    if (cs != null) ref.set(cs.toString());
                }
            } catch (Exception ignored) {}
            l.countDown();
        });
        try { l.await(2, TimeUnit.SECONDS); } catch (Exception ignored) {}
        return ref.get();
    }

    private void setPhoneClipboard(final String txt) {
        mainHandler.post(() -> {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("WristDrop", txt));
            } catch (Exception ignored) {}
        });
    }

    private String readLine(InputStream in) throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int b;
        while ((b = in.read()) != -1) {
            if (b == '\n') break;
            if (b != '\r') buf.write(b);
        }
        if (b == -1 && buf.size() == 0) return null;
        return new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private byte[] readExact(InputStream in, int len) throws Exception {
        byte[] data = new byte[len];
        int off = 0;
        while (off < len) {
            int n = in.read(data, off, len - off);
            if (n < 0) break;
            off += n;
        }
        return data;
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
            } catch (Exception e) { ref.set("{\"ok\":false}"); }
            finally { latch.countDown(); }
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
