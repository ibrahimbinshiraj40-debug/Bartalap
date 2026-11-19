package org.bartalap.tools;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bartalap: every 30 minutes searches all chats/channels for the user's saved keywords
 * ("ChatGPT Plus", "Canva" …) and shows a notification for new posts.
 */
public class KeywordAlertWorker extends Worker {

    static final String KEY_ENABLED = "keyword_alert_enabled";
    private static final String KEY_LAST_DATE = "keyword_alert_last_date";
    private static final String WORK = "bartalap_keyword_alert";

    public KeywordAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static SharedPreferences prefs() {
        return AiClient.prefs();
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_ENABLED, enabled)
                .putInt(KEY_LAST_DATE, (int) (System.currentTimeMillis() / 1000L)).apply();
        WorkManager wm = WorkManager.getInstance(ApplicationLoader.applicationContext);
        if (!enabled) {
            wm.cancelUniqueWork(WORK);
            return;
        }
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(KeywordAlertWorker.class, 30, TimeUnit.MINUTES)
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build();
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!isEnabled()) return Result.success();
        JSONArray keywords;
        try {
            keywords = new JSONArray(prefs().getString("saved_keywords", "[]"));
        } catch (Exception e) {
            return Result.success();
        }
        if (keywords.length() == 0) return Result.success();
        final int account = UserConfig.selectedAccount;
        final int since = prefs().getInt(KEY_LAST_DATE, (int) (System.currentTimeMillis() / 1000L) - 3600);
        int newest = since;
        int shown = 0;
        for (int i = 0; i < Math.min(keywords.length(), 6); i++) {
            final String k = keywords.optString(i, "").trim();
            if (k.isEmpty()) continue;
            final TLRPC.messages_Messages[] holder = new TLRPC.messages_Messages[1];
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ApplicationLoader.postInitApplication();
                    if (!UserConfig.getInstance(account).isClientActivated()) {
                        latch.countDown();
                        return;
                    }
                    TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
                    req.q = k;
                    req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                    req.limit = 10;
                    req.min_date = since + 1;
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                    ConnectionsManager.getInstance(account).sendRequestTyped(req, (res, err) -> {
                        holder[0] = res;
                        latch.countDown();
                    });
                } catch (Throwable t) {
                    FileLog.e("Bartalap keyword alert error", t);
                    latch.countDown();
                }
            });
            try {
                latch.await(25, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            TLRPC.messages_Messages res = holder[0];
            if (res == null || res.messages == null) continue;
            for (TLRPC.Message m : res.messages) {
                if (m == null || m.date <= since || m.out) continue;
                newest = Math.max(newest, m.date);
                if (shown >= 3) continue;
                final TLRPC.messages_Messages r = res;
                final long did = MessageObject.getPeerId(m.peer_id);
                final String[] chatName = {""};
                CountDownLatch nameLatch = new CountDownLatch(1);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        MessagesController mc = MessagesController.getInstance(account);
                        mc.putUsers(r.users, false);
                        mc.putChats(r.chats, false);
                        if (did < 0) {
                            TLRPC.Chat c = mc.getChat(-did);
                            if (c != null) chatName[0] = c.title;
                        } else {
                            TLRPC.User u = mc.getUser(did);
                            if (u != null) chatName[0] = u.first_name;
                        }
                    } catch (Throwable ignored) {
                    } finally {
                        nameLatch.countDown();
                    }
                });
                try {
                    nameLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                String text = m.message == null ? "" : m.message.replace('\n', ' ');
                if (text.length() > 160) text = text.substring(0, 160) + "…";
                BartalapReminders.showNotification(getApplicationContext(), 7000 + shown,
                        "🔔 \"" + k + "\" পাওয়া গেছে" + (chatName[0] == null || chatName[0].isEmpty() ? "" : ": " + chatName[0]),
                        text, did, account);
                shown++;
            }
        }
        prefs().edit().putInt(KEY_LAST_DATE, Math.max(newest, (int) (System.currentTimeMillis() / 1000L) - 60)).apply();
        return Result.success();
    }
}
