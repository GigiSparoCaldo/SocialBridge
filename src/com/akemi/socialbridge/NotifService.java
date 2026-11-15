package com.akemi.socialbridge;

import android.app.Notification;
import android.app.RemoteInput;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public class NotifService extends NotificationListenerService {
    public static class NotifItem {
        public String key, appName, pkg, title, text, time;
        public Notification.Action replyAction;
        public RemoteInput remoteInput;
    }

    private static volatile NotifService instance;
    private static final int MAX_ITEMS = 40;
    private static final LinkedHashMap<String, NotifItem> buffer = new LinkedHashMap<String, NotifItem>(MAX_ITEMS + 2, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, NotifItem> eldest) {
            return size() > MAX_ITEMS;
        }
    };

    public static volatile String sharedClip = "";
    public static volatile byte[] sharedFileBytes = null;
    public static volatile String sharedFileName = "";
    public static volatile boolean onlyImportantApps = false;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
        syncActive();
    }

    public void syncActive() {
        try {
            StatusBarNotification[] active = getActiveNotifications();
            if (active != null) {
                for (StatusBarNotification sbn : active) ingest(sbn);
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        instance = this;
        ingest(sbn);
    }

    private void ingest(StatusBarNotification sbn) {
        if (sbn == null) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return;
        if ((n.flags & Notification.FLAG_FOREGROUND_SERVICE) != 0) return;

        String pkg = sbn.getPackageName();
        if (pkg == null || pkg.equals(getPackageName()) || pkg.contains("btbridge") || "android".equals(pkg)) return;
        if (onlyImportantApps && !isPriorityApp(pkg)) return;

        Bundle ex = n.extras;
        if (ex == null) return;
        String title = String.valueOf(ex.getCharSequence(Notification.EXTRA_TITLE, "")).trim();
        CharSequence big = ex.getCharSequence(Notification.EXTRA_BIG_TEXT);
        String text = String.valueOf(big != null ? big : ex.getCharSequence(Notification.EXTRA_TEXT, "")).trim();

        Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
        if (msgs != null && msgs.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (Parcelable p : msgs) {
                if (p instanceof Bundle) {
                    Bundle mb = (Bundle) p;
                    CharSequence sender = mb.getCharSequence("sender");
                    CharSequence mText = mb.getCharSequence("text");
                    if (mText != null) {
                        if (sb.length() > 0) sb.append("\n");
                        if (sender != null && sender.length() > 0) sb.append(sender).append(": ");
                        sb.append(mText);
                    }
                }
            }
            if (sb.length() > 0) text = sb.toString();
        }
        if (title.isEmpty() && text.isEmpty()) return;

        NotifItem item = new NotifItem();
        item.key = sbn.getKey();
        item.pkg = pkg;
        item.appName = resolveAppName(pkg);
        item.title = title;
        item.text = text;
        item.time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(sbn.getPostTime()));

        if (n.actions != null) {
            for (Notification.Action act : n.actions) {
                if (act != null && act.getRemoteInputs() != null && act.getRemoteInputs().length > 0) {
                    item.replyAction = act;
                    item.remoteInput = act.getRemoteInputs()[0];
                    break;
                }
            }
        }
        synchronized (buffer) {
            buffer.remove(item.key);
            buffer.put(item.key, item);
        }
    }

    private boolean isPriorityApp(String pkg) {
        String p = pkg.toLowerCase(Locale.ROOT);
        return p.contains("messaging") || p.contains("mms") || p.contains("sms")
                || p.contains("whatsapp") || p.contains("telegram")
                || p.contains("bank") || p.contains("banca") || p.contains("intesa")
                || p.contains("unicredit") || p.contains("poste") || p.contains("revolut") || p.contains("paypal")
                || p.contains("brt") || p.contains("gls") || p.contains("dhl") || p.contains("sda")
                || p.contains("amazon") || p.contains("ups") || p.contains("fedex") || p.contains("inpost");
    }

    private String resolveAppName(String pkg) {
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception e) {
            int dot = pkg.lastIndexOf('.');
            return dot >= 0 ? pkg.substring(dot + 1) : pkg;
        }
    }

    public static String getListJson() {
        if (instance != null) instance.syncActive();
        try {
            JSONArray arr = new JSONArray();
            synchronized (buffer) {
                List<NotifItem> vals = new ArrayList<>(buffer.values());
                for (int i = vals.size() - 1; i >= 0; i--) {
                    NotifItem it = vals.get(i);
                    JSONObject o = new JSONObject();
                    o.put("key", it.key);
                    o.put("app", it.appName);
                    o.put("title", it.title);
                    o.put("text", it.text);
                    o.put("time", it.time);
                    o.put("canReply", it.replyAction != null && it.remoteInput != null);
                    arr.put(o);
                }
            }
            return new JSONObject().put("ok", true).put("items", arr).toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"items\":[]}";
        }
    }

    public static boolean sendReply(android.content.Context ctx, String key, String replyText) {
        NotifItem it;
        synchronized (buffer) { it = buffer.get(key); }
        if (it == null || it.replyAction == null || it.remoteInput == null) return false;
        try {
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            bundle.putCharSequence(it.remoteInput.getResultKey(), replyText);
            RemoteInput.addResultsToIntent(new RemoteInput[]{it.remoteInput}, intent, bundle);
            it.replyAction.actionIntent.send(ctx, 0, intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
