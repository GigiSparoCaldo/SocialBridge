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
import android.text.Html;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class BridgeService extends Service {
    public static final int PORT = 8765;
    public static volatile WebView igWeb, redditWeb, newsWeb, geminiWeb;
    public static volatile String pin = "1234";

    private static final ConcurrentHashMap<String, CountDownLatch> latches = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();

    public static class JsCallback {
        @JavascriptInterface
        public void done(String reqId, String json) {
            if (reqId != null && json != null) {
                results.put(reqId, json);
                CountDownLatch l = latches.get(reqId);
                if (l != null) l.countDown();
            }
        }
    }

    private ServerSocket serverSocket;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;

    @Override
    public void onCreate() {
        super.onCreate();
        pin = getSharedPreferences("bridge_prefs", MODE_PRIVATE).getString("pin", "1234");
        NotifService.onlyImportantApps = getSharedPreferences("bridge_prefs", MODE_PRIVATE).getBoolean("only_imp", false);
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
        running = true;
        pool.execute(this::listenLoop);
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
            String method = parts[0], fullPath = parts.length > 1 ? parts[1] : "/";
            String reqPin = "", fileNameHdr = "watch_file.bin";
            int contentLen = 0;
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
            if (!pin.equals(reqPin)) { sendJson(out, 401, "{\"ok\":false,\"err\":\"PIN errato\"}"); return; }

            if ("/ping".equals(path)) sendJson(out, 200, "{\"ok\":true}");
            else if ("/notif".equals(path)) sendJson(out, 200, NotifService.getListJson());
            else if ("/notif/reply".equals(path)) sendJson(out, 200, "{\"ok\":" + NotifService.sendReply(this, q.getOrDefault("key", ""), q.getOrDefault("text", "")) + "}");
            else if ("/clip".equals(path)) {
                if ("POST".equalsIgnoreCase(method) && contentLen > 0) {
                    String txt = new String(readExact(rawIn, contentLen), StandardCharsets.UTF_8);
                    NotifService.sharedClip = txt; setPhoneClipboard(txt); sendJson(out, 200, "{\"ok\":true}");
                } else {
                    String pc = getPhoneClipboard();
                    sendJson(out, 200, new JSONObject().put("ok", true).put("text", !pc.isEmpty() ? pc : NotifService.sharedClip).toString());
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
                    if (fb == null || fb.length == 0) { sendJson(out, 404, "{\"ok\":false}"); return; }
                    String hdr = "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nX-Filename: " + URLEncoder.encode(NotifService.sharedFileName, "UTF-8") + "\r\nContent-Length: " + fb.length + "\r\nConnection: close\r\n\r\n";
                    out.write(hdr.getBytes(StandardCharsets.UTF_8)); out.write(fb); out.flush();
                }
            } else if ("/img".equals(path)) serveCompressedImage(out, q.get("url"));
            else if ("/vid".equals(path)) serveVideoProxy(out, q.get("url"));
            else if ("/gemini/models".equals(path)) sendJson(out, 200, evalJs(geminiWeb, JsScripts.geminiListModels(), 8));
            else if ("/gemini/chats".equals(path)) sendJson(out, 200, evalJs(geminiWeb, JsScripts.geminiListChats(), 8));
            else if ("/gemini/open".equals(path)) sendJson(out, 200, openGeminiChat(q.getOrDefault("url", "")));
            else if ("/gemini/new".equals(path)) { loadGeminiUrl("https://gemini.google.com/app"); sendJson(out, 200, "{\"ok\":true}"); }
            else if ("/gemini/ask".equals(path)) sendJson(out, 200, evalJs(geminiWeb, JsScripts.geminiAsk(q.getOrDefault("q", ""), q.getOrDefault("model", "auto"), q.getOrDefault("think", "medio")), 28));
            else if ("/news/feed".equals(path)) {
                String domJson = evalJs(newsWeb, JsScripts.googleNewsFeed(), 8);
                JSONArray arr = new JSONObject(domJson).optJSONArray("items");
                sendJson(out, 200, (arr != null && arr.length() > 0) ? domJson : fetchGoogleNewsRssFallback());
            } else if ("/news/article".equals(path)) sendJson(out, 200, fetchArticleJson(q.getOrDefault("url", "")));
            else if ("/ig/feed".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igFeed(q.getOrDefault("max_id", "")), 10));
            else if ("/ig/explore".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igExplore(), 10));
            else if ("/ig/saved".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igSaved(), 10));
            else if ("/ig/stories".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igStories(), 10));
            else if ("/ig/story".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igStoryMedia(q.getOrDefault("id", "")), 10));
            else if ("/ig/profile".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igProfile(q.getOrDefault("user", "")), 10));
            else if ("/ig/follow".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igFollow(q.getOrDefault("id", ""), "1".equals(q.get("unfollow"))), 8));
            else if ("/ig/search".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igSearch(q.getOrDefault("q", "")), 8));
            else if ("/ig/comments".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igComments(q.getOrDefault("id", "")), 10));
            else if ("/ig/comment_add".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igAddComment(q.getOrDefault("id", ""), q.getOrDefault("text", "")), 10));
            else if ("/ig/like".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igLike(q.getOrDefault("id", "")), 8));
            else if ("/ig/dm".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igDmInbox(), 10));
            else if ("/ig/dm_thread".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igDmThread(q.getOrDefault("id", "")), 10));
            else if ("/ig/dm_send".equals(path)) sendJson(out, 200, evalJs(igWeb, JsScripts.igDmSend(q.getOrDefault("id", ""), q.getOrDefault("text", "")), 10));
            else if ("/reddit/feed".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditFeed(q.getOrDefault("sub", ""), q.getOrDefault("sort", "hot"), q.getOrDefault("after", "")), 10));
            else if ("/reddit/comments".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditComments(q.getOrDefault("url", "")), 10));
            else if ("/reddit/user".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditUser(q.getOrDefault("u", "")), 10));
            else if ("/reddit/sub_info".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditSubInfo(q.getOrDefault("sub", "")), 8));
            else if ("/reddit/sub_toggle".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditAction("/api/subscribe", "action=" + ("1".equals(q.get("unsub")) ? "unsub" : "sub") + "&sr_name=" + URLEncoder.encode(q.getOrDefault("sub", ""), "UTF-8")), 8));
            else if ("/reddit/search".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditSearch(q.getOrDefault("q", "")), 10));
            else if ("/reddit/saved".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditSaved(), 10));
            else if ("/reddit/vote".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditAction("/api/vote", "id=" + URLEncoder.encode(q.getOrDefault("id", ""), "UTF-8") + "&dir=" + q.getOrDefault("dir", "1")), 8));
            else if ("/reddit/save".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditAction("/api/save", "id=" + URLEncoder.encode(q.getOrDefault("id", ""), "UTF-8")), 8));
            else if ("/reddit/comment_add".equals(path)) sendJson(out, 200, evalJs(redditWeb, JsScripts.redditAction("/api/comment", "thing_id=" + URLEncoder.encode(q.getOrDefault("thing", ""), "UTF-8") + "&text=" + URLEncoder.encode(q.getOrDefault("text", ""), "UTF-8")), 10));
            else sendJson(out, 404, "{\"ok\":false}");
        } catch (Exception ignored) {}
    }

    private void loadGeminiUrl(final String url) {
        if (geminiWeb == null) return;
        mainHandler.post(() -> {
            try { geminiWeb.onResume(); geminiWeb.resumeTimers(); geminiWeb.loadUrl(url); } catch (Exception ignored) {}
        });
    }

    private String openGeminiChat(String url) {
        if (url == null || url.isEmpty()) return "{\"ok\":false,\"turns\":[]}";
        loadGeminiUrl(url);
        try { Thread.sleep(2400); } catch (InterruptedException ignored) {}
        return evalJs(geminiWeb, JsScripts.geminiReadThread(), 8);
    }

    private String evalJs(final WebView wv, final String script, int timeoutSec) {
        if (wv == null) return "{\"ok\":false,\"err\":\"Apri SocialBridge sullo Z Flip\"}";
        final String reqId = UUID.randomUUID().toString();
        final CountDownLatch latch = new CountDownLatch(1);
        latches.put(reqId, latch);
        final String readyJs = script.replace("__REQ_ID__", reqId);
        mainHandler.post(() -> {
            try { wv.onResume(); wv.resumeTimers(); } catch (Exception ignored) {}
            wv.evaluateJavascript(readyJs, null);
        });
        try { latch.await(timeoutSec, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        latches.remove(reqId);
        String res = results.remove(reqId);
        return (res != null && !res.isEmpty()) ? res : "{\"ok\":false,\"err\":\"Timeout JS\"}";
    }

    private void serveVideoProxy(OutputStream out, String vidUrl) throws Exception {
        if (vidUrl == null || vidUrl.isEmpty()) { sendJson(out, 400, "{}"); return; }
        HttpURLConnection c = (HttpURLConnection) new URL(vidUrl).openConnection();
        c.setConnectTimeout(9000); c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
        String cookie = CookieManager.getInstance().getCookie(vidUrl);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream raw = new ByteArrayOutputStream()) {
            byte[] buf = new byte[16384]; int n;
            while ((n = is.read(buf)) != -1 && raw.size() < 9 * 1024 * 1024) raw.write(buf, 0, n);
            byte[] data = raw.toByteArray();
            String hdr = "HTTP/1.1 200 OK\r\nContent-Type: video/mp4\r\nContent-Length: " + data.length + "\r\nConnection: close\r\n\r\n";
            out.write(hdr.getBytes(StandardCharsets.UTF_8)); out.write(data); out.flush();
        } finally { c.disconnect(); }
    }

    private String fetchGoogleNewsRssFallback() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://news.google.com/rss?hl=it&gl=IT&ceid=IT:it").openConnection();
            c.setConnectTimeout(7000); c.setReadTimeout(9000);
            c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = c.getInputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            }
            String xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
            int first = xml.indexOf('<');
            if (first > 0) xml = xml.substring(first);
            XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();
            xpp.setInput(new StringReader(xml));
            JSONArray items = new JSONArray();
            String title = "", link = "";
            boolean inItem = false;
            int ev = xpp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT && items.length() < 20) {
                String tag = xpp.getName() != null ? xpp.getName().toLowerCase() : "";
                if (ev == XmlPullParser.START_TAG) {
                    if ("item".equals(tag)) { inItem = true; title = ""; link = ""; }
                    else if (inItem && "title".equals(tag)) title = Html.fromHtml(xpp.nextText(), Html.FROM_HTML_MODE_LEGACY).toString();
                    else if (inItem && "link".equals(tag)) link = xpp.nextText().trim();
                } else if (ev == XmlPullParser.END_TAG && "item".equals(tag) && inItem) {
                    inItem = false;
                    if (!title.isEmpty()) items.put(new JSONObject().put("title", title).put("url", link).put("img", "").put("src", "Google News"));
                }
                ev = xpp.next();
            }
            return new JSONObject().put("ok", true).put("items", items).toString();
        } catch (Exception e) { return "{\"ok\":true,\"items\":[]}"; }
    }

    private String fetchArticleJson(String rawUrl) {
        try {
            String cur = rawUrl, html = "";
            for (int i = 0; i < 6; i++) {
                HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(8000); c.setReadTimeout(10000);
                c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
                c.setRequestProperty("Cookie", "CONSENT=YES+cb.20260101-00-p0.it+FX+111");
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String loc = c.getHeaderField("Location"); c.disconnect();
                    if (loc == null) break;
                    cur = new URL(new URL(cur), loc).toString(); continue;
                }
                try (InputStream is = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192]; int n;
                    while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                    html = new String(out.toByteArray(), StandardCharsets.UTF_8);
                } finally { c.disconnect(); }
                break;
            }
            String cleaned = html.replaceAll("(?is)<script.*?</script>", " ").replaceAll("(?is)<style.*?</style>", " ")
                    .replaceAll("(?is)<nav.*?</nav>", " ").replaceAll("(?is)<footer.*?</footer>", " ");
            Matcher m = Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p>").matcher(cleaned);
            StringBuilder sb = new StringBuilder();
            while (m.find() && sb.length() < 4500) {
                String p = Html.fromHtml(m.group(1).replaceAll("(?is)<[^>]+>", " "), Html.FROM_HTML_MODE_LEGACY)
                        .toString().replaceAll("\\s+", " ").trim();
                if (p.length() >= 35) sb.append(p).append("\n\n");
            }
            String txt = sb.toString().trim();
            if (txt.isEmpty()) txt = "Anteprima non estraibile (articolo protetto).";
            return new JSONObject().put("ok", true).put("text", txt).toString();
        } catch (Exception e) { return "{\"ok\":false,\"text\":\"Errore apertura articolo: " + e.getMessage() + "\"}"; }
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
        while ((b = in.read()) != -1) { if (b == '\n') break; if (b != '\r') buf.write(b); }
        return (b == -1 && buf.size() == 0) ? null : new String(buf.toByteArray(), StandardCharsets.UTF_8);
    }

    private byte[] readExact(InputStream in, int len) throws Exception {
        byte[] data = new byte[len]; int off = 0;
        while (off < len) { int n = in.read(data, off, len - off); if (n < 0) break; off += n; }
        return data;
    }

    private void serveCompressedImage(OutputStream out, String imgUrl) throws Exception {
        if (imgUrl == null || imgUrl.isEmpty()) { sendJson(out, 400, "{}"); return; }
        HttpURLConnection c = (HttpURLConnection) new URL(imgUrl).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
        String cookie = CookieManager.getInstance().getCookie(imgUrl);
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        try (InputStream is = c.getInputStream(); ByteArrayOutputStream raw = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192]; int n;
            while ((n = is.read(buf)) != -1) raw.write(buf, 0, n);
            byte[] bytes = raw.toByteArray();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            int sample = 1;
            while (o.outWidth / sample > 420) sample *= 2;
            o.inJustDecodeBounds = false; o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.length, o);
            ByteArrayOutputStream jpg = new ByteArrayOutputStream();
            bmp.compress(Bitmap.CompressFormat.JPEG, 72, jpg);
            byte[] outBytes = jpg.toByteArray();
            String hdr = "HTTP/1.1 200 OK\r\nContent-Type: image/jpeg\r\nContent-Length: " + outBytes.length + "\r\nConnection: close\r\n\r\n";
            out.write(hdr.getBytes(StandardCharsets.UTF_8));
            out.write(outBytes); out.flush();
        } finally { c.disconnect(); }
    }

    private void sendJson(OutputStream out, int code, String json) throws Exception {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        String hdr = "HTTP/1.1 " + code + " OK\r\nContent-Type: application/json; charset=utf-8\r\nContent-Length: " + b.length + "\r\nConnection: close\r\n\r\n";
        out.write(hdr.getBytes(StandardCharsets.UTF_8));
        out.write(b); out.flush();
    }

    private Map<String, String> parseQuery(String full) {
        Map<String, String> map = new HashMap<>();
        int q = full.indexOf('?');
        if (q < 0) return map;
        for (String pair : full.substring(q + 1).split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                try { map.put(URLDecoder.decode(pair.substring(0, eq), "UTF-8"), URLDecoder.decode(pair.substring(eq + 1), "UTF-8")); }
                catch (Exception ignored) {}
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
