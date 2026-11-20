package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.PowerManager;

import org.bartalap.tools.AiClient;
import org.bartalap.tools.VoiceNoteMaker;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bartalap AI Auto-Reply: when switched on, Claude (or another AI with the user's own key) answers
 * private messages for the user — reading the chat history and the user's saved profile/style first.
 * Replies are sent as text or (sometimes) as a voice message, without making the user appear online.
 */
public final class BartalapAiAutoReply {

    private BartalapAiAutoReply() {}

    public static final String KEY_ENABLED = "ai_auto_enabled";
    public static final String KEY_ONLY_OFFLINE = "ai_auto_only_offline";
    public static final String KEY_ONLY_CONTACTS = "ai_auto_only_contacts";
    public static final String KEY_VOICE_PERCENT = "ai_auto_voice_percent";
    public static final String KEY_PROFILE = "ai_auto_profile";
    public static final String KEY_STYLE = "ai_auto_style";
    public static final String KEY_EXCLUDED = "ai_auto_excluded";
    public static final String KEY_LOG = "ai_auto_log";
    public static final String KEY_MAX_PER_HOUR = "ai_auto_max_per_hour";
    public static final String KEY_FAST = "ai_auto_fast";

    public static boolean isFast() {
        return prefs().getBoolean(KEY_FAST, true);
    }

    private static final Random RANDOM = new Random();
    private static final ConcurrentHashMap<Long, Long> pendingToken = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ArrayList<Long>> sentTimes = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Long> lastAiSendAt = new ConcurrentHashMap<>();
    private static PowerManager.WakeLock wakeLock;

    public static SharedPreferences prefs() {
        return AiClient.prefs();
    }

    public static boolean isEnabled() {
        return prefs().getBoolean(KEY_ENABLED, false);
    }

    public static void setEnabled(boolean on) {
        prefs().edit().putBoolean(KEY_ENABLED, on).apply();
    }

    public static boolean isExcluded(long dialogId) {
        try {
            JSONArray a = new JSONArray(prefs().getString(KEY_EXCLUDED, "[]"));
            for (int i = 0; i < a.length(); i++) if (a.optLong(i) == dialogId) return true;
        } catch (Exception ignored) {
        }
        return false;
    }

    public static void setExcluded(long dialogId, boolean excluded) {
        try {
            JSONArray a = new JSONArray(prefs().getString(KEY_EXCLUDED, "[]"));
            JSONArray out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (a.optLong(i) != dialogId) out.put(a.optLong(i));
            if (excluded) out.put(dialogId);
            prefs().edit().putString(KEY_EXCLUDED, out.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void log(String chat, String incoming, String reply, boolean voice) {
        try {
            JSONArray a = new JSONArray(prefs().getString(KEY_LOG, "[]"));
            JSONObject o = new JSONObject();
            o.put("chat", chat);
            o.put("in", incoming);
            o.put("out", reply);
            o.put("voice", voice);
            o.put("time", System.currentTimeMillis());
            a.put(o);
            while (a.length() > 200) a.remove(0);
            prefs().edit().putString(KEY_LOG, a.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static void holdWake(long ms) {
        try {
            if (wakeLock == null) {
                PowerManager pm = (PowerManager) ApplicationLoader.applicationContext.getSystemService(Context.POWER_SERVICE);
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Bartalap:AiAutoReply");
                wakeLock.setReferenceCounted(false);
            }
            wakeLock.acquire(ms);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Called for every incoming message. Returns true when the AI auto-reply took responsibility for it
     * (so the classic fixed-text auto-answer is skipped).
     */
    public static boolean handle(MessageObject msg, int account) {
        if (!isEnabled() || !AiClient.isConfigured()) return false;
        try {
            if (msg == null || msg.messageOwner == null || msg.isOut() || msg.messageOwner.action != null) return true;
            if (msg.isReactionPush || msg.isStoryPush || msg.isStoryReactionPush || msg.isStoryMentionPush || msg.isEdited()) return true;
            long dialogId = msg.getDialogId();
            if (!DialogObject.isUserDialog(dialogId) || dialogId == 777000L) return true;
            if (dialogId == UserConfig.getInstance(account).getClientUserId()) return true;
            if (isExcluded(dialogId)) return true;
            int now = (int) (System.currentTimeMillis() / 1000L);
            if (msg.messageOwner.date > 0 && now - msg.messageOwner.date > 3 * 3600) return true;
            boolean appVisible = !ApplicationLoader.mainInterfacePaused && ApplicationLoader.isScreenOn && account == UserConfig.selectedAccount;
            if (prefs().getBoolean(KEY_ONLY_OFFLINE, true) && appVisible) return true;
            TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
            if (u != null && (u.bot || u.self)) return true;
            if (prefs().getBoolean(KEY_ONLY_CONTACTS, false) && (u == null || !u.contact)) return true;

            // Debounce: wait for the person to finish typing (several messages in a row → one reply)
            long token = System.nanoTime();
            pendingToken.put(dialogId, token);
            // Fast mode: answer almost instantly (2s wait only to group messages sent in a burst)
            long delay = isFast() ? 2_000L : 12_000L + RANDOM.nextInt(18_000);
            holdWake(delay + 90_000L);
            ConnectionsManager.getInstance(account).keepNetworkAliveInBackground();
            Utilities.globalQueue.postRunnable(() -> {
                Long cur = pendingToken.get(dialogId);
                if (cur == null || cur != token) return;
                pendingToken.remove(dialogId);
                new Thread(() -> process(account, dialogId), "BartalapAI").start();
            }, delay);
        } catch (Throwable e) {
            FileLog.e("Bartalap AI handle error", e);
        }
        return true;
    }

    private static boolean rateOk(long dialogId) {
        int max = prefs().getInt(KEY_MAX_PER_HOUR, 8);
        ArrayList<Long> list = sentTimes.get(dialogId);
        if (list == null) {
            list = new ArrayList<>();
            sentTimes.put(dialogId, list);
        }
        long cutoff = System.currentTimeMillis() - 3600_000L;
        synchronized (list) {
            for (int i = list.size() - 1; i >= 0; i--) if (list.get(i) < cutoff) list.remove(i);
            return list.size() < max;
        }
    }

    private static void markSent(long dialogId) {
        ArrayList<Long> list = sentTimes.get(dialogId);
        if (list == null) {
            list = new ArrayList<>();
            sentTimes.put(dialogId, list);
        }
        synchronized (list) {
            list.add(System.currentTimeMillis());
        }
        lastAiSendAt.put(dialogId, System.currentTimeMillis());
    }

    private static String describe(TLRPC.Message m) {
        String t = m.message == null ? "" : m.message.trim();
        if (m.media != null && !(m.media instanceof TLRPC.TL_messageMediaEmpty) && !(m.media instanceof TLRPC.TL_messageMediaWebPage)) {
            String kind = "[মিডিয়া]";
            if (m.media instanceof TLRPC.TL_messageMediaPhoto) kind = "[ছবি]";
            else if (m.media.document != null) {
                String mime = m.media.document.mime_type == null ? "" : m.media.document.mime_type;
                if (mime.startsWith("audio/ogg")) kind = "[ভয়েস মেসেজ]";
                else if (mime.startsWith("video")) kind = "[ভিডিও]";
                else if (mime.contains("webp") || mime.contains("tgsticker")) kind = "[স্টিকার]";
                else kind = "[ফাইল]";
            }
            t = kind + (t.isEmpty() ? "" : " " + t);
        }
        if (t.length() > 600) t = t.substring(0, 600) + "…";
        return t;
    }

    private static void process(int account, long dialogId) {
        try {
            if (!isEnabled() || !rateOk(dialogId)) return;
            // 1) Load recent history of this chat
            final TLRPC.messages_Messages[] holder = new TLRPC.messages_Messages[1];
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                    req.peer = MessagesController.getInstance(account).getInputPeer(dialogId);
                    req.limit = isFast() ? 25 : 40;
                    ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                        if (response instanceof TLRPC.messages_Messages) holder[0] = (TLRPC.messages_Messages) response;
                        latch.countDown();
                    });
                } catch (Throwable t) {
                    latch.countDown();
                }
            });
            latch.await(25, TimeUnit.SECONDS);
            TLRPC.messages_Messages res = holder[0];
            if (res == null || res.messages == null || res.messages.isEmpty()) return;
            TLRPC.Message newest = res.messages.get(0);
            if (newest.out) return; // already answered (by the user or AI)
            // If the user himself wrote in this chat in the last 10 minutes, he is handling it.
            Long lastAi = lastAiSendAt.get(dialogId);
            long nowSec = System.currentTimeMillis() / 1000L;
            for (TLRPC.Message m : res.messages) {
                if (m.out && nowSec - m.date < 600) {
                    if (lastAi == null || m.date * 1000L > lastAi + 5000) return;
                }
            }
            TLRPC.User other = MessagesController.getInstance(account).getUser(dialogId);
            if (other == null) {
                for (TLRPC.User uu : res.users) if (uu.id == dialogId) other = uu;
            }
            String otherName = other == null ? "সে" : (other.first_name == null ? "সে" : other.first_name);
            TLRPC.User me = UserConfig.getInstance(account).getCurrentUser();
            String myName = me == null ? "আমি" : ContactsController.formatName(me.first_name, me.last_name);

            StringBuilder chat = new StringBuilder();
            String lastIncoming = "";
            for (int i = res.messages.size() - 1; i >= 0; i--) {
                TLRPC.Message m = res.messages.get(i);
                String d = describe(m);
                if (d.isEmpty()) continue;
                chat.append(m.out ? myName + " (আমি)" : otherName).append(": ").append(d).append('\n');
                if (!m.out) lastIncoming = d;
            }

            String profile = prefs().getString(KEY_PROFILE, "");
            String style = prefs().getString(KEY_STYLE, "");
            String system = "You are writing Telegram replies on behalf of " + myName + " (the account owner) while he is away. "
                    + "Write exactly like him: same language mix (Bengali / Banglish / English as the other person uses), same tone, short and natural like a real person texting a friend. "
                    + "Usually 1-2 short sentences, no lists, no formal greetings every time, emoji only if he uses them. "
                    + "Use the chat history and his notes to answer correctly.\n"
                    + "RULES:\n"
                    + "- Never promise payments, discounts, deliveries, dates or anything about money that is not clearly in his notes; say he'll confirm a bit later.\n"
                    + "- Never say a payment was received or confirm a TrxID. Never share passwords, codes, OTPs, phone numbers, addresses or private details.\n"
                    + "- For serious, sad, medical, legal, angry or very personal topics: reply briefly and kindly and say he will talk properly a little later.\n"
                    + "- Do not volunteer that this is automated. But if the person sincerely asks whether they are talking to a bot/AI/auto-reply, do not lie: say he is busy right now, this is his auto reply, and he will reply himself soon.\n"
                    + "- If no reply is needed (e.g. the last message is just 'ok', a sticker, thanks, or a goodbye), output exactly: SKIP\n"
                    + "- Output ONLY the reply text.\n\n"
                    + "ABOUT " + myName + " (his own notes):\n" + (profile.isEmpty() ? "(none)" : profile) + "\n\n"
                    + "HIS WRITING STYLE:\n" + (style.isEmpty() ? "(learn from the history below)" : style);
            String prompt = "Chat with " + otherName + " (oldest first):\n" + chat + "\nWrite " + myName + "'s next reply now.";
            String reply = AiClient.ask(system, prompt);
            if (reply == null) return;
            reply = reply.trim();
            if (reply.startsWith("\"") && reply.endsWith("\"") && reply.length() > 1) reply = reply.substring(1, reply.length() - 1).trim();
            if (reply.isEmpty() || reply.equalsIgnoreCase("SKIP") || reply.startsWith("SKIP")) return;
            if (!isEnabled()) return;

            int voicePercent = prefs().getInt(KEY_VOICE_PERCENT, 20);
            boolean asVoice = voicePercent > 0 && reply.length() < 220 && RANDOM.nextInt(100) < voicePercent;
            final String finalReply = reply;
            final String finalIncoming = lastIncoming;
            final String finalOther = otherName;

            // Short human-like "typing time"
            if (!isFast()) Thread.sleep(Math.min(9000, 1500 + finalReply.length() * 60L));
            if (asVoice) {
                final CountDownLatch vl = new CountDownLatch(1);
                final boolean[] ok = {false};
                VoiceNoteMaker.makeAndSend(account, dialogId, finalReply, 1.0f, 1.0f, (success, error) -> {
                    ok[0] = success;
                    vl.countDown();
                });
                vl.await(120, TimeUnit.SECONDS);
                if (ok[0]) {
                    markSent(dialogId);
                    log(finalOther, finalIncoming, finalReply, true);
                    return;
                }
            }
            final CountDownLatch sl = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    ConnectionsManager cm = ConnectionsManager.getInstance(account);
                    cm.keepNetworkAliveInBackground();
                    cm.resumeNetworkMaybe();
                    HashMap<String, String> extra = new HashMap<>(2);
                    extra.put("ng_auto_reply", "1");
                    SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(
                            finalReply, dialogId, null, null, null, true, null, null, extra, false, 0, 0, null, false);
                    SendMessagesHelper.getInstance(account).sendMessage(params);
                } catch (Throwable t) {
                    FileLog.e("Bartalap AI send error", t);
                } finally {
                    sl.countDown();
                }
            });
            sl.await(15, TimeUnit.SECONDS);
            markSent(dialogId);
            log(finalOther, finalIncoming, finalReply, false);
            Thread.sleep(6000); // let the message leave the phone before the process may sleep
        } catch (Throwable e) {
            FileLog.e("Bartalap AI process error", e);
        }
    }

    /**
     * Reads the user's own recent messages from his top private chats and asks the AI to describe
     * his writing style and facts about him. Call off the UI thread. Returns the style text or throws.
     */
    public static String learnStyle(int account) throws Exception {
        final ArrayList<Long> dialogs = new ArrayList<>();
        final CountDownLatch dl = new CountDownLatch(1);
        AndroidUtilities.runOnUIThread(() -> {
            try {
                MessagesController mc = MessagesController.getInstance(account);
                long self = UserConfig.getInstance(account).getClientUserId();
                for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
                    if (d == null || !DialogObject.isUserDialog(d.id) || d.id == self || d.id == 777000L) continue;
                    TLRPC.User u = mc.getUser(d.id);
                    if (u == null || u.bot) continue;
                    dialogs.add(d.id);
                    if (dialogs.size() >= 12) break;
                }
            } finally {
                dl.countDown();
            }
        });
        dl.await(10, TimeUnit.SECONDS);
        StringBuilder samples = new StringBuilder();
        int lines = 0;
        for (Long did : dialogs) {
            final TLRPC.messages_Messages[] holder = new TLRPC.messages_Messages[1];
            final CountDownLatch latch = new CountDownLatch(1);
            AndroidUtilities.runOnUIThread(() -> {
                TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                req.peer = MessagesController.getInstance(account).getInputPeer(did);
                req.limit = 60;
                ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                    if (response instanceof TLRPC.messages_Messages) holder[0] = (TLRPC.messages_Messages) response;
                    latch.countDown();
                });
            });
            latch.await(20, TimeUnit.SECONDS);
            if (holder[0] == null) continue;
            for (TLRPC.Message m : holder[0].messages) {
                if (!m.out || m.message == null || m.message.trim().isEmpty()) continue;
                String t = m.message.trim();
                if (t.length() > 300) t = t.substring(0, 300);
                samples.append("- ").append(t.replace('\n', ' ')).append('\n');
                if (++lines >= 250) break;
            }
            if (lines >= 250) break;
        }
        if (lines < 5) throw new Exception("শেখার মতো যথেষ্ট নিজের মেসেজ পাওয়া যায়নি");
        String system = "You analyse how a person writes on Telegram so an assistant can reply exactly like him.";
        String prompt = "These are messages this person sent to different people:\n" + samples
                + "\nIn Bengali, write short notes (max 15 bullet lines): his language mix (Bangla/Banglish/English), tone, typical length, "
                + "common words/phrases/greetings he uses, emoji habits, how he addresses people (ভাই/আপু etc.), "
                + "and useful facts about him or his work that appear in the messages (business, services, prices only if clearly stated). "
                + "Do not include phone numbers, passwords, codes or other private data.";
        return AiClient.ask(system, prompt);
    }
}
