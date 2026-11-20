package org.bartalap.tools;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/**
 * Bartalap Unified Inbox: copies incoming WhatsApp / Messenger / Instagram / IMO message notifications
 * into Bartalap (like WAMR). Keeps messages even if the sender deletes them, and can reply through the
 * notification's own reply button. Everything stays on the phone.
 */
public class BartalapNotificationListener extends NotificationListenerService {

    public static final String[][] APPS = {
            {"com.whatsapp", "WhatsApp"},
            {"com.whatsapp.w4b", "WA Business"},
            {"com.facebook.orca", "Messenger"},
            {"com.facebook.mlite", "Messenger Lite"},
            {"com.instagram.android", "Instagram"},
            {"com.imo.android.imoim", "IMO"},
            {"org.thoughtcrime.securesms", "Signal"},
            {"com.viber.voip", "Viber"}
    };

    private static final int MAX_ITEMS = 1500;
    private static final Object LOCK = new Object();
    /** Latest reply action per conversation (only while the service is alive). */
    private static final HashMap<String, Notification.Action> REPLY_ACTIONS = new HashMap<>();

    public static String appName(String pkg) {
        for (String[] a : APPS) if (a[0].equals(pkg)) return a[1];
        return pkg;
    }

    private static boolean watched(String pkg) {
        for (String[] a : APPS) if (a[0].equals(pkg)) return true;
        return false;
    }

    public static boolean isPermissionGranted(Context ctx) {
        String flat = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        if (flat == null) return false;
        ComponentName me = new ComponentName(ctx, BartalapNotificationListener.class);
        return flat.contains(me.flattenToString()) || flat.contains(me.flattenToShortString());
    }

    public static Intent permissionIntent() {
        return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
    }

    // ------------------------------------------------------------ storage

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "bartalap_unified_inbox.json");
    }

    public static JSONArray load(Context ctx) {
        synchronized (LOCK) {
            File f = file(ctx);
            if (!f.exists()) return new JSONArray();
            try (FileInputStream in = new FileInputStream(f)) {
                byte[] b = new byte[(int) f.length()];
                int off = 0;
                while (off < b.length) {
                    int r = in.read(b, off, b.length - off);
                    if (r < 0) break;
                    off += r;
                }
                return new JSONArray(new String(b, 0, off, StandardCharsets.UTF_8));
            } catch (Exception e) {
                return new JSONArray();
            }
        }
    }

    private static void save(Context ctx, JSONArray arr) {
        synchronized (LOCK) {
            try (FileOutputStream out = new FileOutputStream(file(ctx))) {
                out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            } catch (Exception ignored) {
            }
        }
    }

    public static void clearAll(Context ctx) {
        save(ctx, new JSONArray());
    }

    public static String convKey(String pkg, String chat) {
        return pkg + "|" + chat;
    }

    private static boolean isDeletedText(String t) {
        String s = t.toLowerCase();
        return s.contains("this message was deleted") || s.contains("message was deleted")
                || s.contains("মুছে ফেলা হয়েছে") || s.contains("unsent a message") || s.contains("removed a message");
    }

    private static boolean isSummaryText(String t) {
        String s = t.toLowerCase();
        return s.matches("^\\d+ new messages?.*") || s.matches(".*\\d+ messages? from \\d+ chats?.*")
                || s.equals("checking for new messages") || s.contains("টি নতুন মেসেজ");
    }

    // ------------------------------------------------------------ capture

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null || !watched(sbn.getPackageName())) return;
            Notification n = sbn.getNotification();
            if (n == null || (n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
            Bundle ex = n.extras;
            if (ex == null) return;
            String pkg = sbn.getPackageName();
            String chat = str(ex.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE));
            String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            if (chat.isEmpty()) chat = title;
            if (chat.isEmpty()) return;

            // Remember the reply button so the user can answer from Bartalap
            if (n.actions != null) {
                for (Notification.Action a : n.actions) {
                    if (a != null && a.getRemoteInputs() != null && a.getRemoteInputs().length > 0) {
                        synchronized (REPLY_ACTIONS) {
                            REPLY_ACTIONS.put(convKey(pkg, chat), a);
                        }
                        break;
                    }
                }
            }

            JSONArray arr = load(this);
            Parcelable[] msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES);
            boolean changed = false;
            if (msgs != null && msgs.length > 0) {
                // MessagingStyle: only the newest message is new
                Parcelable p = msgs[msgs.length - 1];
                if (p instanceof Bundle) {
                    Bundle mb = (Bundle) p;
                    String text = str(mb.getCharSequence("text"));
                    String sender = str(mb.getCharSequence("sender"));
                    long time = mb.getLong("time", System.currentTimeMillis());
                    changed = add(arr, pkg, chat, sender.isEmpty() ? title : sender, text, time);
                }
            } else {
                String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
                changed = add(arr, pkg, chat, title, text, sbn.getPostTime());
            }
            if (changed) save(this, arr);
        } catch (Throwable ignored) {
        }
    }

    private static boolean add(JSONArray arr, String pkg, String chat, String sender, String text, long time) throws Exception {
        if (text.isEmpty() || isSummaryText(text)) return false;
        // Duplicate check (same conversation + same text in the last items)
        for (int i = arr.length() - 1; i >= Math.max(0, arr.length() - 40); i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && pkg.equals(o.optString("pkg")) && chat.equals(o.optString("chat")) && text.equals(o.optString("text"))) {
                return false;
            }
        }
        if (isDeletedText(text)) {
            // Mark the sender's last message as deleted, keep the original text
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && pkg.equals(o.optString("pkg")) && chat.equals(o.optString("chat"))
                        && !o.optBoolean("deleted") && sender.equals(o.optString("sender"))) {
                    o.put("deleted", true);
                    return true;
                }
            }
            return false;
        }
        JSONObject o = new JSONObject();
        o.put("pkg", pkg);
        o.put("chat", chat);
        o.put("sender", sender);
        o.put("text", text);
        o.put("time", time);
        arr.put(o);
        while (arr.length() > MAX_ITEMS) arr.remove(0);
        return true;
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    // ------------------------------------------------------------ reply

    public static boolean canReply(String pkg, String chat) {
        synchronized (REPLY_ACTIONS) {
            return REPLY_ACTIONS.containsKey(convKey(pkg, chat));
        }
    }

    /** Sends a reply through the original app's notification reply button. */
    public static boolean reply(Context ctx, String pkg, String chat, String text) {
        Notification.Action a;
        synchronized (REPLY_ACTIONS) {
            a = REPLY_ACTIONS.get(convKey(pkg, chat));
        }
        if (a == null || a.actionIntent == null || TextUtils.isEmpty(text)) return false;
        try {
            RemoteInput[] inputs = a.getRemoteInputs();
            Intent intent = new Intent();
            Bundle results = new Bundle();
            for (RemoteInput ri : inputs) results.putCharSequence(ri.getResultKey(), text);
            RemoteInput.addResultsToIntent(inputs, intent, results);
            a.actionIntent.send(ctx, 0, intent);
            JSONArray arr = load(ctx);
            JSONObject o = new JSONObject();
            o.put("pkg", pkg);
            o.put("chat", chat);
            o.put("sender", "আমি");
            o.put("text", text);
            o.put("time", System.currentTimeMillis());
            o.put("out", true);
            arr.put(o);
            save(ctx, arr);
            synchronized (REPLY_ACTIONS) {
                REPLY_ACTIONS.remove(convKey(pkg, chat));
            }
            return true;
        } catch (PendingIntent.CanceledException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
