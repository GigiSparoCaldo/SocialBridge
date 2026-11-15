package com.akemi.socialbridge;

import android.text.Html;
import android.webkit.CookieManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

public class NativeSocialHttp {
    private static volatile String cachedModhash = "";

    public static String redditGet(String pathAndQuery) {
        try {
            String urlStr = pathAndQuery.startsWith("http") ? pathAndQuery : "https://old.reddit.com" + pathAndQuery;
            HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(7000);
            c.setReadTimeout(10000);
            c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
            c.setRequestProperty("Accept", "application/json");
            String ck = CookieManager.getInstance().getCookie("https://old.reddit.com");
            if (ck == null || ck.isEmpty()) ck = CookieManager.getInstance().getCookie("https://www.reddit.com");
            if (ck != null && !ck.isEmpty()) c.setRequestProperty("Cookie", ck);
            try (InputStream is = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } finally { c.disconnect(); }
        } catch (Exception e) {
            return "{\"data\":{\"children\":[]},\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    public static String redditFeed(String sub, String sort, String after) {
        String s = sub == null ? "" : sub.trim();
        String base = s.isEmpty() ? "/.json?limit=20&raw_json=1"
                : ("day".equals(sort) || "week".equals(sort) || "month".equals(sort))
                ? "/r/" + s + "/top.json?t=" + sort + "&limit=20&raw_json=1"
                : "new".equals(sort) ? "/r/" + s + "/new.json?limit=20&raw_json=1"
                : "/r/" + s + "/hot.json?limit=20&raw_json=1";
        if (after != null && !after.isEmpty()) base += "&after=" + after;
        return redditGet(base);
    }

    public static String redditComments(String permalink) {
        String clean = permalink.replaceAll("^https?://[^/]+", "").split("\\?")[0];
        if (!clean.startsWith("/")) clean = "/" + clean;
        if (clean.endsWith("/")) clean = clean.substring(0, clean.length() - 1);
        return redditGet(clean + ".json?limit=35&depth=4&raw_json=1");
    }

    public static String redditUser(String username) {
        try {
            String enc = URLEncoder.encode(username, "UTF-8");
            JSONObject d = new JSONObject(redditGet("/user/" + enc + "/about.json")).optJSONObject("data");
            JSONArray items = new JSONObject(redditGet("/user/" + enc + "/submitted.json?limit=20&raw_json=1"))
                    .optJSONObject("data").optJSONArray("children");
            JSONObject out = new JSONObject().put("ok", true).put("user", username);
            if (d != null) {
                out.put("karma", d.optInt("total_karma", d.optInt("link_karma", 0) + d.optInt("comment_karma", 0)));
                JSONObject sr = d.optJSONObject("subreddit");
                out.put("bio", sr != null ? sr.optString("public_description", "") : "");
            }
            out.put("items", items != null ? items : new JSONArray());
            return out.toString();
        } catch (Exception e) { return "{\"ok\":false,\"items\":[]}"; }
    }

    public static String redditSubInfo(String sub) {
        try {
            String enc = URLEncoder.encode(sub, "UTF-8");
            JSONObject d = new JSONObject(redditGet("/r/" + enc + "/about.json")).optJSONObject("data");
            if (d == null) return "{\"ok\":false}";
            return new JSONObject().put("ok", true)
                    .put("sub", d.optString("display_name", sub))
                    .put("desc", d.optString("public_description", ""))
                    .put("subs", d.optInt("subscribers", 0))
                    .put("subscribed", d.optBoolean("user_is_subscriber", false)).toString();
        } catch (Exception e) { return "{\"ok\":false}"; }
    }

    public static String redditSearch(String q) {
        try {
            String enc = URLEncoder.encode(q, "UTF-8");
            JSONArray srCh = new JSONObject(redditGet("/subreddits/search.json?q=" + enc + "&limit=8")).optJSONObject("data").optJSONArray("children");
            JSONArray subs = new JSONArray();
            if (srCh != null) {
                for (int i = 0; i < srCh.length(); i++) {
                    JSONObject d = srCh.optJSONObject(i).optJSONObject("data");
                    if (d != null) subs.put(new JSONObject().put("sub", d.optString("display_name", "")).put("subs", d.optInt("subscribers", 0)));
                }
            }
            JSONArray posts = new JSONObject(redditGet("/search.json?q=" + enc + "&limit=18&raw_json=1")).optJSONObject("data").optJSONArray("children");
            return new JSONObject().put("ok", true).put("subs", subs).put("posts", posts != null ? posts : new JSONArray()).toString();
        } catch (Exception e) { return "{\"ok\":false}"; }
    }

    public static String redditSaved() {
        try {
            JSONObject me = new JSONObject(redditGet("/api/me.json")).optJSONObject("data");
            String name = me != null ? me.optString("name", "") : "";
            if (name.isEmpty()) return "{\"data\":{\"children\":[]}}";
            cachedModhash = me.optString("modhash", cachedModhash);
            return redditGet("/user/" + URLEncoder.encode(name, "UTF-8") + "/saved.json?limit=25&raw_json=1");
        } catch (Exception e) { return "{\"data\":{\"children\":[]}}"; }
    }

    public static String redditPost(String endpoint, String formParams) {
        try {
            if (cachedModhash.isEmpty()) {
                JSONObject me = new JSONObject(redditGet("/api/me.json")).optJSONObject("data");
                if (me != null) cachedModhash = me.optString("modhash", "");
            }
            HttpURLConnection c = (HttpURLConnection) new URL("https://old.reddit.com" + endpoint).openConnection();
            c.setRequestMethod("POST"); c.setDoOutput(true);
            c.setConnectTimeout(7000); c.setReadTimeout(9000);
            c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
            c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            if (!cachedModhash.isEmpty()) c.setRequestProperty("X-Modhash", cachedModhash);
            String ck = CookieManager.getInstance().getCookie("https://old.reddit.com");
            if (ck == null || ck.isEmpty()) ck = CookieManager.getInstance().getCookie("https://www.reddit.com");
            if (ck != null) c.setRequestProperty("Cookie", ck);
            String body = formParams + "&uh=" + URLEncoder.encode(cachedModhash, "UTF-8");
            try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
            int code = c.getResponseCode(); c.disconnect();
            return "{\"ok\":" + (code >= 200 && code < 300) + "}";
        } catch (Exception e) { return "{\"ok\":false}"; }
    }

    public static String googleNewsRss() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("https://news.google.com/rss?hl=it&gl=IT&ceid=IT:it").openConnection();
            c.setConnectTimeout(6000); c.setReadTimeout(8000);
            c.setRequestProperty("User-Agent", MainActivity.MOBILE_UA);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (InputStream is = c.getInputStream()) {
                byte[] buf = new byte[8192]; int n;
                while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
            }
            String xml = new String(out.toByteArray(), StandardCharsets.UTF_8);
            int first = xml.indexOf('<'); if (first > 0) xml = xml.substring(first);
            XmlPullParser xpp = XmlPullParserFactory.newInstance().newPullParser();
            xpp.setInput(new StringReader(xml));
            JSONArray items = new JSONArray();
            String title = "", link = ""; boolean inItem = false;
            int ev = xpp.getEventType();
            while (ev != XmlPullParser.END_DOCUMENT && items.length() < 22) {
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

    public static String fetchArticleJson(String rawUrl) {
        try {
            String cur = rawUrl, html = "";
            for (int i = 0; i < 6; i++) {
                HttpURLConnection c = (HttpURLConnection) new URL(cur).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(7000); c.setReadTimeout(9000);
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
        } catch (Exception e) { return "{\"ok\":false,\"text\":\"Errore: " + e.getMessage() + "\"}"; }
    }
}
