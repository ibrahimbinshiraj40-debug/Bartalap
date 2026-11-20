package org.bartalap.tools;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.ChatActivityEnterView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Bartalap: tools that work inside an open chat (three-dot menu → "⚡ Bartalap Tools").
 * Text is placed in the message box so the user can check it before sending.
 */
public final class BartalapChatTools {

    private BartalapChatTools() {}

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bartalap_tools", Context.MODE_PRIVATE);
    }

    private static int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    private static void toast(String s) {
        Toast.makeText(ApplicationLoader.applicationContext, s, Toast.LENGTH_SHORT).show();
    }

    private static String chatName(ChatActivity chat, long dialogId, boolean firstNameOnly) {
        MessagesController mc = MessagesController.getInstance(chat.getCurrentAccount());
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = mc.getUser(dialogId);
            if (u == null) return "";
            if (firstNameOnly && u.first_name != null && !u.first_name.isEmpty()) return u.first_name;
            return ContactsController.formatName(u.first_name, u.last_name);
        }
        TLRPC.Chat c = mc.getChat(-dialogId);
        return c == null ? "" : c.title;
    }

    /** Puts text into the chat's message box (user taps send). */
    private static void putInInput(ChatActivity chat, String text) {
        try {
            ChatActivityEnterView enter = chat.getChatActivityEnterView();
            if (enter != null) {
                enter.setFieldText(text);
                toast("মেসেজ বক্সে বসানো হয়েছে, দেখে Send চাপুন ✅");
                return;
            }
        } catch (Throwable ignored) {
        }
        copy(text);
    }

    private static void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("bartalap", text));
            toast("কপি হয়েছে ✅");
        } catch (Throwable ignored) {
        }
    }

    public static void show(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        if (a == null) return;
        CharSequence[] items = {
                "⚡ Quick Reply পাঠান",
                "🎙️ লিখে বাংলা ভয়েস মেসেজ পাঠান",
                "⌨️ বাংলিশে লিখে বাংলায় পাঠান",
                "🤖 AI দিয়ে উত্তর লিখুন",
                "🧾 AI দিয়ে চ্যাটের সারাংশ",
                "✅ AI দিয়ে কাজের লিস্ট (কে কী কথা দিয়েছে)",
                "💳 AI দিয়ে পেমেন্ট স্ক্রিনশট পড়ুন",
                "📜 কন্টাক্ট টাইমলাইন",
                "📝 এই কন্টাক্টের নোট",
                "💰 বাকির খাতায় যোগ করুন",
                "😴 চ্যাট স্নুজ (পরে মনে করাবে)",
                "⚙️ AI সেটিংস"
        };
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("⚡ Bartalap Tools");
        b.setItems(items, (d, which) -> {
            switch (which) {
                case 0: quickReply(chat, dialogId); break;
                case 1: voiceNote(chat, dialogId); break;
                case 2: banglish(chat); break;
                case 3: aiReply(chat, dialogId); break;
                case 4: aiSummary(chat, dialogId); break;
                case 5: aiTasks(chat, dialogId); break;
                case 6: paymentScreenshot(chat, dialogId); break;
                case 7: timeline(chat, dialogId); break;
                case 8: contactNote(chat, dialogId); break;
                case 9: addToLedger(chat, dialogId); break;
                case 10: snooze(chat, dialogId); break;
                default: aiSettings(a, null); break;
            }
        });
        b.setNegativeButton("বন্ধ", null);
        chat.showDialog(b.create());
    }

    // ------------------------------------------------------------ quick reply

    private static void quickReply(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        JSONArray arr;
        try {
            arr = new JSONArray(prefs().getString("quick_replies", "[]"));
        } catch (Exception e) {
            arr = new JSONArray();
        }
        if (arr.length() == 0) {
            arr.put("আসসালামু আলাইকুম {নাম} ভাই, কিভাবে সাহায্য করতে পারি?");
            arr.put("ধন্যবাদ {নাম} ভাই, আপনার অর্ডার কনফার্ম হয়েছে ✅");
        }
        String name = chatName(chat, dialogId, true);
        CharSequence[] list = new CharSequence[arr.length()];
        for (int i = 0; i < arr.length(); i++) {
            list[i] = BartalapToolsActivity.fillTemplate(arr.optString(i), name);
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("⚡ Quick Reply (" + (name.isEmpty() ? "এই চ্যাট" : name) + ")");
        b.setItems(list, (d, which) -> putInInput(chat, list[which].toString()));
        b.setNegativeButton("বন্ধ", null);
        b.setNeutralButton("টেমপ্লেট এডিট", (d, w) -> chat.presentFragment(new BartalapToolsActivity(BartalapToolsActivity.MODE_QUICK_REPLY)));
        chat.showDialog(b.create());
    }

    // ------------------------------------------------------------ text to voice

    private static boolean hasLatin(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) return true;
        }
        return false;
    }

    private static void voiceNote(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText in = new EditText(a);
        in.setHint("বাংলায় লিখুন (বা বাংলিশে: ami ashchi)");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        in.setMinLines(3);
        TextView info = new TextView(a);
        info.setTextSize(12.5f);
        info.setPadding(0, dp(8), 0, 0);
        info.setText("ফোনের Google Text-to-Speech-এর বাংলা কণ্ঠে ভয়েস মেসেজ বানিয়ে পাঠাবে।");
        final float[] speed = {prefs().getFloat("voice_speed", 1.0f)};
        TextView speedBtn = new TextView(a);
        speedBtn.setTextSize(15);
        speedBtn.setPadding(0, dp(10), 0, 0);
        speedBtn.setText("গতি: " + speed[0] + "x (চাপ দিয়ে বদলান)");
        speedBtn.setOnClickListener(v -> {
            speed[0] = speed[0] >= 1.4f ? 0.8f : Math.round((speed[0] + 0.1f) * 10f) / 10f;
            speedBtn.setText("গতি: " + speed[0] + "x (চাপ দিয়ে বদলান)");
        });
        box.addView(in);
        box.addView(speedBtn);
        box.addView(info);
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("🎙️ লিখে ভয়েস পাঠান");
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("ভয়েস পাঠান", (d, w) -> {
            String t = in.getText().toString().trim();
            if (t.isEmpty()) {
                toast("কিছু লিখুন");
                return;
            }
            if (hasLatin(t)) t = BanglishConverter.convert(t);
            prefs().edit().putFloat("voice_speed", speed[0]).apply();
            toast("ভয়েস তৈরি হচ্ছে… ⏳");
            VoiceNoteMaker.makeAndSend(chat.getCurrentAccount(), dialogId, t, speed[0], 1.0f, (ok, error) -> {
                if (ok) toast("ভয়েস মেসেজ পাঠানো হয়েছে ✅");
                else if (chat.getParentActivity() != null) {
                    AlertDialog.Builder eb = new AlertDialog.Builder(chat.getParentActivity());
                    eb.setTitle("⚠️ ভয়েস পাঠানো যায়নি");
                    eb.setMessage(error);
                    eb.setPositiveButton("ঠিক আছে", null);
                    eb.show();
                }
            });
        });
        chat.showDialog(b.create());
    }

    // ------------------------------------------------------------ banglish

    private static void banglish(ChatActivity chat) {
        Activity a = chat.getParentActivity();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText in = new EditText(a);
        in.setHint("বাংলিশে লিখুন: ami ashchi");
        in.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        TextView preview = new TextView(a);
        preview.setTextSize(18);
        preview.setPadding(0, dp(10), 0, 0);
        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { preview.setText(BanglishConverter.convert(s.toString())); }
        });
        box.addView(in);
        box.addView(preview);
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("⌨️ বাংলিশ → বাংলা");
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("মেসেজ বক্সে দিন", (d, w) -> putInInput(chat, BanglishConverter.convert(in.getText().toString())));
        chat.showDialog(b.create());
    }

    // ------------------------------------------------------------ AI

    private static String transcript(ChatActivity chat, long dialogId, int max) {
        ArrayList<MessageObject> msgs = chat.messages;
        if (msgs == null) return "";
        String me = "আমি";
        String other = chatName(chat, dialogId, true);
        MessagesController mc = MessagesController.getInstance(chat.getCurrentAccount());
        ArrayList<String> lines = new ArrayList<>();
        for (int i = 0; i < msgs.size() && lines.size() < max; i++) {
            MessageObject m = msgs.get(i);
            if (m == null || m.messageOwner == null || m.messageOwner.message == null || m.messageOwner.message.trim().isEmpty()) continue;
            String who;
            if (m.isOut()) {
                who = me;
            } else if (DialogObject.isUserDialog(dialogId)) {
                who = other.isEmpty() ? "সে" : other;
            } else {
                long from = m.getFromChatId();
                TLRPC.User u = from > 0 ? mc.getUser(from) : null;
                who = u != null ? ContactsController.formatName(u.first_name, u.last_name) : "সদস্য";
            }
            String text = m.messageOwner.message.trim();
            if (text.length() > 500) text = text.substring(0, 500) + "…";
            lines.add(who + ": " + text);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = lines.size() - 1; i >= 0; i--) sb.append(lines.get(i)).append('\n');
        return sb.toString();
    }

    private static void runAi(ChatActivity chat, String title, String system, String prompt, boolean offerInsert) {
        Activity a = chat.getParentActivity();
        if (!AiClient.isConfigured()) {
            aiSettings(a, () -> runAi(chat, title, system, prompt, offerInsert));
            return;
        }
        toast("AI ভাবছে… ⏳");
        new Thread(() -> {
            String answer;
            boolean ok;
            try {
                answer = AiClient.ask(system, prompt);
                ok = true;
            } catch (Throwable e) {
                answer = e.getMessage() == null ? "AI সমস্যা" : e.getMessage();
                ok = false;
            }
            final String result = answer;
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (chat.getParentActivity() == null) return;
                TextView tv = new TextView(chat.getParentActivity());
                tv.setText(result);
                tv.setTextSize(16);
                tv.setTextIsSelectable(true);
                tv.setPadding(dp(22), dp(10), dp(22), dp(10));
                ScrollView sv = new ScrollView(chat.getParentActivity());
                sv.addView(tv);
                AlertDialog.Builder b = new AlertDialog.Builder(chat.getParentActivity());
                b.setTitle(success ? title : "⚠️ সমস্যা");
                b.setView(sv);
                if (success && offerInsert) {
                    b.setPositiveButton("মেসেজ বক্সে দিন", (d, w) -> putInInput(chat, result));
                } else if (success) {
                    b.setPositiveButton("কপি", (d, w) -> copy(result));
                } else {
                    b.setPositiveButton("AI সেটিংস", (d, w) -> aiSettings(chat.getParentActivity(), null));
                }
                b.setNegativeButton("বন্ধ", null);
                chat.showDialog(b.create());
            });
        }).start();
    }

    private static void aiReply(ChatActivity chat, long dialogId) {
        String t = transcript(chat, dialogId, 30);
        if (t.isEmpty()) {
            toast("চ্যাটে কোনো লেখা মেসেজ নেই");
            return;
        }
        String system = "You help a Bangladeshi seller and content creator reply on Telegram. "
                + "Write ONE short, polite, natural reply as 'আমি' to the latest message. "
                + "Reply in the same language the other person used (usually Bengali). Output only the reply text.";
        runAi(chat, "🤖 AI উত্তর", system, "চ্যাট:\n" + t + "\nএখন 'আমি' হিসেবে পরের উত্তরটা লিখুন।", true);
    }

    private static void aiSummary(ChatActivity chat, long dialogId) {
        String t = transcript(chat, dialogId, 80);
        if (t.isEmpty()) {
            toast("চ্যাটে কোনো লেখা মেসেজ নেই");
            return;
        }
        String system = "Summarize this Telegram chat in simple Bengali in 3-6 short bullet points. "
                + "Mention any orders, payments, amounts, promises and pending tasks.";
        runAi(chat, "🧾 চ্যাটের সারাংশ", system, t, false);
    }

    private static void aiTasks(ChatActivity chat, long dialogId) {
        String t = transcript(chat, dialogId, 80);
        if (t.isEmpty()) {
            toast("চ্যাটে কোনো লেখা মেসেজ নেই");
            return;
        }
        String system = "From this Telegram chat, list in simple Bengali every promise, pending task, payment due and deadline. "
                + "Format each line as: '• কে — কী করবে — কবে'. If nothing is pending, say 'কোনো বাকি কাজ নেই'.";
        runAi(chat, "✅ কাজের লিস্ট", system, t, false);
    }

    // ------------------------------------------------------------ payment screenshot (AI vision)

    private static void paymentScreenshot(ChatActivity chat, long dialogId) {
        ArrayList<MessageObject> msgs = chat.messages;
        ArrayList<String> paths = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        if (msgs != null) {
            for (int i = 0; i < msgs.size() && paths.size() < 8; i++) {
                MessageObject m = msgs.get(i);
                if (m == null || m.messageOwner == null || !m.isPhoto()) continue;
                java.io.File f = org.telegram.messenger.FileLoader.getInstance(chat.getCurrentAccount()).getPathToMessage(m.messageOwner);
                if (f == null || !f.exists()) continue;
                paths.add(f.getAbsolutePath());
                labels.add((m.isOut() ? "আমার পাঠানো ছবি · " : "আসা ছবি · ")
                        + new SimpleDateFormat("dd/MM hh:mm a", Locale.US).format(new Date(m.messageOwner.date * 1000L)));
            }
        }
        if (paths.isEmpty()) {
            toast("এই চ্যাটে খোলা (ডাউনলোড করা) কোনো ছবি পাওয়া যায়নি। আগে স্ক্রিনশটটা খুলে নিন।");
            return;
        }
        if (!AiClient.isConfigured()) {
            aiSettings(chat.getParentActivity(), () -> paymentScreenshot(chat, dialogId));
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(chat.getParentActivity());
        b.setTitle("কোন ছবিটা পড়বে?");
        b.setItems(labels.toArray(new CharSequence[0]), (d, which) -> readPayment(chat, dialogId, paths.get(which)));
        chat.showDialog(b.create());
    }

    private static void readPayment(ChatActivity chat, long dialogId, String path) {
        toast("AI ছবি পড়ছে… ⏳");
        new Thread(() -> {
            String result;
            boolean ok;
            try {
                String b64 = AiClient.imageToBase64(path);
                result = AiClient.ask("You read Bangladeshi mobile payment screenshots (bKash, Nagad, Rocket, Upay, bank).",
                        "এই স্ক্রিনশট থেকে বাংলায় লিখুন:\nমাধ্যম:\nটাকা:\nTrxID:\nকোন নাম্বার থেকে/কোন নাম্বারে:\nতারিখ ও সময়:\n"
                                + "যদি এটা পেমেন্ট স্ক্রিনশট না হয় তাহলে শুধু লিখুন: 'এটা পেমেন্ট স্ক্রিনশট না'।", b64);
                ok = true;
            } catch (Throwable e) {
                result = e.getMessage() == null ? "AI সমস্যা" : e.getMessage();
                ok = false;
            }
            final String text = result;
            final boolean success = ok;
            AndroidUtilities.runOnUIThread(() -> {
                if (chat.getParentActivity() == null) return;
                AlertDialog.Builder b = new AlertDialog.Builder(chat.getParentActivity());
                b.setTitle(success ? "💳 পেমেন্টের তথ্য" : "⚠️ সমস্যা");
                b.setMessage(text);
                b.setNegativeButton("বন্ধ", null);
                if (success) {
                    b.setPositiveButton("কপি", (d, w) -> copy(text));
                    b.setNeutralButton("বাকির খাতা থেকে মিলিয়ে নিন", (d, w) -> chat.presentFragment(new BartalapToolsActivity(BartalapToolsActivity.MODE_LEDGER)));
                }
                chat.showDialog(b.create());
            });
        }).start();
    }

    // ------------------------------------------------------------ contact timeline

    private static void timeline(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        String name = chatName(chat, dialogId, false);
        StringBuilder sb = new StringBuilder();
        String note = prefs().getString("chat_note_" + dialogId, "");
        sb.append("📝 নোট:\n").append(note.isEmpty() ? "নেই" : note).append("\n\n");
        sb.append("💰 বাকির খাতা:\n");
        double due = 0;
        int dueCount = 0;
        try {
            JSONArray arr = new JSONArray(prefs().getString("due_ledger", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                boolean match = o.optLong("dialog_id", 0) == dialogId || (!name.isEmpty() && name.equalsIgnoreCase(o.optString("name")));
                if (!match) continue;
                due += o.optDouble("amount", 0);
                dueCount++;
                sb.append("• ৳").append(o.optDouble("amount", 0)).append(" ").append(o.optString("note")).append(" (").append(o.optString("date")).append(")\n");
            }
        } catch (Exception ignored) {
        }
        if (dueCount == 0) sb.append("কিছু বাকি নেই\n");
        else sb.append("মোট বাকি: ৳").append(due).append("\n");
        sb.append("\n🧾 চ্যাটে পাওয়া পেমেন্ট:\n");
        int pay = 0;
        int links = 0;
        int photos = 0;
        int total = 0;
        int first = Integer.MAX_VALUE;
        ArrayList<MessageObject> msgs = chat.messages;
        if (msgs != null) {
            for (MessageObject m : msgs) {
                if (m == null || m.messageOwner == null) continue;
                total++;
                first = Math.min(first, m.messageOwner.date);
                if (m.isPhoto()) photos++;
                String t = m.messageOwner.message;
                if (t == null || t.isEmpty()) continue;
                if (t.contains("http")) links++;
                TrxDetector.Result r = TrxDetector.detect(t);
                if (!r.trxId.isEmpty() && pay < 10) {
                    pay++;
                    sb.append("• ").append(r.method.isEmpty() ? "" : r.method + " ").append(r.amount.isEmpty() ? "" : "৳" + r.amount + " ")
                            .append("TrxID ").append(r.trxId).append(" (")
                            .append(new SimpleDateFormat("dd/MM", Locale.US).format(new Date(m.messageOwner.date * 1000L))).append(")\n");
                }
            }
        }
        if (pay == 0) sb.append("লোড করা মেসেজে কোনো TrxID নেই\n");
        sb.append("\n📊 লোড করা ").append(total).append(" মেসেজে: ").append(photos).append(" ছবি, ").append(links).append(" লিংক");
        if (first != Integer.MAX_VALUE) {
            sb.append("\n📅 শুরু (লোড করা অংশে): ").append(new SimpleDateFormat("dd MMM yyyy", Locale.US).format(new Date(first * 1000L)));
        }
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("📜 " + name);
        b.setMessage(sb.toString());
        b.setPositiveButton("ঠিক আছে", null);
        b.setNeutralButton("নোট এডিট", (d, w) -> contactNote(chat, dialogId));
        chat.showDialog(b.create());
    }

    public static void aiSettings(Activity a, Runnable after) {
        if (a == null) return;
        SharedPreferences p = AiClient.prefs();
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView fmt = new TextView(a);
        final boolean[] openai = {AiClient.isOpenAiFormat()};
        fmt.setTextSize(15);
        fmt.setPadding(0, dp(6), 0, dp(10));
        fmt.setText(openai[0] ? "ধরন: OpenAI / Gemini (চাপ দিয়ে বদলান)" : "ধরন: Claude (Anthropic) (চাপ দিয়ে বদলান)");
        EditText base = new EditText(a);
        base.setHint("Base URL (খালি রাখলে ডিফল্ট)");
        base.setSingleLine(true);
        base.setText(p.getString(AiClient.KEY_BASE, ""));
        EditText key = new EditText(a);
        key.setHint("API Key");
        key.setSingleLine(true);
        key.setText(p.getString(AiClient.KEY_API_KEY, ""));
        EditText model = new EditText(a);
        model.setSingleLine(true);
        model.setText(p.getString(AiClient.KEY_MODEL, ""));
        model.setHint("মডেল (খালি = " + (openai[0] ? AiClient.DEFAULT_OPENAI_MODEL : AiClient.DEFAULT_ANTHROPIC_MODEL) + ")");
        fmt.setOnClickListener(v -> {
            openai[0] = !openai[0];
            fmt.setText(openai[0] ? "ধরন: OpenAI / Gemini (চাপ দিয়ে বদলান)" : "ধরন: Claude (Anthropic) (চাপ দিয়ে বদলান)");
            model.setHint("মডেল (খালি = " + (openai[0] ? AiClient.DEFAULT_OPENAI_MODEL : AiClient.DEFAULT_ANTHROPIC_MODEL) + ")");
        });
        TextView help = new TextView(a);
        help.setTextSize(12.5f);
        help.setPadding(0, dp(10), 0, 0);
        help.setText("Claude: console.anthropic.com থেকে API Key\nGemini: aistudio.google.com থেকে API Key (ধরন: OpenAI/Gemini)\nনিজের কেনা API হলে তার Base URL দিন।");
        box.addView(fmt);
        box.addView(base);
        box.addView(key);
        box.addView(model);
        box.addView(help);
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("⚙️ AI সেটিংস");
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("সেভ", (d, w) -> {
            p.edit().putString(AiClient.KEY_FORMAT, openai[0] ? "openai" : "anthropic")
                    .putString(AiClient.KEY_BASE, base.getText().toString().trim())
                    .putString(AiClient.KEY_API_KEY, key.getText().toString().trim())
                    .putString(AiClient.KEY_MODEL, model.getText().toString().trim())
                    .apply();
            toast("AI সেটিংস সেভ হয়েছে ✅");
            if (after != null && AiClient.isConfigured()) after.run();
        });
        b.show();
    }

    // ------------------------------------------------------------ snooze

    private static void snooze(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        String name = chatName(chat, dialogId, false);
        CharSequence[] opts = {"১ ঘণ্টা পর", "৩ ঘণ্টা পর", "আজ রাত ৯টায়", "কাল সকাল ৯টায়"};
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("😴 কখন মনে করাবে?");
        b.setItems(opts, (d, which) -> {
            long minutes;
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar t = java.util.Calendar.getInstance();
            t.set(java.util.Calendar.MINUTE, 0);
            t.set(java.util.Calendar.SECOND, 0);
            if (which == 0) {
                minutes = 60;
            } else if (which == 1) {
                minutes = 180;
            } else {
                if (which == 2) {
                    t.set(java.util.Calendar.HOUR_OF_DAY, 21);
                } else {
                    t.add(java.util.Calendar.DAY_OF_YEAR, 1);
                    t.set(java.util.Calendar.HOUR_OF_DAY, 9);
                }
                if (!t.after(now)) t.add(java.util.Calendar.DAY_OF_YEAR, 1);
                minutes = Math.max(1, (t.getTimeInMillis() - now.getTimeInMillis()) / 60000L);
            }
            BartalapReminders.snooze(chat.getCurrentAccount(), dialogId, name, minutes);
            toast("চ্যাটটা Archive-এ গেল। সময় হলে নোটিফিকেশন দিয়ে ফিরিয়ে আনবে ✅");
            chat.finishFragment();
        });
        b.setNegativeButton("বাতিল", null);
        chat.showDialog(b.create());
    }

    // ------------------------------------------------------------ notes & ledger

    private static String today() {
        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date());
    }

    private static void contactNote(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        String name = chatName(chat, dialogId, false);
        String key = "chat_note_" + dialogId;
        EditText e = new EditText(a);
        e.setHint("এই কন্টাক্ট নিয়ে নোট লিখুন");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setMinLines(3);
        e.setText(prefs().getString(key, ""));
        LinearLayout box = new LinearLayout(a);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        box.addView(e, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("📝 " + name);
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("সেভ", (d, w) -> {
            String note = e.getText().toString().trim();
            prefs().edit().putString(key, note).apply();
            // Also keep it in the main notes list so it shows in Bartalap Tools → কন্টাক্ট নোট
            try {
                JSONArray arr = new JSONArray(prefs().getString("contact_notes", "[]"));
                JSONArray out = new JSONArray();
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o != null && o.optLong("dialog_id", 0) == dialogId) continue;
                    out.put(arr.opt(i));
                }
                if (!note.isEmpty()) {
                    JSONObject o = new JSONObject();
                    o.put("name", name);
                    o.put("note", note);
                    o.put("date", today());
                    o.put("dialog_id", dialogId);
                    out.put(o);
                }
                prefs().edit().putString("contact_notes", out.toString()).apply();
            } catch (Exception ignored) {
            }
            toast("নোট সেভ হয়েছে ✅");
        });
        chat.showDialog(b.create());
    }

    private static void addToLedger(ChatActivity chat, long dialogId) {
        Activity a = chat.getParentActivity();
        String name = chatName(chat, dialogId, false);
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText amount = new EditText(a);
        amount.setHint("কত টাকা বাকি");
        amount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText note = new EditText(a);
        note.setHint("কী জন্য (যেমন: ChatGPT Plus)");
        note.setSingleLine(true);
        box.addView(amount);
        box.addView(note);
        AlertDialog.Builder b = new AlertDialog.Builder(a);
        b.setTitle("💰 " + name + " এর বাকি");
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("যোগ করুন", (d, w) -> {
            double v;
            try {
                v = Double.parseDouble(TrxDetector.toEnglishDigits(amount.getText().toString()).replace(",", "").trim());
            } catch (Exception ex) {
                v = 0;
            }
            if (v <= 0) {
                toast("টাকার পরিমাণ দিন");
                return;
            }
            try {
                JSONArray arr = new JSONArray(prefs().getString("due_ledger", "[]"));
                JSONObject o = new JSONObject();
                o.put("name", name);
                o.put("amount", v);
                o.put("note", note.getText().toString().trim());
                o.put("date", today());
                o.put("dialog_id", dialogId);
                arr.put(o);
                prefs().edit().putString("due_ledger", arr.toString()).apply();
                toast("বাকির খাতায় যোগ হয়েছে ✅");
            } catch (Exception ignored) {
            }
        });
        chat.showDialog(b.create());
    }
}
