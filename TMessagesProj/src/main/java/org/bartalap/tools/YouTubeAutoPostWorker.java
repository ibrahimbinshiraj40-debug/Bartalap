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

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bartalap: checks the user's YouTube channel every 15 minutes and posts each new video
 * to the Telegram channel/group they picked.
 */
public class YouTubeAutoPostWorker extends Worker {

    private static final String WORK_NAME = "bartalap_youtube_autopost";

    static final String KEY_ENABLED = "yt_enabled";
    static final String KEY_CHANNEL_ID = "yt_channel_id";
    static final String KEY_TARGET_DIALOG = "yt_target_dialog";
    static final String KEY_TARGET_NAME = "yt_target_name";
    static final String KEY_TARGET_ACCOUNT = "yt_target_account";
    static final String KEY_TEMPLATE = "yt_template";
    static final String KEY_LAST_VIDEO = "yt_last_video";
    static final String KEY_LAST_CHECK = "yt_last_check";
    static final String KEY_LAST_POSTED_TITLE = "yt_last_posted_title";

    public YouTubeAutoPostWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bartalap_tools", Context.MODE_PRIVATE);
    }

    public static void schedule(Context context) {
        try {
            Constraints constraints = new Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                    YouTubeAutoPostWorker.class, 15, TimeUnit.MINUTES)
                    .setConstraints(constraints)
                    .build();
            WorkManager.getInstance(context.getApplicationContext())
                    .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request);
        } catch (Throwable e) {
            FileLog.e("Bartalap YouTube schedule error", e);
        }
    }

    public static void cancel(Context context) {
        try {
            WorkManager.getInstance(context.getApplicationContext()).cancelUniqueWork(WORK_NAME);
        } catch (Throwable e) {
            FileLog.e("Bartalap YouTube cancel error", e);
        }
    }

    /** Result codes for {@link #checkNow(boolean)}. */
    public static final int RESULT_NOT_SET_UP = 0;
    public static final int RESULT_NO_NEW = 1;
    public static final int RESULT_POSTED = 2;
    public static final int RESULT_ERROR = 3;

    /**
     * Checks the feed once. Must be called off the UI thread (does network).
     * When forcePostLatest is true the latest video is posted even if it was seen before (test button).
     */
    public static int checkNow(boolean forcePostLatest) {
        SharedPreferences p = prefs();
        String channelId = p.getString(KEY_CHANNEL_ID, "");
        long target = p.getLong(KEY_TARGET_DIALOG, 0);
        int account = p.getInt(KEY_TARGET_ACCOUNT, UserConfig.selectedAccount);
        if (channelId.isEmpty() || target == 0) return RESULT_NOT_SET_UP;
        try {
            YouTubeFeed.Video v = YouTubeFeed.fetchLatest(channelId);
            p.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply();
            if (v == null) return RESULT_NO_NEW;
            String last = p.getString(KEY_LAST_VIDEO, "");
            if (!forcePostLatest && v.id.equals(last)) return RESULT_NO_NEW;
            if (!forcePostLatest && last.isEmpty()) {
                // First run: remember the current latest video, only post videos uploaded after setup.
                p.edit().putString(KEY_LAST_VIDEO, v.id).apply();
                return RESULT_NO_NEW;
            }
            String text = YouTubeFeed.fillTemplate(p.getString(KEY_TEMPLATE, YouTubeFeed.DEFAULT_TEMPLATE), v);
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ApplicationLoader.postInitApplication();
                    if (UserConfig.getInstance(account).isClientActivated()) {
                        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, target));
                    }
                } catch (Throwable t) {
                    FileLog.e("Bartalap YouTube post error", t);
                } finally {
                    latch.countDown();
                }
            });
            latch.await(15, TimeUnit.SECONDS);
            p.edit().putString(KEY_LAST_VIDEO, v.id).putString(KEY_LAST_POSTED_TITLE, v.title).apply();
            return RESULT_POSTED;
        } catch (Throwable e) {
            FileLog.e("Bartalap YouTube check error", e);
            return RESULT_ERROR;
        }
    }

    @NonNull
    @Override
    public Result doWork() {
        if (!prefs().getBoolean(KEY_ENABLED, false)) return Result.success();
        int r = checkNow(false);
        if (r == RESULT_POSTED) {
            // Give the network a moment to deliver the message before the process may be frozen.
            try {
                Thread.sleep(8000);
            } catch (InterruptedException ignored) {
            }
        }
        return Result.success();
    }
}
