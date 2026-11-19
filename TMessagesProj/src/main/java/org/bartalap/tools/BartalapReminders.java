package org.bartalap.tools;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bartalap: chat snooze (archive now, bring back later with a notification) and a nightly digest.
 */
public final class BartalapReminders {

    private BartalapReminders() {}

    private static final String CHANNEL_ID = "bartalap_reminders";
    static final String KEY_DIGEST_ENABLED = "digest_enabled";
    private static final String DIGEST_WORK = "bartalap_daily_digest";

    // ------------------------------------------------------------ notifications

    private static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Bartalap রিমাইন্ডার", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("চ্যাট স্নুজ আর ডেইলি ডাইজেস্ট");
                nm.createNotificationChannel(ch);
            }
        }
    }

    static void notify(Context ctx, int id, String title, String text, long openDialogId, int account) {
        try {
            ensureChannel(ctx);
            Intent intent = new Intent(ctx, LaunchActivity.class);
            if (openDialogId != 0) {
                intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
                if (DialogObject.isUserDialog(openDialogId)) {
                    intent.putExtra("userId", openDialogId);
                } else {
                    intent.putExtra("chatId", -openDialogId);
                }
                intent.putExtra("currentAccount", account);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, id, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                    .setSmallIcon(R.drawable.notification)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setAutoCancel(true)
                    .setContentIntent(pi);
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(id, b.build());
        } catch (Throwable e) {
            FileLog.e("Bartalap notify error", e);
        }
    }

    private static boolean runOnUi(Runnable r) {
        CountDownLatch latch = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                ApplicationLoader.postInitApplication();
                r.run();
            } catch (Throwable t) {
                FileLog.e("Bartalap ui task error", t);
            } finally {
                latch.countDown();
            }
        });
        try {
            return latch.await(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    // ------------------------------------------------------------ snooze

    /** Archives the chat now and brings it back (with a notification) after delayMinutes. Call on UI thread. */
    public static void snooze(int account, long dialogId, String name, long delayMinutes) {
        try {
            MessagesController.getInstance(account).addDialogToFolder(dialogId, 1, -1, 0);
        } catch (Throwable e) {
            FileLog.e("Bartalap snooze archive error", e);
        }
        Data data = new Data.Builder()
                .putLong("dialog_id", dialogId)
                .putInt("account", account)
                .putString("name", name)
                .build();
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(SnoozeWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .setInputData(data)
                .build();
        WorkManager.getInstance(ApplicationLoader.applicationContext)
                .enqueueUniqueWork("bartalap_snooze_" + dialogId, ExistingWorkPolicy.REPLACE, req);
    }

    public static class SnoozeWorker extends Worker {
        public SnoozeWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            long dialogId = getInputData().getLong("dialog_id", 0);
            int account = getInputData().getInt("account", 0);
            String name = getInputData().getString("name");
            if (dialogId == 0) return Result.success();
            runOnUi(() -> {
                if (UserConfig.getInstance(account).isClientActivated()) {
                    MessagesController.getInstance(account).addDialogToFolder(dialogId, 0, -1, 0);
                }
            });
            notify(getApplicationContext(), (int) (dialogId % 100000) + 5000, "⏰ স্নুজ শেষ",
                    (name == null || name.isEmpty() ? "একটা চ্যাট" : name) + " এর চ্যাট আবার চ্যাট লিস্টে ফিরে এসেছে। এখন উত্তর দিন।",
                    dialogId, account);
            return Result.success();
        }
    }

    // ------------------------------------------------------------ daily digest

    public static boolean isDigestEnabled() {
        return AiClient.prefs().getBoolean(KEY_DIGEST_ENABLED, false);
    }

    public static void setDigestEnabled(boolean enabled) {
        AiClient.prefs().edit().putBoolean(KEY_DIGEST_ENABLED, enabled).apply();
        WorkManager wm = WorkManager.getInstance(ApplicationLoader.applicationContext);
        if (!enabled) {
            wm.cancelUniqueWork(DIGEST_WORK);
            return;
        }
        Calendar now = Calendar.getInstance();
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, 21);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        if (!next.after(now)) next.add(Calendar.DAY_OF_YEAR, 1);
        long delay = next.getTimeInMillis() - now.getTimeInMillis();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(DigestWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        wm.enqueueUniquePeriodicWork(DIGEST_WORK, ExistingPeriodicWorkPolicy.UPDATE, req);
    }

    /** Builds the digest text from the loaded dialog list. Call on UI thread. */
    public static String buildDigest(int account) {
        MessagesController mc = MessagesController.getInstance(account);
        long self = UserConfig.getInstance(account).getClientUserId();
        int unreadChats = 0, unreadMessages = 0, unanswered = 0;
        ArrayList<String> top = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null) continue;
            if (d.unread_count > 0) {
                unreadChats++;
                unreadMessages += d.unread_count;
            }
            if (DialogObject.isUserDialog(d.id) && d.id != self && d.id != 777000L) {
                TLRPC.User u = mc.getUser(d.id);
                if (u == null || u.bot) continue;
                ArrayList<MessageObject> msgs = mc.dialogMessage.get(d.id);
                if (msgs != null && !msgs.isEmpty() && msgs.get(0) != null && !msgs.get(0).isOut()) {
                    unanswered++;
                    if (top.size() < 5) top.add(u.first_name == null ? "" : u.first_name);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("📬 ").append(unreadChats).append(" টি চ্যাটে ").append(unreadMessages).append(" টি না-পড়া মেসেজ\n");
        sb.append("💬 ").append(unanswered).append(" জনের উত্তর বাকি");
        if (!top.isEmpty()) sb.append(": ").append(android.text.TextUtils.join(", ", top));
        return sb.toString();
    }

    public static class DigestWorker extends Worker {
        public DigestWorker(@NonNull Context context, @NonNull WorkerParameters params) {
            super(context, params);
        }

        @NonNull
        @Override
        public Result doWork() {
            if (!isDigestEnabled()) return Result.success();
            final String[] text = {null};
            final int account = UserConfig.selectedAccount;
            runOnUi(() -> {
                if (UserConfig.getInstance(account).isClientActivated()) text[0] = buildDigest(account);
            });
            if (text[0] != null) {
                notify(getApplicationContext(), 4242, "🌙 Bartalap আজকের সারাংশ", text[0], 0, account);
            }
            return Result.success();
        }
    }
}
