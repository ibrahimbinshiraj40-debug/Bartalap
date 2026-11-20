package org.bartalap.tools;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.browser.Browser;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Date;
import java.util.Locale;

/**
 * Bartalap Tools: new features that NayaGram does not have.
 *  1. Banglish -> Bangla typing
 *  2. Quick Reply templates with {নাম}
 *  3. Contact notes
 *  4. Due ledger (বাকির খাতা)
 *  5. bKash / Nagad TrxID detector
 */
public class BartalapToolsActivity extends BaseFragment {

    public static final int MODE_HOME = 0;
    public static final int MODE_BANGLISH = 1;
    public static final int MODE_QUICK_REPLY = 2;
    public static final int MODE_NOTES = 3;
    public static final int MODE_LEDGER = 4;
    public static final int MODE_TRX = 5;
    public static final int MODE_INBOX = 6;
    public static final int MODE_LINKS = 7;
    public static final int MODE_KEYWORDS = 8;
    public static final int MODE_YOUTUBE = 9;
    public static final int MODE_BEST_TIME = 10;
    public static final int MODE_CROSSPOST = 11;
    public static final int MODE_TRACKER = 12;
    public static final int MODE_UNIFIED = 13;
    public static final int MODE_UNIFIED_CHAT = 14;
    public static final int MODE_PRIORITY = 15;
    public static final int MODE_POSTS = 16;
    public static final int MODE_WEBSITE = 17;
    public static final int MODE_MEDIA = 18;
    public static final int MODE_LIVE = 19;
    public static final int MODE_AI_AUTO = 20;

    private static final String PREFS = "bartalap_tools";
    private static final String KEY_TEMPLATES = "quick_replies";
    private static final String KEY_NOTES = "contact_notes";
    private static final String KEY_LEDGER = "due_ledger";

    private final int mode;
    private LinearLayout content;

    public BartalapToolsActivity() {
        this(MODE_HOME);
    }

    public BartalapToolsActivity(int mode) {
        this.mode = mode;
    }

    // ---------------------------------------------------------------- helpers

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static JSONArray loadArray(String key) {
        try {
            return new JSONArray(prefs().getString(key, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void saveArray(String key, JSONArray arr) {
        prefs().edit().putString(key, arr.toString()).apply();
    }

    private static JSONArray removeAt(JSONArray arr, int index) {
        JSONArray out = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            if (i != index) out.put(arr.opt(i));
        }
        return out;
    }

    private void copy(String text) {
        try {
            ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("bartalap", text));
            toast("কপি হয়েছে ✅");
        } catch (Exception ignored) {
        }
    }

    private void toast(String s) {
        Toast.makeText(ApplicationLoader.applicationContext, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return AndroidUtilities.dp(v);
    }

    private GradientDrawable card(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(14));
        return d;
    }

    private TextView text(Context ctx, String s, float size, boolean bold, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private int textColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteBlackText);
    }

    private int grayColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhiteGrayText);
    }

    private int cardColor() {
        return Theme.getColor(Theme.key_windowBackgroundWhite);
    }

    private int accent() {
        return Theme.getColor(Theme.key_featuredStickers_addButton);
    }

    private LinearLayout.LayoutParams lp(int topMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topMargin);
        return p;
    }

    private Button button(Context ctx, String label, View.OnClickListener l) {
        Button b = new Button(ctx);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(15);
        GradientDrawable d = new GradientDrawable();
        d.setColor(accent());
        d.setCornerRadius(dp(10));
        b.setBackground(d);
        b.setOnClickListener(l);
        return b;
    }

    private EditText input(Context ctx, String hint, boolean multiLine) {
        EditText e = new EditText(ctx);
        e.setHint(hint);
        e.setTextColor(textColor());
        e.setHintTextColor(grayColor());
        e.setTextSize(16);
        if (multiLine) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        } else {
            e.setInputType(InputType.TYPE_CLASS_TEXT);
            e.setSingleLine(true);
        }
        return e;
    }

    private View itemCard(Context ctx, String title, String sub, View.OnClickListener click, View.OnLongClickListener longClick) {
        LinearLayout box = new LinearLayout(ctx);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackground(card(cardColor()));
        box.addView(text(ctx, title, 16, true, textColor()));
        if (sub != null && !sub.isEmpty()) {
            TextView s = text(ctx, sub, 13.5f, false, grayColor());
            s.setPadding(0, dp(4), 0, 0);
            box.addView(s);
        }
        if (click != null) box.setOnClickListener(click);
        if (longClick != null) box.setOnLongClickListener(longClick);
        box.setLayoutParams(lp(10));
        return box;
    }

    private void confirm(String title, String msg, Runnable yes) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(title);
        b.setMessage(msg);
        b.setNegativeButton("না", null);
        b.setPositiveButton("হ্যাঁ", (d, w) -> yes.run());
        showDialog(b.create());
    }

    // ---------------------------------------------------------------- view

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(titleForMode());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(8), dp(14), dp(24));
        scroll.addView(content, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fragmentView = root;

        build(context);
        return fragmentView;
    }

    private String titleForMode() {
        switch (mode) {
            case MODE_BANGLISH: return "বাংলিশ → বাংলা";
            case MODE_QUICK_REPLY: return "Quick Reply টেমপ্লেট";
            case MODE_NOTES: return "কন্টাক্ট নোট";
            case MODE_LEDGER: return "বাকির খাতা";
            case MODE_TRX: return "TrxID ডিটেক্টর";
            case MODE_INBOX: return "স্মার্ট ইনবক্স";
            case MODE_LINKS: return "লিংক ভল্ট";
            case MODE_KEYWORDS: return "অফার / কীওয়ার্ড খুঁজুন";
            case MODE_YOUTUBE: return "YouTube অটো পোস্ট";
            case MODE_BEST_TIME: return "পোস্ট করার সেরা সময়";
            case MODE_CROSSPOST: return "এক পোস্ট সব জায়গায়";
            case MODE_TRACKER: return "চ্যানেল ট্র্যাকার";
            case MODE_UNIFIED: return "ইউনিফাইড ইনবক্স";
            case MODE_UNIFIED_CHAT: return "চ্যাট";
            case MODE_PRIORITY: return "জরুরি ইনবক্স";
            case MODE_POSTS: return "পোস্ট পারফরম্যান্স ও A/B";
            case MODE_WEBSITE: return "চ্যানেল থেকে ওয়েবসাইট";
            case MODE_MEDIA: return "মিডিয়া খুঁজুন";
            case MODE_LIVE: return "লাইভ স্টুডিও";
            case MODE_AI_AUTO: return "AI অটো-রিপ্লাই";
            default: return "Bartalap Tools";
        }
    }

    private void build(Context ctx) {
        content.removeAllViews();
        switch (mode) {
            case MODE_BANGLISH: buildBanglish(ctx); break;
            case MODE_QUICK_REPLY: buildQuickReply(ctx); break;
            case MODE_NOTES: buildNotes(ctx); break;
            case MODE_LEDGER: buildLedger(ctx); break;
            case MODE_TRX: buildTrx(ctx); break;
            case MODE_INBOX: buildInbox(ctx); break;
            case MODE_LINKS: buildLinks(ctx); break;
            case MODE_KEYWORDS: buildKeywords(ctx); break;
            case MODE_YOUTUBE: buildYouTube(ctx); break;
            case MODE_BEST_TIME: buildBestTime(ctx); break;
            case MODE_CROSSPOST: buildCrossPost(ctx); break;
            case MODE_TRACKER: buildTracker(ctx); break;
            case MODE_UNIFIED: buildUnified(ctx); break;
            case MODE_UNIFIED_CHAT: buildUnifiedChat(ctx); break;
            case MODE_PRIORITY: buildPriority(ctx); break;
            case MODE_POSTS: buildPosts(ctx); break;
            case MODE_WEBSITE: buildWebsite(ctx); break;
            case MODE_MEDIA: buildMedia(ctx); break;
            case MODE_LIVE: buildLive(ctx); break;
            case MODE_AI_AUTO: buildAiAuto(ctx); break;
            default: buildHome(ctx); break;
        }
    }

    // ---------------------------------------------------------------- home

    private void buildHome(Context ctx) {
        TextView intro = text(ctx, "Bartalap-এর নিজস্ব ফিচার। যেকোনোটায় চাপ দিন 👇\nটিপস: যেকোনো চ্যাটে ⋮ → ⚡ Bartalap Tools থেকে Quick Reply, AI উত্তর, নোট সরাসরি পাবেন।", 14, false, grayColor());
        intro.setPadding(dp(4), dp(8), dp(4), dp(4));
        content.addView(intro);
        boolean aiOn = org.telegram.messenger.BartalapAiAutoReply.isEnabled();
        content.addView(itemCard(ctx, "🤖 AI অটো-রিপ্লাই: " + (aiOn ? "চালু ✅" : "বন্ধ"),
                "আপনি অফলাইনে থাকলে Claude আপনার মতো করে সবার মেসেজের উত্তর দেবে, কখনো লিখে কখনো ভয়েসে। চালু/বন্ধ আর সেটিংস এখানে",
                v -> presentFragment(new BartalapToolsActivity(MODE_AI_AUTO)), null));
        content.addView(itemCard(ctx, "📲 ইউনিফাইড ইনবক্স (WhatsApp, Messenger…)", "WhatsApp, Messenger, Instagram, IMO-র মেসেজ এখানে। কেউ ডিলিট করলেও দেখবেন, এখান থেকেই রিপ্লাই",
                v -> presentFragment(new BartalapToolsActivity(MODE_UNIFIED)), null));
        content.addView(itemCard(ctx, "⭐ জরুরি ইনবক্স", "পেমেন্ট, অর্ডার, জরুরি মেসেজ আগে দেখাবে",
                v -> presentFragment(new BartalapToolsActivity(MODE_PRIORITY)), null));
        content.addView(ghostCard(ctx));
        boolean nsOn = BartalapAudio.isNoiseFilterEnabled();
        content.addView(itemCard(ctx, "🎙 ভয়েস মেসেজে নয়েজ কমানো: " + (nsOn ? "চালু ✅" : "বন্ধ"),
                "রেকর্ড করা ভয়েসের পেছনের শব্দ কমাবে আর আওয়াজ ঠিক রাখবে। চাপ দিয়ে চালু/বন্ধ করুন",
                v -> {
                    BartalapAudio.setNoiseFilterEnabled(!BartalapAudio.isNoiseFilterEnabled());
                    build(ctx);
                }, null));
        boolean digestOn = BartalapReminders.isDigestEnabled();
        content.addView(itemCard(ctx, "🌙 ডেইলি ডাইজেস্ট: " + (digestOn ? "চালু ✅" : "বন্ধ"),
                "প্রতিদিন রাত ৯টায় একটা নোটিফিকেশন: কতগুলো মেসেজ পড়া হয়নি, কার উত্তর বাকি। চাপ দিয়ে চালু/বন্ধ করুন",
                v -> {
                    boolean on = !BartalapReminders.isDigestEnabled();
                    BartalapReminders.setDigestEnabled(on);
                    if (on) {
                        String now = BartalapReminders.buildDigest(currentAccount);
                        if (getParentActivity() != null) {
                            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                            b.setTitle("চালু হয়েছে ✅ আজকের নমুনা:");
                            b.setMessage(now);
                            b.setPositiveButton("ঠিক আছে", null);
                            showDialog(b.create());
                        }
                    } else {
                        toast("ডেইলি ডাইজেস্ট বন্ধ");
                    }
                    build(ctx);
                }, null));
        content.addView(itemCard(ctx, "🤖 AI সেটিংস", "Claude বা Gemini API Key দিন। তারপর যেকোনো চ্যাটে ⋮ → ⚡ Bartalap Tools থেকে AI উত্তর আর সারাংশ পাবেন",
                v -> BartalapChatTools.aiSettings(getParentActivity(), null), null));
        content.addView(itemCard(ctx, "📥 স্মার্ট ইনবক্স", "যাদের উত্তর দেননি, আর যাদের ফলো-আপ দরকার, আলাদা লিস্টে",
                v -> presentFragment(new BartalapToolsActivity(MODE_INBOX)), null));
        content.addView(itemCard(ctx, "▶️ YouTube অটো পোস্ট", "আপনার YouTube চ্যানেলে নতুন ভিডিও এলে নিজে থেকে Telegram চ্যানেলে পোস্ট হবে",
                v -> presentFragment(new BartalapToolsActivity(MODE_YOUTUBE)), null));
        content.addView(itemCard(ctx, "🚀 এক পোস্ট সব জায়গায়", "একবার লিখে অনেকগুলো চ্যানেল আর গ্রুপে একসাথে পোস্ট করুন",
                v -> presentFragment(new BartalapToolsActivity(MODE_CROSSPOST)), null));
        content.addView(itemCard(ctx, "📈 চ্যানেল ট্র্যাকার", "নিজের বা প্রতিযোগীর পাবলিক চ্যানেলের সাবস্ক্রাইবার কত বাড়ছে দেখুন",
                v -> presentFragment(new BartalapToolsActivity(MODE_TRACKER)), null));
        content.addView(itemCard(ctx, "🔴 লাইভ স্টুডিও", "চ্যানেলে লাইভ, স্ক্রিন লাইভ, আর একসাথে Telegram + YouTube + Facebook লাইভের সহজ গাইড",
                v -> presentFragment(new BartalapToolsActivity(MODE_LIVE)), null));
        content.addView(itemCard(ctx, "🔥 পোস্ট পারফরম্যান্স ও A/B তুলনা", "কোন পোস্টে বেশি ভিউ, দুটো পোস্ট পাশাপাশি তুলনা",
                v -> presentFragment(new BartalapToolsActivity(MODE_POSTS)), null));
        content.addView(itemCard(ctx, "🌐 চ্যানেল থেকে ওয়েবসাইট", "চ্যানেলের পোস্ট দিয়ে এক চাপে ওয়েবসাইট (Netlify-তে আপলোড করুন)",
                v -> presentFragment(new BartalapToolsActivity(MODE_WEBSITE)), null));
        content.addView(itemCard(ctx, "🗂 মিডিয়া খুঁজুন", "সব চ্যাটের ছবি, ভিডিও, ফাইল, গান, ভয়েস আলাদা আলাদা",
                v -> presentFragment(new BartalapToolsActivity(MODE_MEDIA)), null));
        content.addView(itemCard(ctx, "📊 পোস্ট করার সেরা সময়", "আপনার Telegram চ্যানেলে কোন সময় আর কোন দিন পোস্ট করলে বেশি ভিউ আসে",
                v -> presentFragment(new BartalapToolsActivity(MODE_BEST_TIME)), null));
        content.addView(itemCard(ctx, "🔗 লিংক ভল্ট", "সব চ্যাটে আসা লিংক এক জায়গায়: YouTube, Drive, Facebook, Telegram",
                v -> presentFragment(new BartalapToolsActivity(MODE_LINKS)), null));
        content.addView(itemCard(ctx, "🔔 অফার / কীওয়ার্ড খুঁজুন", "সব চ্যানেল আর গ্রুপে \"ChatGPT Plus\", \"Canva\" এর মতো শব্দ খুঁজুন",
                v -> presentFragment(new BartalapToolsActivity(MODE_KEYWORDS)), null));
        content.addView(itemCard(ctx, "⌨️ বাংলিশ → বাংলা", "\"ami bhalo achi\" লিখলে \"আমি ভালো আচি\" হয়ে যাবে, কপি করে চ্যাটে পাঠান",
                v -> presentFragment(new BartalapToolsActivity(MODE_BANGLISH)), null));
        content.addView(itemCard(ctx, "⚡ Quick Reply টেমপ্লেট", "প্রাইস লিস্ট, পেমেন্ট নাম্বার, সালাম ইত্যাদি এক ট্যাপে কপি, {নাম} নিজে বসবে",
                v -> presentFragment(new BartalapToolsActivity(MODE_QUICK_REPLY)), null));
        content.addView(itemCard(ctx, "📝 কন্টাক্ট নোট", "যেকোনো কন্টাক্টের জন্য নিজের গোপন নোট রাখুন",
                v -> presentFragment(new BartalapToolsActivity(MODE_NOTES)), null));
        content.addView(itemCard(ctx, "💰 বাকির খাতা", "কে কত টাকা বাকি, এক জায়গায় হিসাব আর মোট বাকি",
                v -> presentFragment(new BartalapToolsActivity(MODE_LEDGER)), null));
        content.addView(itemCard(ctx, "🔎 TrxID ডিটেক্টর", "bKash/Nagad/Rocket SMS পেস্ট করলে TrxID, টাকা, নাম্বার বের করে দেবে",
                v -> presentFragment(new BartalapToolsActivity(MODE_TRX)), null));
        content.addView(itemCard(ctx, "💾 ব্যাকআপ / রিস্টোর", "নোট, বাকির খাতা, টেমপ্লেট সব Saved Messages-এ ব্যাকআপ রাখুন",
                v -> backupDialog(ctx), null));
    }

    // ---------------------------------------------------------------- banglish

    private void buildBanglish(Context ctx) {
        EditText in = input(ctx, "এখানে বাংলিশ লিখুন, যেমন: ami tomake bhalobashi", true);
        in.setLayoutParams(lp(8));
        content.addView(in);

        TextView out = text(ctx, "", 19, false, textColor());
        out.setPadding(dp(14), dp(14), dp(14), dp(14));
        out.setBackground(card(cardColor()));
        out.setMinHeight(dp(90));
        out.setTextIsSelectable(true);
        out.setLayoutParams(lp(12));
        content.addView(out);

        in.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                out.setText(BanglishConverter.convert(s.toString()));
            }
        });

        Button copyBtn = button(ctx, "📋 বাংলা লেখা কপি করুন", v -> copy(out.getText().toString()));
        copyBtn.setLayoutParams(lp(12));
        content.addView(copyBtn);

        TextView tips = text(ctx,
                "টিপস:\n• T=ট, t=ত, D=ড, d=দ, N=ণ, n=ন\n• sh=শ, Sh=ষ, s=স, R=ড়\n• kh=খ, gh=ঘ, ch=চ, chh=ছ, jh=ঝ, th=থ, dh=ধ, ph=ফ, bh=ভ\n• a=া, i=ি, I/ee=ী, u=ু, U/oo=ূ, e=ে, O=ো, oi=ৈ, ou=ৌ\n• ng=ং, y=য়, ky=ক্য, ^=ঁ, .=।",
                13, false, grayColor());
        tips.setLayoutParams(lp(16));
        content.addView(tips);
    }

    // ---------------------------------------------------------------- quick reply

    public static String fillTemplate(String template, String name) {
        if (template == null) return "";
        String n = name == null ? "" : name.trim();
        return template.replace("{নাম}", n).replace("{name}", n).replace("{Name}", n);
    }

    private void buildQuickReply(Context ctx) {
        JSONArray arr = loadArray(KEY_TEMPLATES);
        if (arr.length() == 0 && !prefs().getBoolean("templates_seeded", false)) {
            arr.put("আসসালামু আলাইকুম {নাম} ভাই, কিভাবে সাহায্য করতে পারি?");
            arr.put("{নাম} ভাই, পেমেন্ট করুন bKash/Nagad: 01XXXXXXXXX (Send Money). পেমেন্ট করে TrxID দিন।");
            arr.put("ধন্যবাদ {নাম} ভাই, আপনার অর্ডার কনফার্ম হয়েছে ✅");
            saveArray(KEY_TEMPLATES, arr);
            prefs().edit().putBoolean("templates_seeded", true).apply();
        }

        Button add = button(ctx, "➕ নতুন টেমপ্লেট", v -> editTemplate(-1, ""));
        add.setLayoutParams(lp(8));
        content.addView(add);

        TextView hint = text(ctx, "চাপ দিলে কপি হবে (নাম জিজ্ঞেস করবে)। লম্বা চাপে এডিট/ডিলিট।", 13, false, grayColor());
        hint.setLayoutParams(lp(10));
        content.addView(hint);

        for (int i = 0; i < arr.length(); i++) {
            final int index = i;
            final String t = arr.optString(i, "");
            content.addView(itemCard(ctx, t, null,
                    v -> useTemplate(t),
                    v -> {
                        templateMenu(index, t);
                        return true;
                    }));
        }
    }

    private void useTemplate(String template) {
        if (getParentActivity() == null) return;
        if (!template.contains("{নাম}") && !template.contains("{name}") && !template.contains("{Name}")) {
            copy(template);
            return;
        }
        EditText nameIn = input(getParentActivity(), "নাম লিখুন", false);
        nameIn.setPadding(dp(20), dp(10), dp(20), dp(10));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("কার জন্য?");
        b.setView(nameIn);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("কপি", (d, w) -> copy(fillTemplate(template, nameIn.getText().toString())));
        showDialog(b.create());
    }

    private void templateMenu(int index, String t) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("টেমপ্লেট");
        b.setItems(new CharSequence[]{"✏️ এডিট", "🗑 ডিলিট"}, (d, which) -> {
            if (which == 0) {
                editTemplate(index, t);
            } else {
                confirm("ডিলিট করবেন?", t, () -> {
                    saveArray(KEY_TEMPLATES, removeAt(loadArray(KEY_TEMPLATES), index));
                    build(getParentActivity());
                });
            }
        });
        showDialog(b.create());
    }

    private void editTemplate(int index, String current) {
        if (getParentActivity() == null) return;
        EditText e = input(getParentActivity(), "যেমন: আসসালামু আলাইকুম {নাম} ভাই", true);
        e.setText(current);
        e.setPadding(dp(20), dp(10), dp(20), dp(10));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(index < 0 ? "নতুন টেমপ্লেট" : "টেমপ্লেট এডিট");
        b.setView(e);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("সেভ", (d, w) -> {
            String v = e.getText().toString().trim();
            if (v.isEmpty()) return;
            JSONArray arr = loadArray(KEY_TEMPLATES);
            try {
                if (index < 0) arr.put(v); else arr.put(index, v);
            } catch (Exception ignored) {
            }
            saveArray(KEY_TEMPLATES, arr);
            build(getParentActivity());
        });
        showDialog(b.create());
    }

    // ---------------------------------------------------------------- contact notes

    private void buildNotes(Context ctx) {
        Button add = button(ctx, "➕ নতুন নোট", v -> editNote(-1, "", ""));
        add.setLayoutParams(lp(8));
        content.addView(add);

        EditText search = input(ctx, "🔍 নাম দিয়ে খুঁজুন", false);
        search.setLayoutParams(lp(10));
        content.addView(search);

        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        content.addView(list);

        Runnable render = () -> {
            list.removeAllViews();
            JSONArray arr = loadArray(KEY_NOTES);
            String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);
            int shown = 0;
            for (int i = arr.length() - 1; i >= 0; i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                String name = o.optString("name");
                String note = o.optString("note");
                if (!q.isEmpty() && !name.toLowerCase(Locale.ROOT).contains(q) && !note.toLowerCase(Locale.ROOT).contains(q)) continue;
                final int index = i;
                list.addView(itemCard(ctx, "👤 " + name, note + "\n" + o.optString("date"),
                        v -> editNote(index, name, note),
                        v -> {
                            confirm("নোট ডিলিট করবেন?", name, () -> {
                                saveArray(KEY_NOTES, removeAt(loadArray(KEY_NOTES), index));
                                build(getParentActivity());
                            });
                            return true;
                        }));
                shown++;
            }
            if (shown == 0) {
                TextView empty = text(ctx, "কোনো নোট নেই। উপরের বাটন দিয়ে যোগ করুন।", 14, false, grayColor());
                empty.setLayoutParams(lp(16));
                list.addView(empty);
            }
        };
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { render.run(); }
        });
        render.run();
    }

    private void editNote(int index, String name, String note) {
        if (getParentActivity() == null) return;
        LinearLayout box = new LinearLayout(getParentActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText nameIn = input(getParentActivity(), "কন্টাক্টের নাম / username", false);
        nameIn.setText(name);
        EditText noteIn = input(getParentActivity(), "নোট, যেমন: ৫০০ টাকা বাকি, ChatGPT নিয়েছে ১২ তারিখ", true);
        noteIn.setText(note);
        box.addView(nameIn);
        box.addView(noteIn);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(index < 0 ? "নতুন নোট" : "নোট এডিট");
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("সেভ", (d, w) -> {
            String n = nameIn.getText().toString().trim();
            String t = noteIn.getText().toString().trim();
            if (n.isEmpty()) {
                toast("নাম দিন");
                return;
            }
            try {
                JSONObject o = new JSONObject();
                o.put("name", n);
                o.put("note", t);
                o.put("date", new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date()));
                JSONArray arr = loadArray(KEY_NOTES);
                if (index < 0) arr.put(o); else arr.put(index, o);
                saveArray(KEY_NOTES, arr);
            } catch (Exception ignored) {
            }
            build(getParentActivity());
        });
        showDialog(b.create());
    }

    // ---------------------------------------------------------------- ledger

    private static double parseAmount(String s) {
        try {
            return Double.parseDouble(TrxDetector.toEnglishDigits(s).replace(",", "").trim());
        } catch (Exception e) {
            return 0;
        }
    }

    private static String formatAmount(double v) {
        if (v == Math.floor(v)) return String.valueOf((long) v);
        return String.format(Locale.US, "%.2f", v);
    }

    private void buildLedger(Context ctx) {
        JSONArray arr = loadArray(KEY_LEDGER);
        double total = 0;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null) total += o.optDouble("amount", 0);
        }

        LinearLayout summary = new LinearLayout(ctx);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setPadding(dp(16), dp(16), dp(16), dp(16));
        summary.setBackground(card(accent()));
        summary.addView(text(ctx, "মোট বাকি", 14, false, 0xFFFFFFFF));
        summary.addView(text(ctx, "৳ " + formatAmount(total), 28, true, 0xFFFFFFFF));
        summary.addView(text(ctx, arr.length() + " জনের কাছে", 13, false, 0xDDFFFFFF));
        summary.setLayoutParams(lp(8));
        content.addView(summary);

        Button add = button(ctx, "➕ নতুন বাকি যোগ করুন", v -> editLedger(-1, null));
        add.setLayoutParams(lp(12));
        content.addView(add);

        TextView hint = text(ctx, "চাপ দিলে এডিট/পরিশোধ/রিমাইন্ডার কপি।", 13, false, grayColor());
        hint.setLayoutParams(lp(10));
        content.addView(hint);

        for (int i = arr.length() - 1; i >= 0; i--) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final int index = i;
            final JSONObject item = o;
            String sub = (o.optString("note").isEmpty() ? "" : o.optString("note") + "\n") + o.optString("date");
            content.addView(itemCard(ctx, "👤 " + o.optString("name") + "   ৳ " + formatAmount(o.optDouble("amount", 0)), sub,
                    v -> ledgerMenu(index, item), null));
        }
    }

    private void ledgerMenu(int index, JSONObject o) {
        if (getParentActivity() == null) return;
        String name = o.optString("name");
        String amount = formatAmount(o.optDouble("amount", 0));
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(name + " · ৳ " + amount);
        b.setItems(new CharSequence[]{"✅ পরিশোধ হয়েছে (মুছে ফেলুন)", "✏️ এডিট", "📋 রিমাইন্ডার মেসেজ কপি"}, (d, which) -> {
            if (which == 0) {
                confirm("পরিশোধ হয়েছে?", name + " এর ৳ " + amount + " খাতা থেকে মুছে যাবে।", () -> {
                    saveArray(KEY_LEDGER, removeAt(loadArray(KEY_LEDGER), index));
                    build(getParentActivity());
                });
            } else if (which == 1) {
                editLedger(index, o);
            } else {
                copy("আসসালামু আলাইকুম " + name + " ভাই, আপনার ৳ " + amount + " টাকা বাকি আছে। সুবিধামতো পরিশোধ করে দিলে উপকৃত হব। ধন্যবাদ।");
            }
        });
        showDialog(b.create());
    }

    private void editLedger(int index, JSONObject current) {
        if (getParentActivity() == null) return;
        LinearLayout box = new LinearLayout(getParentActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), dp(8));
        EditText nameIn = input(getParentActivity(), "নাম", false);
        EditText amountIn = input(getParentActivity(), "টাকার পরিমাণ", false);
        amountIn.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        EditText noteIn = input(getParentActivity(), "নোট (কী জন্য)", false);
        if (current != null) {
            nameIn.setText(current.optString("name"));
            amountIn.setText(formatAmount(current.optDouble("amount", 0)));
            noteIn.setText(current.optString("note"));
        }
        box.addView(nameIn);
        box.addView(amountIn);
        box.addView(noteIn);
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(index < 0 ? "নতুন বাকি" : "এডিট");
        b.setView(box);
        b.setNegativeButton("বাতিল", null);
        b.setPositiveButton("সেভ", (d, w) -> {
            String n = nameIn.getText().toString().trim();
            double a = parseAmount(amountIn.getText().toString());
            if (n.isEmpty() || a <= 0) {
                toast("নাম আর টাকার পরিমাণ দিন");
                return;
            }
            try {
                JSONObject o = new JSONObject();
                o.put("name", n);
                o.put("amount", a);
                o.put("note", noteIn.getText().toString().trim());
                o.put("date", new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(new Date()));
                JSONArray arr = loadArray(KEY_LEDGER);
                if (index < 0) arr.put(o); else arr.put(index, o);
                saveArray(KEY_LEDGER, arr);
            } catch (Exception ignored) {
            }
            build(getParentActivity());
        });
        showDialog(b.create());
    }

    // ---------------------------------------------------------------- trx detector

    private void buildTrx(Context ctx) {
        EditText in = input(ctx, "bKash/Nagad/Rocket এর SMS বা কাস্টমারের মেসেজ এখানে পেস্ট করুন", true);
        in.setMinLines(5);
        in.setLayoutParams(lp(8));
        content.addView(in);

        TextView out = text(ctx, "", 16, false, textColor());
        out.setPadding(dp(16), dp(14), dp(16), dp(14));
        out.setBackground(card(cardColor()));
        out.setTextIsSelectable(true);
        out.setLayoutParams(lp(12));

        Button detect = button(ctx, "🔎 খুঁজে বের করুন", v -> {
            TrxDetector.Result r = TrxDetector.detect(in.getText().toString());
            out.setText(r.isEmpty() ? "কিছু পাওয়া যায়নি। পুরো SMS পেস্ট করুন।" : r.toBangla());
        });
        detect.setLayoutParams(lp(12));
        content.addView(detect);
        content.addView(out);

        Button copyBtn = button(ctx, "📋 ফলাফল কপি", v -> copy(out.getText().toString()));
        copyBtn.setLayoutParams(lp(12));
        content.addView(copyBtn);
    }

    // ---------------------------------------------------------------- shared helpers (batch 2)

    private static String timeAgo(int unixDate) {
        long diff = System.currentTimeMillis() / 1000L - unixDate;
        if (diff < 60) return "এইমাত্র";
        if (diff < 3600) return (diff / 60) + " মিনিট আগে";
        if (diff < 86400) return (diff / 3600) + " ঘণ্টা আগে";
        return (diff / 86400) + " দিন আগে";
    }

    private static String shorten(CharSequence cs, int max) {
        if (cs == null) return "";
        String s = cs.toString().replace('\n', ' ').trim();
        return s.length() > max ? s.substring(0, max) + "…" : s;
    }

    private String dialogName(long dialogId) {
        MessagesController mc = MessagesController.getInstance(currentAccount);
        if (DialogObject.isUserDialog(dialogId)) {
            TLRPC.User u = mc.getUser(dialogId);
            return u == null ? String.valueOf(dialogId) : ContactsController.formatName(u.first_name, u.last_name);
        }
        TLRPC.Chat c = mc.getChat(-dialogId);
        return c == null ? String.valueOf(dialogId) : c.title;
    }

    private void openChat(long dialogId, int messageId) {
        Bundle args = new Bundle();
        if (DialogObject.isUserDialog(dialogId)) {
            args.putLong("user_id", dialogId);
        } else {
            args.putLong("chat_id", -dialogId);
        }
        if (messageId > 0) args.putInt("message_id", messageId);
        presentFragment(new ChatActivity(args));
    }

    private TextView emptyText(Context ctx, String s) {
        TextView t = text(ctx, s, 14, false, grayColor());
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), dp(24), dp(8), dp(24));
        return t;
    }

    // ---------------------------------------------------------------- smart inbox

    private boolean inboxShowFollowUp = false;

    private void buildInbox(Context ctx) {
        LinearLayout tabs = new LinearLayout(ctx);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        Button unanswered = button(ctx, "💬 উত্তর বাকি", v -> {
            inboxShowFollowUp = false;
            build(ctx);
        });
        Button follow = button(ctx, "⏰ ফলো-আপ দরকার", v -> {
            inboxShowFollowUp = true;
            build(ctx);
        });
        unanswered.setAlpha(inboxShowFollowUp ? 0.5f : 1f);
        follow.setAlpha(inboxShowFollowUp ? 1f : 0.5f);
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half.rightMargin = dp(6);
        tabs.addView(unanswered, half);
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        half2.leftMargin = dp(6);
        tabs.addView(follow, half2);
        tabs.setLayoutParams(lp(8));
        content.addView(tabs);

        TextView hint = text(ctx, inboxShowFollowUp
                        ? "আপনি শেষ মেসেজ দিয়েছেন কিন্তু ২ দিনের বেশি কোনো উত্তর আসেনি। চাপ দিলে চ্যাট খুলবে।"
                        : "এদের শেষ মেসেজের উত্তর আপনি এখনো দেননি। চাপ দিলে চ্যাট খুলবে।",
                13, false, grayColor());
        hint.setLayoutParams(lp(10));
        content.addView(hint);

        MessagesController mc = MessagesController.getInstance(currentAccount);
        long self = UserConfig.getInstance(currentAccount).getClientUserId();
        long now = System.currentTimeMillis() / 1000L;
        ArrayList<MessageObject> found = new ArrayList<>();
        ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>(mc.getAllDialogs());
        for (TLRPC.Dialog d : dialogs) {
            if (d == null || !DialogObject.isUserDialog(d.id) || d.id == self || d.id == 777000L) continue;
            TLRPC.User u = mc.getUser(d.id);
            if (u == null || u.bot || u.deleted) continue;
            ArrayList<MessageObject> msgs = mc.dialogMessage.get(d.id);
            if (msgs == null || msgs.isEmpty() || msgs.get(0) == null || msgs.get(0).messageOwner == null) continue;
            MessageObject m = msgs.get(0);
            boolean out = m.isOut();
            if (!inboxShowFollowUp && !out) {
                found.add(m);
            } else if (inboxShowFollowUp && out && now - m.messageOwner.date > 2 * 86400L && now - m.messageOwner.date < 60 * 86400L) {
                found.add(m);
            }
        }
        Collections.sort(found, (a, b) -> Integer.compare(b.messageOwner.date, a.messageOwner.date));

        TextView count = text(ctx, found.size() + " টি চ্যাট", 14, true, accent());
        count.setLayoutParams(lp(10));
        content.addView(count);

        if (found.isEmpty()) {
            content.addView(emptyText(ctx, inboxShowFollowUp ? "কোনো ফলো-আপ বাকি নেই 🎉" : "সব মেসেজের উত্তর দেওয়া হয়েছে 🎉"));
            return;
        }
        for (MessageObject m : found) {
            final long did = m.getDialogId();
            String snippet = (inboxShowFollowUp ? "আপনি: " : "") + shorten(m.messageText, 90);
            content.addView(itemCard(ctx, "👤 " + dialogName(did) + "  ·  " + timeAgo(m.messageOwner.date), snippet,
                    v -> openChat(did, 0), null));
        }
    }

    // ---------------------------------------------------------------- link vault

    private static final Pattern URL_PATTERN = Pattern.compile("(https?://[^\\s<>\"']+|(?:www\\.|t\\.me/|youtu\\.be/)[^\\s<>\"']+)");
    private String linkFilter = "সব";

    private static String categoryOf(String url) {
        String u = url.toLowerCase(Locale.ROOT);
        if (u.contains("youtube.com") || u.contains("youtu.be")) return "YouTube";
        if (u.contains("drive.google") || u.contains("docs.google")) return "Drive";
        if (u.contains("facebook.com") || u.contains("fb.watch") || u.contains("fb.com")) return "Facebook";
        if (u.contains("t.me/") || u.contains("telegram.")) return "Telegram";
        return "অন্যান্য";
    }

    private static final class LinkItem {
        String url;
        String category;
        long dialogId;
        int messageId;
        int date;
    }

    private final ArrayList<LinkItem> linkItems = new ArrayList<>();
    private boolean linksLoading = false;
    private boolean linksLoaded = false;

    private void buildLinks(Context ctx) {
        LinearLayout chips = new LinearLayout(ctx);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        String[] cats = {"সব", "YouTube", "Drive", "Facebook", "Telegram", "অন্যান্য"};
        for (String c : cats) {
            Button b = button(ctx, c, v -> {
                linkFilter = c;
                build(ctx);
            });
            b.setAlpha(c.equals(linkFilter) ? 1f : 0.5f);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(8);
            chips.addView(b, p);
        }
        hs.addView(chips);
        hs.setLayoutParams(lp(8));
        content.addView(hs);

        Button reload = button(ctx, "🔄 আবার লোড করুন", v -> {
            linksLoaded = false;
            loadLinks(ctx);
        });
        reload.setLayoutParams(lp(10));
        content.addView(reload);

        if (!linksLoaded) {
            content.addView(emptyText(ctx, "লিংক খোঁজা হচ্ছে… ⏳"));
            loadLinks(ctx);
            return;
        }
        int shown = 0;
        for (LinkItem it : linkItems) {
            if (!"সব".equals(linkFilter) && !linkFilter.equals(it.category)) continue;
            final LinkItem item = it;
            content.addView(itemCard(ctx, item.url, item.category + " · " + dialogName(item.dialogId) + " · " + timeAgo(item.date),
                    v -> {
                        try {
                            Browser.openUrl(getParentActivity(), item.url.startsWith("http") ? item.url : "https://" + item.url);
                        } catch (Exception ignored) {
                        }
                    },
                    v -> {
                        linkMenu(item);
                        return true;
                    }));
            shown++;
        }
        if (shown == 0) content.addView(emptyText(ctx, "এই ক্যাটাগরিতে কোনো লিংক নেই।"));
        else {
            TextView tip = text(ctx, "লম্বা চাপ দিলে কপি বা চ্যাটে যাওয়ার অপশন।", 12.5f, false, grayColor());
            tip.setLayoutParams(lp(12));
            content.addView(tip);
        }
    }

    private void linkMenu(LinkItem item) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle(item.category);
        b.setItems(new CharSequence[]{"📋 লিংক কপি", "💬 যে চ্যাটে এসেছে সেখানে যান"}, (d, which) -> {
            if (which == 0) copy(item.url);
            else openChat(item.dialogId, item.messageId);
        });
        showDialog(b.create());
    }

    private void loadLinks(Context ctx) {
        if (linksLoading) return;
        linksLoading = true;
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterUrl();
        req.limit = 100;
        req.offset_rate = 0;
        req.offset_id = 0;
        req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            linksLoading = false;
            linksLoaded = true;
            linkItems.clear();
            if (res != null) {
                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                LinkedHashSet<String> seen = new LinkedHashSet<>();
                for (TLRPC.Message m : res.messages) {
                    if (m == null) continue;
                    ArrayList<String> urls = new ArrayList<>();
                    if (m.entities != null) {
                        for (TLRPC.MessageEntity e : m.entities) {
                            if (e != null && e.url != null && !e.url.isEmpty()) urls.add(e.url);
                        }
                    }
                    if (m.message != null) {
                        Matcher mt = URL_PATTERN.matcher(m.message);
                        while (mt.find()) urls.add(mt.group(1));
                    }
                    for (String u : urls) {
                        String key = u.toLowerCase(Locale.ROOT);
                        if (!seen.add(key)) continue;
                        LinkItem li = new LinkItem();
                        li.url = u;
                        li.category = categoryOf(u);
                        li.dialogId = MessageObject.getPeerId(m.peer_id);
                        li.messageId = m.id;
                        li.date = m.date;
                        linkItems.add(li);
                    }
                }
            }
            if (fragmentView != null && mode == MODE_LINKS) build(ctx);
        }));
    }

    // ---------------------------------------------------------------- keyword / offer search

    private static final String KEY_KEYWORDS = "saved_keywords";

    private void buildKeywords(Context ctx) {
        EditText q = input(ctx, "যেমন: ChatGPT Plus, Canva Pro, Netflix", false);
        q.setLayoutParams(lp(8));
        content.addView(q);

        LinearLayout results = new LinearLayout(ctx);
        results.setOrientation(LinearLayout.VERTICAL);

        Button search = button(ctx, "🔎 সব চ্যানেল আর গ্রুপে খুঁজুন", v -> runKeywordSearch(ctx, q.getText().toString().trim(), results));
        search.setLayoutParams(lp(10));
        content.addView(search);

        Button save = button(ctx, "⭐ এই শব্দটা সেভ করুন", v -> {
            String k = q.getText().toString().trim();
            if (k.isEmpty()) return;
            JSONArray arr = loadArray(KEY_KEYWORDS);
            arr.put(k);
            saveArray(KEY_KEYWORDS, arr);
            toast("সেভ হয়েছে");
            build(ctx);
        });
        save.setLayoutParams(lp(8));
        content.addView(save);

        JSONArray saved = loadArray(KEY_KEYWORDS);
        if (saved.length() > 0) {
            TextView h = text(ctx, "সেভ করা শব্দ (চাপ দিলে খুঁজবে, লম্বা চাপে মুছবে):", 13, false, grayColor());
            h.setLayoutParams(lp(14));
            content.addView(h);
            for (int i = 0; i < saved.length(); i++) {
                final int index = i;
                final String k = saved.optString(i);
                content.addView(itemCard(ctx, "⭐ " + k, null,
                        v -> {
                            q.setText(k);
                            runKeywordSearch(ctx, k, results);
                        },
                        v -> {
                            confirm("মুছে ফেলবেন?", k, () -> {
                                saveArray(KEY_KEYWORDS, removeAt(loadArray(KEY_KEYWORDS), index));
                                build(ctx);
                            });
                            return true;
                        }));
            }
        }
        boolean alertOn = KeywordAlertWorker.isEnabled();
        View alertCard = itemCard(ctx, "🔔 নতুন পোস্টে নোটিফিকেশন: " + (alertOn ? "চালু ✅" : "বন্ধ"),
                "সেভ করা শব্দগুলো নিয়ে কেউ নতুন পোস্ট দিলে প্রতি ৩০ মিনিটে চেক করে নোটিফিকেশন দেবে। চাপ দিয়ে চালু/বন্ধ করুন",
                v -> {
                    if (!KeywordAlertWorker.isEnabled() && loadArray(KEY_KEYWORDS).length() == 0) {
                        toast("আগে অন্তত একটা শব্দ সেভ করুন");
                        return;
                    }
                    KeywordAlertWorker.setEnabled(!KeywordAlertWorker.isEnabled());
                    toast(KeywordAlertWorker.isEnabled() ? "নোটিফিকেশন চালু ✅" : "নোটিফিকেশন বন্ধ");
                    build(ctx);
                }, null);
        content.addView(alertCard);
        results.setLayoutParams(lp(12));
        content.addView(results);
    }

    private void runKeywordSearch(Context ctx, String query, LinearLayout results) {
        if (query.isEmpty()) {
            toast("কিছু লিখুন");
            return;
        }
        results.removeAllViews();
        results.addView(emptyText(ctx, "খোঁজা হচ্ছে… ⏳"));
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.q = query;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = 50;
        req.offset_rate = 0;
        req.offset_id = 0;
        req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            results.removeAllViews();
            if (res == null || res.messages.isEmpty()) {
                results.addView(emptyText(ctx, "কিছু পাওয়া যায়নি।"));
                return;
            }
            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
            TextView c = text(ctx, res.messages.size() + " টি মেসেজ পাওয়া গেছে", 14, true, accent());
            results.addView(c);
            for (TLRPC.Message m : res.messages) {
                if (m == null) continue;
                final long did = MessageObject.getPeerId(m.peer_id);
                final int mid = m.id;
                results.addView(itemCard(ctx, dialogName(did) + "  ·  " + timeAgo(m.date), shorten(m.message, 160),
                        v -> openChat(did, mid), null));
            }
        }));
    }

    // ---------------------------------------------------------------- youtube auto post

    private void buildYouTube(Context ctx) {
        SharedPreferences p = YouTubeAutoPostWorker.prefs();
        boolean enabled = p.getBoolean(YouTubeAutoPostWorker.KEY_ENABLED, false);
        String channelId = p.getString(YouTubeAutoPostWorker.KEY_CHANNEL_ID, "");
        String targetName = p.getString(YouTubeAutoPostWorker.KEY_TARGET_NAME, "");

        LinearLayout status = new LinearLayout(ctx);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackground(card(enabled ? 0xFF16A34A : 0xFF64748B));
        status.addView(text(ctx, enabled ? "✅ অটো পোস্ট চালু" : "⏸ অটো পোস্ট বন্ধ", 18, true, 0xFFFFFFFF));
        String last = p.getString(YouTubeAutoPostWorker.KEY_LAST_POSTED_TITLE, "");
        status.addView(text(ctx, "প্রতি ১৫ মিনিটে চেক করবে" + (last.isEmpty() ? "" : "\nশেষ পোস্ট: " + last), 13, false, 0xEEFFFFFF));
        status.setLayoutParams(lp(8));
        content.addView(status);

        // Step 1: YouTube channel
        TextView s1 = text(ctx, "১. আপনার YouTube চ্যানেল", 15, true, textColor());
        s1.setLayoutParams(lp(16));
        content.addView(s1);
        EditText ch = input(ctx, "চ্যানেলের লিংক বা @handle, যেমন @Freehelpnow786", false);
        ch.setText(channelId);
        content.addView(ch);

        // Step 2: Telegram target
        TextView s2 = text(ctx, "২. কোন Telegram চ্যানেল/গ্রুপে পোস্ট হবে", 15, true, textColor());
        s2.setLayoutParams(lp(16));
        content.addView(s2);
        Button pick = button(ctx, targetName.isEmpty() ? "📢 চ্যানেল বেছে নিন" : "📢 " + targetName + " (বদলাতে চাপুন)", v -> pickTargetChannel(ctx));
        pick.setLayoutParams(lp(6));
        content.addView(pick);

        // Step 3: template
        TextView s3 = text(ctx, "৩. পোস্টের লেখা ({title} = ভিডিওর নাম, {link} = লিংক)", 15, true, textColor());
        s3.setLayoutParams(lp(16));
        content.addView(s3);
        EditText tpl = input(ctx, "", true);
        tpl.setText(p.getString(YouTubeAutoPostWorker.KEY_TEMPLATE, YouTubeFeed.DEFAULT_TEMPLATE));
        content.addView(tpl);

        Button saveBtn = button(ctx, enabled ? "💾 সেভ করুন" : "✅ সেভ করে চালু করুন", v -> {
            String input = ch.getText().toString().trim();
            String template = tpl.getText().toString();
            if (input.isEmpty()) {
                toast("YouTube চ্যানেলের লিংক দিন");
                return;
            }
            if (YouTubeAutoPostWorker.prefs().getLong(YouTubeAutoPostWorker.KEY_TARGET_DIALOG, 0) == 0) {
                toast("আগে Telegram চ্যানেল বেছে নিন");
                return;
            }
            toast("YouTube চ্যানেল খোঁজা হচ্ছে…");
            new Thread(() -> {
                String id = null;
                try {
                    id = YouTubeFeed.resolveChannelId(input);
                } catch (Throwable ignored) {
                }
                final String resolved = id;
                AndroidUtilities.runOnUIThread(() -> {
                    if (resolved == null) {
                        toast("YouTube চ্যানেল পাওয়া যায়নি। চ্যানেলের পুরো লিংক দিন।");
                        return;
                    }
                    SharedPreferences.Editor e = YouTubeAutoPostWorker.prefs().edit();
                    if (!resolved.equals(YouTubeAutoPostWorker.prefs().getString(YouTubeAutoPostWorker.KEY_CHANNEL_ID, ""))) {
                        e.putString(YouTubeAutoPostWorker.KEY_LAST_VIDEO, "");
                    }
                    e.putString(YouTubeAutoPostWorker.KEY_CHANNEL_ID, resolved)
                            .putString(YouTubeAutoPostWorker.KEY_TEMPLATE, template)
                            .putBoolean(YouTubeAutoPostWorker.KEY_ENABLED, true)
                            .apply();
                    YouTubeAutoPostWorker.schedule(ApplicationLoader.applicationContext);
                    new Thread(() -> YouTubeAutoPostWorker.checkNow(false)).start();
                    toast("চালু হয়েছে ✅ এখন থেকে নতুন ভিডিও এলে পোস্ট হবে");
                    if (getParentActivity() != null) build(getParentActivity());
                });
            }).start();
        });
        saveBtn.setLayoutParams(lp(18));
        content.addView(saveBtn);

        Button test = button(ctx, "🧪 টেস্ট: সর্বশেষ ভিডিওটা এখনই পোস্ট করুন", v -> confirm("টেস্ট পোস্ট",
                "আপনার চ্যানেলের সর্বশেষ ভিডিওটা এখনই " + (targetName.isEmpty() ? "বেছে নেওয়া চ্যানেলে" : targetName + " এ") + " পোস্ট হবে।", () -> new Thread(() -> {
                    int r = YouTubeAutoPostWorker.checkNow(true);
                    AndroidUtilities.runOnUIThread(() -> toast(r == YouTubeAutoPostWorker.RESULT_POSTED ? "পোস্ট হয়েছে ✅"
                            : r == YouTubeAutoPostWorker.RESULT_NOT_SET_UP ? "আগে সেভ করে চালু করুন" : "ভিডিও পাওয়া যায়নি বা ইন্টারনেট সমস্যা"));
                }).start()));
        test.setLayoutParams(lp(10));
        content.addView(test);

        if (enabled) {
            Button off = button(ctx, "⏸ অটো পোস্ট বন্ধ করুন", v -> {
                YouTubeAutoPostWorker.prefs().edit().putBoolean(YouTubeAutoPostWorker.KEY_ENABLED, false).apply();
                YouTubeAutoPostWorker.cancel(ApplicationLoader.applicationContext);
                toast("বন্ধ হয়েছে");
                build(ctx);
            });
            off.setLayoutParams(lp(10));
            content.addView(off);
        }

        TextView note = text(ctx, "নোট: ফোনের ব্যাটারি সেভার Bartalap-কে বন্ধ করে দিলে পোস্ট দেরিতে হতে পারে। Settings → Apps → Bartalap → Battery → Unrestricted করে দিন।",
                12.5f, false, grayColor());
        note.setLayoutParams(lp(16));
        content.addView(note);
    }

    private void pickTargetChannel(Context ctx) {
        if (getParentActivity() == null) return;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<CharSequence> names = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null || !DialogObject.isChatDialog(d.id)) continue;
            TLRPC.Chat c = mc.getChat(-d.id);
            if (c == null) continue;
            boolean canPost = ChatObject.isChannelAndNotMegaGroup(c) ? ChatObject.canPost(c) : ChatObject.canSendMessages(c);
            if (!canPost) continue;
            ids.add(d.id);
            names.add((ChatObject.isChannelAndNotMegaGroup(c) ? "📢 " : "👥 ") + c.title);
        }
        if (ids.isEmpty()) {
            toast("পোস্ট করার মতো কোনো চ্যানেল/গ্রুপ পাওয়া যায়নি");
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("কোথায় পোস্ট হবে?");
        b.setItems(names.toArray(new CharSequence[0]), (dlg, which) -> {
            YouTubeAutoPostWorker.prefs().edit()
                    .putLong(YouTubeAutoPostWorker.KEY_TARGET_DIALOG, ids.get(which))
                    .putString(YouTubeAutoPostWorker.KEY_TARGET_NAME, names.get(which).toString())
                    .putInt(YouTubeAutoPostWorker.KEY_TARGET_ACCOUNT, currentAccount)
                    .apply();
            build(ctx);
        });
        showDialog(b.create());
    }


    // ---------------------------------------------------------------- best time to post

    private void buildBestTime(Context ctx) {
        TextView intro = text(ctx, "আপনার চ্যানেলের শেষ ১০০টা পোস্টের ভিউ দেখে বের করবে কোন সময়ে পোস্ট করলে বেশি ভিউ আসে।", 14, false, grayColor());
        intro.setLayoutParams(lp(8));
        content.addView(intro);
        Button pick = button(ctx, "📢 চ্যানেল বেছে নিন", v -> pickChannelForStats(ctx));
        pick.setLayoutParams(lp(12));
        content.addView(pick);
        statsBox = new LinearLayout(ctx);
        statsBox.setOrientation(LinearLayout.VERTICAL);
        statsBox.setLayoutParams(lp(12));
        content.addView(statsBox);
    }

    private LinearLayout statsBox;

    private void pickChannelForStats(Context ctx) {
        if (getParentActivity() == null) return;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<CharSequence> names = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null || !DialogObject.isChatDialog(d.id)) continue;
            TLRPC.Chat c = mc.getChat(-d.id);
            if (c == null || !ChatObject.isChannelAndNotMegaGroup(c)) continue;
            ids.add(d.id);
            names.add("📢 " + c.title);
        }
        if (ids.isEmpty()) {
            toast("কোনো চ্যানেল পাওয়া যায়নি");
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("কোন চ্যানেল?");
        b.setItems(names.toArray(new CharSequence[0]), (dlg, which) -> loadChannelStats(ctx, ids.get(which), names.get(which).toString()));
        showDialog(b.create());
    }

    private static final String[] WEEKDAYS = {"রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার", "শনিবার"};

    private static String hourLabel(int h) {
        String part = h < 5 ? "রাত" : h < 12 ? "সকাল" : h < 15 ? "দুপুর" : h < 18 ? "বিকাল" : h < 20 ? "সন্ধ্যা" : "রাত";
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return part + " " + h12 + "টা";
    }

    private void loadChannelStats(Context ctx, long dialogId, String name) {
        statsBox.removeAllViews();
        statsBox.addView(emptyText(ctx, name + " এর পোস্ট দেখা হচ্ছে… ⏳"));
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.limit = 100;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            statsBox.removeAllViews();
            if (!(response instanceof TLRPC.messages_Messages)) {
                statsBox.addView(emptyText(ctx, "পোস্ট লোড করা যায়নি।"));
                return;
            }
            TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
            long[] hourViews = new long[24];
            int[] hourCount = new int[24];
            long[] dayViews = new long[7];
            int[] dayCount = new int[7];
            int posts = 0;
            long total = 0;
            java.util.Calendar cal = java.util.Calendar.getInstance();
            for (TLRPC.Message m : res.messages) {
                if (m == null || m.views <= 0) continue;
                cal.setTimeInMillis(m.date * 1000L);
                int h = cal.get(java.util.Calendar.HOUR_OF_DAY);
                int dw = cal.get(java.util.Calendar.DAY_OF_WEEK) - 1;
                hourViews[h] += m.views;
                hourCount[h]++;
                dayViews[dw] += m.views;
                dayCount[dw]++;
                posts++;
                total += m.views;
            }
            if (posts < 3) {
                statsBox.addView(emptyText(ctx, "হিসাব করার মতো যথেষ্ট পোস্ট নেই।"));
                return;
            }
            int bestH = -1;
            double bestAvg = -1;
            for (int h = 0; h < 24; h++) {
                if (hourCount[h] == 0) continue;
                double avg = hourViews[h] / (double) hourCount[h];
                if (avg > bestAvg) {
                    bestAvg = avg;
                    bestH = h;
                }
            }
            int bestD = -1;
            double bestDAvg = -1;
            for (int d = 0; d < 7; d++) {
                if (dayCount[d] == 0) continue;
                double avg = dayViews[d] / (double) dayCount[d];
                if (avg > bestDAvg) {
                    bestDAvg = avg;
                    bestD = d;
                }
            }
            LinearLayout best = new LinearLayout(ctx);
            best.setOrientation(LinearLayout.VERTICAL);
            best.setPadding(dp(16), dp(16), dp(16), dp(16));
            best.setBackground(card(accent()));
            best.addView(text(ctx, "🏆 সেরা সময়: " + hourLabel(bestH), 20, true, 0xFFFFFFFF));
            if (bestD >= 0) best.addView(text(ctx, "📅 সেরা দিন: " + WEEKDAYS[bestD], 16, false, 0xFFFFFFFF));
            best.addView(text(ctx, posts + " টা পোস্ট, গড় ভিউ " + (total / posts), 13, false, 0xEEFFFFFF));
            statsBox.addView(best);

            TextView h2 = text(ctx, "ঘণ্টা অনুযায়ী গড় ভিউ:", 14, true, textColor());
            h2.setLayoutParams(lp(14));
            statsBox.addView(h2);
            double max = bestAvg <= 0 ? 1 : bestAvg;
            for (int h = 0; h < 24; h++) {
                if (hourCount[h] == 0) continue;
                double avg = hourViews[h] / (double) hourCount[h];
                int bars = (int) Math.round(avg / max * 12);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < bars; i++) sb.append('█');
                TextView row = text(ctx, String.format(Locale.US, "%-12s %s %d", hourLabel(h), sb.toString(), Math.round(avg)), 13, h == bestH, h == bestH ? accent() : textColor());
                row.setTypeface(h == bestH ? Typeface.DEFAULT_BOLD : Typeface.MONOSPACE);
                statsBox.addView(row);
            }
        }));
    }


    // ---------------------------------------------------------------- cross post (one post, many chats)

    private final ArrayList<Long> crossTargets = new ArrayList<>();

    private void buildCrossPost(Context ctx) {
        TextView intro = text(ctx, "একবার লিখুন, তারপর যতগুলো চ্যানেল/গ্রুপ চান বেছে নিন। এক চাপে সবগুলোতে পোস্ট হবে।", 14, false, grayColor());
        intro.setLayoutParams(lp(8));
        content.addView(intro);
        EditText msg = input(ctx, "পোস্টের লেখা লিখুন…", true);
        msg.setMinLines(5);
        msg.setText(prefs().getString("crosspost_draft", ""));
        msg.setLayoutParams(lp(10));
        content.addView(msg);
        Button aiWrite = button(ctx, "🤖 AI দিয়ে পোস্ট লিখুন", v -> {
            if (!AiClient.isConfigured()) {
                BartalapChatTools.aiSettings(getParentActivity(), null);
                return;
            }
            String topic = msg.getText().toString().trim();
            if (topic.isEmpty()) {
                toast("উপরের ঘরে বিষয়টা লিখুন, যেমন: ChatGPT Plus অফার ৫০০ টাকা");
                return;
            }
            toast("AI লিখছে… ⏳");
            new Thread(() -> {
                String r;
                try {
                    r = AiClient.ask("You write catchy Telegram channel posts in natural Bengali for a Bangladeshi audience. Use a few relevant emoji, short lines, a clear call to action. Output only the post.",
                            "এই বিষয়ে একটা পোস্ট লিখুন:\n" + topic);
                } catch (Throwable e) {
                    r = null;
                }
                final String out = r;
                AndroidUtilities.runOnUIThread(() -> {
                    if (out == null) toast("AI লিখতে পারেনি, API Key দেখুন");
                    else msg.setText(out);
                });
            }).start();
        });
        aiWrite.setLayoutParams(lp(8));
        content.addView(aiWrite);

        MessagesController mc = MessagesController.getInstance(currentAccount);
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null || !DialogObject.isChatDialog(d.id)) continue;
            TLRPC.Chat c = mc.getChat(-d.id);
            if (c == null) continue;
            boolean can = ChatObject.isChannelAndNotMegaGroup(c) ? ChatObject.canPost(c) : ChatObject.canSendMessages(c);
            if (!can) continue;
            ids.add(d.id);
            names.add((ChatObject.isChannelAndNotMegaGroup(c) ? "📢 " : "👥 ") + c.title);
        }
        TextView pickTitle = text(ctx, "কোথায় কোথায় পোস্ট হবে (চাপ দিয়ে বেছে নিন):", 14, true, textColor());
        pickTitle.setLayoutParams(lp(14));
        content.addView(pickTitle);
        if (ids.isEmpty()) {
            content.addView(emptyText(ctx, "পোস্ট করার মতো কোনো চ্যানেল/গ্রুপ নেই।"));
            return;
        }
        for (int i = 0; i < ids.size(); i++) {
            final long id = ids.get(i);
            final String name = names.get(i);
            TextView row = text(ctx, (crossTargets.contains(id) ? "☑️ " : "⬜ ") + name, 16, false, textColor());
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(card(cardColor()));
            row.setLayoutParams(lp(6));
            row.setOnClickListener(v -> {
                if (crossTargets.contains(id)) crossTargets.remove(Long.valueOf(id)); else crossTargets.add(id);
                ((TextView) v).setText((crossTargets.contains(id) ? "☑️ " : "⬜ ") + name);
            });
            content.addView(row);
        }
        Button send = button(ctx, "🚀 সব জায়গায় পোস্ট করুন", v -> {
            String t = msg.getText().toString().trim();
            if (t.isEmpty()) {
                toast("লেখা দিন");
                return;
            }
            if (crossTargets.isEmpty()) {
                toast("অন্তত একটা চ্যানেল/গ্রুপ বেছে নিন");
                return;
            }
            prefs().edit().putString("crosspost_draft", t).apply();
            confirm("পোস্ট করবেন?", crossTargets.size() + " টা জায়গায় এই লেখাটা পোস্ট হবে।", () -> {
                for (Long target : new ArrayList<>(crossTargets)) {
                    org.telegram.messenger.SendMessagesHelper.getInstance(currentAccount)
                            .sendMessage(org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(t, target));
                }
                toast(crossTargets.size() + " টা জায়গায় পোস্ট হয়েছে ✅");
                prefs().edit().putString("crosspost_draft", "").apply();
            });
        });
        send.setLayoutParams(lp(16));
        content.addView(send);
    }

    // ---------------------------------------------------------------- channel tracker (competitors)

    private static final String KEY_TRACKED = "tracked_channels";

    private void buildTracker(Context ctx) {
        TextView intro = text(ctx, "যেকোনো পাবলিক চ্যানেলের (নিজের বা প্রতিযোগীর) সাবস্ক্রাইবার কত বাড়ছে বা কমছে দেখুন। যতবার খুলবেন, তত হিসাব জমবে।", 14, false, grayColor());
        intro.setLayoutParams(lp(8));
        content.addView(intro);
        EditText un = input(ctx, "@username বা t.me/username", false);
        un.setLayoutParams(lp(10));
        content.addView(un);
        Button add = button(ctx, "➕ ট্র্যাকিং-এ যোগ করুন", v -> {
            String u = un.getText().toString().trim().replace("https://", "").replace("http://", "").replace("t.me/", "").replace("@", "");
            if (u.contains("/")) u = u.substring(0, u.indexOf('/'));
            if (u.isEmpty()) {
                toast("username দিন");
                return;
            }
            JSONArray arr = loadArray(KEY_TRACKED);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null && u.equalsIgnoreCase(o.optString("username"))) {
                    toast("আগেই যোগ করা আছে");
                    return;
                }
            }
            try {
                arr.put(new JSONObject().put("username", u).put("history", new JSONArray()));
            } catch (Exception ignored) {
            }
            saveArray(KEY_TRACKED, arr);
            build(ctx);
        });
        add.setLayoutParams(lp(8));
        content.addView(add);

        JSONArray arr = loadArray(KEY_TRACKED);
        if (arr.length() == 0) {
            content.addView(emptyText(ctx, "এখনো কোনো চ্যানেল যোগ করা হয়নি।"));
            return;
        }
        Button refresh = button(ctx, "🔄 সবগুলোর এখনকার সংখ্যা আনুন", v -> refreshTracked(ctx, 0));
        refresh.setLayoutParams(lp(12));
        content.addView(refresh);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            final int index = i;
            String u = o.optString("username");
            JSONArray h = o.optJSONArray("history");
            String sub;
            if (h == null || h.length() == 0) {
                sub = "এখনো হিসাব নেওয়া হয়নি, রিফ্রেশ চাপুন";
            } else {
                JSONObject first = h.optJSONObject(0);
                JSONObject last = h.optJSONObject(h.length() - 1);
                int now = last.optInt("count");
                int diff = now - first.optInt("count");
                long days = Math.max(1, (last.optLong("time") - first.optLong("time")) / 86400000L);
                sub = "👥 " + now + " সাবস্ক্রাইবার\n" + (diff >= 0 ? "📈 +" : "📉 ") + diff + " (" + days + " দিনে), শেষ চেক: " + last.optString("date");
            }
            content.addView(itemCard(ctx, "📢 " + o.optString("title", "@" + u), sub,
                    v -> {
                        try {
                            Browser.openUrl(getParentActivity(), "https://t.me/" + u);
                        } catch (Exception ignored) {
                        }
                    },
                    v -> {
                        confirm("ট্র্যাকিং থেকে সরাবেন?", "@" + u, () -> {
                            saveArray(KEY_TRACKED, removeAt(loadArray(KEY_TRACKED), index));
                            build(ctx);
                        });
                        return true;
                    }));
        }
        if (!trackerAutoRefreshed) {
            trackerAutoRefreshed = true;
            refreshTracked(ctx, 0);
        }
    }

    private boolean trackerAutoRefreshed = false;

    private void refreshTracked(Context ctx, int index) {
        JSONArray arr = loadArray(KEY_TRACKED);
        if (index >= arr.length()) {
            if (fragmentView != null && mode == MODE_TRACKER) build(ctx);
            return;
        }
        JSONObject o = arr.optJSONObject(index);
        if (o == null) {
            refreshTracked(ctx, index + 1);
            return;
        }
        String u = o.optString("username");
        TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
        req.username = u;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_contacts_resolvedPeer) || ((TLRPC.TL_contacts_resolvedPeer) response).chats.isEmpty()) {
                refreshTracked(ctx, index + 1);
                return;
            }
            TLRPC.TL_contacts_resolvedPeer rp = (TLRPC.TL_contacts_resolvedPeer) response;
            MessagesController.getInstance(currentAccount).putChats(rp.chats, false);
            MessagesController.getInstance(currentAccount).putUsers(rp.users, false);
            TLRPC.Chat chat = rp.chats.get(0);
            TLRPC.TL_channels_getFullChannel full = new TLRPC.TL_channels_getFullChannel();
            full.channel = MessagesController.getInputChannel(chat);
            ConnectionsManager.getInstance(currentAccount).sendRequest(full, (r2, e2) -> AndroidUtilities.runOnUIThread(() -> {
                int count = chat.participants_count;
                if (r2 instanceof TLRPC.TL_messages_chatFull && ((TLRPC.TL_messages_chatFull) r2).full_chat != null) {
                    count = ((TLRPC.TL_messages_chatFull) r2).full_chat.participants_count;
                }
                if (count > 0) {
                    try {
                        JSONArray all = loadArray(KEY_TRACKED);
                        JSONObject cur = all.optJSONObject(index);
                        if (cur != null) {
                            cur.put("title", chat.title);
                            JSONArray h = cur.optJSONArray("history");
                            if (h == null) h = new JSONArray();
                            h.put(new JSONObject().put("count", count).put("time", System.currentTimeMillis())
                                    .put("date", new SimpleDateFormat("dd/MM HH:mm", Locale.US).format(new Date())));
                            while (h.length() > 200) h.remove(1);
                            cur.put("history", h);
                            all.put(index, cur);
                            saveArray(KEY_TRACKED, all);
                        }
                    } catch (Exception ignored) {
                    }
                }
                refreshTracked(ctx, index + 1);
            }));
        }));
    }


    // ================================================================ batch 8

    private String uPkg = "";
    private String uChat = "";
    private String unifiedFilter = "";

    public static BartalapToolsActivity unifiedChat(String pkg, String chat) {
        BartalapToolsActivity f = new BartalapToolsActivity(MODE_UNIFIED_CHAT);
        f.uPkg = pkg;
        f.uChat = chat;
        return f;
    }

    private static String clock(long ms) {
        return new SimpleDateFormat("dd/MM hh:mm a", Locale.US).format(new Date(ms));
    }

    // ---------------------------------------------------------------- unified inbox (WhatsApp, Messenger …)

    private void buildUnified(Context ctx) {
        boolean granted = BartalapNotificationListener.isPermissionGranted(ctx);
        LinearLayout status = new LinearLayout(ctx);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(16), dp(14), dp(16), dp(14));
        status.setBackground(card(granted ? 0xFF16A34A : 0xFFDC2626));
        status.addView(text(ctx, granted ? "✅ চালু আছে" : "⚠️ অনুমতি দরকার", 17, true, 0xFFFFFFFF));
        status.addView(text(ctx, granted
                ? "WhatsApp, Messenger, Instagram, IMO-র নতুন মেসেজ এখানে জমা হবে। কেউ ডিলিট করলেও কপি থাকবে।"
                : "নিচের বাটনে চাপ দিয়ে লিস্ট থেকে Bartalap চালু করুন (Notification access)।", 13, false, 0xEEFFFFFF));
        status.setLayoutParams(lp(8));
        content.addView(status);
        if (!granted) {
            Button allow = button(ctx, "🔓 অনুমতি দিন", v -> {
                try {
                    if (getParentActivity() != null) getParentActivity().startActivity(BartalapNotificationListener.permissionIntent());
                } catch (Exception e) {
                    toast("Settings → Notifications → Notification access খুলুন");
                }
            });
            allow.setLayoutParams(lp(10));
            content.addView(allow);
        }
        TextView note = text(ctx, "নোট: শুধু নোটিফিকেশনে যে মেসেজ আসে সেটাই দেখা যাবে। সব তথ্য শুধু আপনার ফোনেই থাকে।", 12.5f, false, grayColor());
        note.setLayoutParams(lp(8));
        content.addView(note);

        // Filter chips
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(ctx);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(ctx);
        String[][] filters = {{"", "সব"}, {"com.whatsapp", "WhatsApp"}, {"com.facebook.orca", "Messenger"}, {"com.instagram.android", "Instagram"}, {"com.imo.android.imoim", "IMO"}, {"deleted", "🗑 ডিলিট করা"}};
        for (String[] f : filters) {
            Button b = button(ctx, f[1], v -> {
                unifiedFilter = f[0];
                build(ctx);
            });
            b.setAlpha(f[0].equals(unifiedFilter) ? 1f : 0.5f);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.rightMargin = dp(8);
            chips.addView(b, p);
        }
        hs.addView(chips);
        hs.setLayoutParams(lp(10));
        content.addView(hs);

        JSONArray all = BartalapNotificationListener.load(ctx);
        if ("deleted".equals(unifiedFilter)) {
            int shown = 0;
            for (int i = all.length() - 1; i >= 0 && shown < 200; i--) {
                JSONObject o = all.optJSONObject(i);
                if (o == null || !o.optBoolean("deleted")) continue;
                final String pkg = o.optString("pkg"), chat = o.optString("chat");
                content.addView(itemCard(ctx, "🗑 " + o.optString("sender") + " · " + BartalapNotificationListener.appName(pkg),
                        o.optString("text") + "\n" + clock(o.optLong("time")),
                        v -> presentFragment(unifiedChat(pkg, chat)), null));
                shown++;
            }
            if (shown == 0) content.addView(emptyText(ctx, "কোনো ডিলিট করা মেসেজ ধরা পড়েনি।"));
            return;
        }
        // Group by conversation, newest first
        java.util.LinkedHashMap<String, JSONObject> latest = new java.util.LinkedHashMap<>();
        java.util.HashMap<String, Integer> counts = new java.util.HashMap<>();
        for (int i = all.length() - 1; i >= 0; i--) {
            JSONObject o = all.optJSONObject(i);
            if (o == null) continue;
            String pkg = o.optString("pkg");
            if (!unifiedFilter.isEmpty() && !pkg.startsWith(unifiedFilter)) continue;
            String key = BartalapNotificationListener.convKey(pkg, o.optString("chat"));
            if (!latest.containsKey(key)) latest.put(key, o);
            Integer c = counts.get(key);
            counts.put(key, c == null ? 1 : c + 1);
        }
        if (latest.isEmpty()) {
            content.addView(emptyText(ctx, granted ? "এখনো কোনো মেসেজ আসেনি। নতুন মেসেজ এলে এখানে দেখাবে।" : "অনুমতি দিলে এখানে মেসেজ দেখাবে।"));
            return;
        }
        for (java.util.Map.Entry<String, JSONObject> e : latest.entrySet()) {
            JSONObject o = e.getValue();
            final String pkg = o.optString("pkg"), chat = o.optString("chat");
            String sub = (o.optBoolean("deleted") ? "🗑 " : "") + shorten(o.optString("text"), 90) + "\n" + clock(o.optLong("time")) + " · " + counts.get(e.getKey()) + " টি মেসেজ";
            content.addView(itemCard(ctx, "💬 " + chat + "  ·  " + BartalapNotificationListener.appName(pkg), sub,
                    v -> presentFragment(unifiedChat(pkg, chat)), null));
        }
        Button clear = button(ctx, "🧹 সব মুছে ফেলুন", v -> confirm("সব মুছবেন?", "ইউনিফাইড ইনবক্সের সব জমা মেসেজ মুছে যাবে।", () -> {
            BartalapNotificationListener.clearAll(ctx);
            build(ctx);
        }));
        clear.setLayoutParams(lp(18));
        content.addView(clear);
    }

    private void buildUnifiedChat(Context ctx) {
        JSONArray all = BartalapNotificationListener.load(ctx);
        TextView head = text(ctx, "💬 " + uChat + " · " + BartalapNotificationListener.appName(uPkg), 17, true, textColor());
        head.setLayoutParams(lp(6));
        content.addView(head);
        for (int i = 0; i < all.length(); i++) {
            JSONObject o = all.optJSONObject(i);
            if (o == null || !uPkg.equals(o.optString("pkg")) || !uChat.equals(o.optString("chat"))) continue;
            boolean out = o.optBoolean("out");
            TextView bubble = text(ctx, (out ? "" : o.optString("sender") + "\n") + o.optString("text")
                    + (o.optBoolean("deleted") ? "\n🗑 পাঠানো ব্যক্তি এটা ডিলিট করেছে" : "") + "\n" + clock(o.optLong("time")), 15, false,
                    out ? 0xFFFFFFFF : textColor());
            bubble.setTextIsSelectable(true);
            bubble.setPadding(dp(14), dp(10), dp(14), dp(10));
            bubble.setBackground(card(out ? accent() : (o.optBoolean("deleted") ? 0x33DC2626 : cardColor())));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            p.topMargin = dp(8);
            p.gravity = out ? Gravity.END : Gravity.START;
            content.addView(bubble, p);
        }
        boolean canReply = BartalapNotificationListener.canReply(uPkg, uChat);
        EditText in = input(ctx, canReply ? "উত্তর লিখুন (বাংলিশও চলবে)…" : "রিপ্লাই করতে নতুন মেসেজ আসার অপেক্ষা করুন", true);
        in.setLayoutParams(lp(16));
        content.addView(in);
        Button aiBtn = button(ctx, "🤖 AI দিয়ে উত্তর লিখুন", v -> {
            if (!AiClient.isConfigured()) {
                BartalapChatTools.aiSettings(getParentActivity(), null);
                return;
            }
            StringBuilder hist = new StringBuilder();
            for (int i = Math.max(0, all.length() - 200); i < all.length(); i++) {
                JSONObject o = all.optJSONObject(i);
                if (o == null || !uPkg.equals(o.optString("pkg")) || !uChat.equals(o.optString("chat"))) continue;
                hist.append(o.optBoolean("out") ? "আমি" : o.optString("sender")).append(": ").append(o.optString("text")).append('\n');
            }
            String profileNotes = org.telegram.messenger.BartalapAiAutoReply.prefs().getString(org.telegram.messenger.BartalapAiAutoReply.KEY_PROFILE, "");
            String styleNotes = org.telegram.messenger.BartalapAiAutoReply.prefs().getString(org.telegram.messenger.BartalapAiAutoReply.KEY_STYLE, "");
            toast("AI লিখছে… ⏳");
            new Thread(() -> {
                String r;
                try {
                    r = AiClient.ask("Write the account owner's next short, natural, friendly reply exactly in his style. Never promise money or prices not in his notes. Output only the reply.\nHIS NOTES:\n"
                            + profileNotes + "\nHIS STYLE:\n" + styleNotes, "Chat:\n" + hist + "\nWrite 'আমি' next reply.");
                } catch (Throwable e) {
                    r = null;
                }
                final String out = r;
                AndroidUtilities.runOnUIThread(() -> {
                    if (out == null) toast("AI লিখতে পারেনি");
                    else in.setText(out);
                });
            }).start();
        });
        aiBtn.setLayoutParams(lp(8));
        content.addView(aiBtn);
        Button send = button(ctx, canReply ? "📤 " + BartalapNotificationListener.appName(uPkg) + "-এ উত্তর পাঠান" : "📱 " + BartalapNotificationListener.appName(uPkg) + " খুলুন", v -> {
            if (BartalapNotificationListener.canReply(uPkg, uChat)) {
                String t = in.getText().toString().trim();
                if (t.isEmpty()) return;
                boolean latin = false;
                for (int i = 0; i < t.length(); i++) {
                    char c = t.charAt(i);
                    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) { latin = true; break; }
                }
                if (latin && prefs().getBoolean("unified_banglish", false)) t = BanglishConverter.convert(t);
                if (BartalapNotificationListener.reply(ctx, uPkg, uChat, t)) {
                    toast("পাঠানো হয়েছে ✅");
                    build(ctx);
                } else {
                    toast("পাঠানো যায়নি, অ্যাপ খুলে উত্তর দিন");
                }
            } else {
                try {
                    android.content.Intent li = ctx.getPackageManager().getLaunchIntentForPackage(uPkg);
                    if (li != null && getParentActivity() != null) getParentActivity().startActivity(li);
                } catch (Exception ignored) {
                }
            }
        });
        send.setLayoutParams(lp(8));
        content.addView(send);
        boolean bn = prefs().getBoolean("unified_banglish", false);
        View bnToggle = itemCard(ctx, "⌨️ বাংলিশ লিখলে বাংলায় পাঠাবে: " + (bn ? "চালু" : "বন্ধ"), null, v -> {
            prefs().edit().putBoolean("unified_banglish", !prefs().getBoolean("unified_banglish", false)).apply();
            build(ctx);
        }, null);
        content.addView(bnToggle);
    }

    // ---------------------------------------------------------------- ghost read toggle (used on home)

    private View ghostCard(Context ctx) {
        boolean on = org.telegram.messenger.NayoGramFeatures.isGhostModeEnabled();
        return itemCard(ctx, "👻 সিন না দিয়ে পড়া: " + (on ? "চালু ✅" : "বন্ধ"),
                "চালু থাকলে প্রাইভেট চ্যাটে মেসেজ পড়লেও অন্যজন ব্লু টিক/Seen দেখবে না, আর আপনার typing… দেখাবে না। চাপ দিয়ে চালু/বন্ধ করুন",
                v -> {
                    org.telegram.messenger.NayoGramFeatures.setGhostModeEnabled(!org.telegram.messenger.NayoGramFeatures.isGhostModeEnabled());
                    toast(org.telegram.messenger.NayoGramFeatures.isGhostModeEnabled() ? "সিন না দিয়ে পড়া চালু ✅" : "বন্ধ হয়েছে");
                    build(ctx);
                }, null);
    }

    // ---------------------------------------------------------------- priority inbox

    private static final String[] PRIORITY_WORDS = {"bkash", "বিকাশ", "nagad", "নগদ", "rocket", "trxid", "txnid", "টাকা", "taka", "tk",
            "payment", "পেমেন্ট", "order", "অর্ডার", "price", "দাম", "urgent", "জরুরি", "কিনব", "কিনতে", "buy", "renew", "রিনিউ", "subscription", "send money"};

    private int priorityScore(MessageObject m, TLRPC.User u, TLRPC.Dialog d) {
        int score = 0;
        String t = m.messageText == null ? "" : m.messageText.toString().toLowerCase(Locale.ROOT);
        for (String w : PRIORITY_WORDS) if (t.contains(w)) score += 3;
        if (u != null && u.contact) score += 2;
        if (d.unread_count > 0) score += Math.min(4, d.unread_count);
        long age = System.currentTimeMillis() / 1000L - m.messageOwner.date;
        if (age < 3600) score += 3;
        else if (age < 86400) score += 1;
        return score;
    }

    private void buildPriority(Context ctx) {
        TextView hint = text(ctx, "পেমেন্ট, অর্ডার, টাকা, জরুরি ধরনের কথা আর পরিচিতদের মেসেজ উপরে। শুধু যাদের উত্তর বাকি তারা।", 13, false, grayColor());
        hint.setLayoutParams(lp(8));
        content.addView(hint);
        MessagesController mc = MessagesController.getInstance(currentAccount);
        long self = UserConfig.getInstance(currentAccount).getClientUserId();
        ArrayList<Object[]> list = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null || !DialogObject.isUserDialog(d.id) || d.id == self || d.id == 777000L) continue;
            TLRPC.User u = mc.getUser(d.id);
            if (u == null || u.bot) continue;
            ArrayList<MessageObject> msgs = mc.dialogMessage.get(d.id);
            if (msgs == null || msgs.isEmpty() || msgs.get(0) == null || msgs.get(0).messageOwner == null || msgs.get(0).isOut()) continue;
            int sc = priorityScore(msgs.get(0), u, d);
            if (sc >= 3) list.add(new Object[]{sc, msgs.get(0)});
        }
        Collections.sort(list, (a, b) -> Integer.compare((Integer) b[0], (Integer) a[0]));
        if (list.isEmpty()) {
            content.addView(emptyText(ctx, "এই মুহূর্তে জরুরি কিছু নেই 🎉"));
            return;
        }
        for (Object[] row : list) {
            MessageObject m = (MessageObject) row[1];
            int sc = (Integer) row[0];
            final long did = m.getDialogId();
            String badge = sc >= 9 ? "🔴 খুব জরুরি" : sc >= 6 ? "🟠 জরুরি" : "🟡 দেখুন";
            content.addView(itemCard(ctx, badge + "  ·  " + dialogName(did), shorten(m.messageText, 100) + "\n" + timeAgo(m.messageOwner.date),
                    v -> openChat(did, 0), null));
        }
    }

    // ---------------------------------------------------------------- post performance + A/B compare

    private void buildPosts(Context ctx) {
        TextView intro = text(ctx, "চ্যানেলের শেষ ১০০টা পোস্ট ভিউ অনুযায়ী সাজানো। দুটো পোস্টে চাপ দিলে A/B তুলনা দেখাবে।", 14, false, grayColor());
        intro.setLayoutParams(lp(8));
        content.addView(intro);
        Button pick = button(ctx, "📢 চ্যানেল বেছে নিন", v -> pickChannel(ctx, (id, name) -> loadPosts(ctx, id, name)));
        pick.setLayoutParams(lp(10));
        content.addView(pick);
        statsBox = new LinearLayout(ctx);
        statsBox.setOrientation(LinearLayout.VERTICAL);
        statsBox.setLayoutParams(lp(10));
        content.addView(statsBox);
    }

    private interface ChannelPicked {
        void onPicked(long dialogId, String name);
    }

    private void pickChannel(Context ctx, ChannelPicked cb) {
        if (getParentActivity() == null) return;
        MessagesController mc = MessagesController.getInstance(currentAccount);
        ArrayList<Long> ids = new ArrayList<>();
        ArrayList<CharSequence> names = new ArrayList<>();
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            if (d == null || !DialogObject.isChatDialog(d.id)) continue;
            TLRPC.Chat c = mc.getChat(-d.id);
            if (c == null || !ChatObject.isChannelAndNotMegaGroup(c)) continue;
            ids.add(d.id);
            names.add("📢 " + c.title);
        }
        if (ids.isEmpty()) {
            toast("কোনো চ্যানেল পাওয়া যায়নি");
            return;
        }
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("কোন চ্যানেল?");
        b.setItems(names.toArray(new CharSequence[0]), (dlg, which) -> cb.onPicked(ids.get(which), names.get(which).toString()));
        showDialog(b.create());
    }

    private final ArrayList<TLRPC.Message> abPick = new ArrayList<>();

    private void loadPosts(Context ctx, long dialogId, String name) {
        statsBox.removeAllViews();
        abPick.clear();
        statsBox.addView(emptyText(ctx, "লোড হচ্ছে… ⏳"));
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.limit = 100;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            statsBox.removeAllViews();
            if (!(response instanceof TLRPC.messages_Messages)) {
                statsBox.addView(emptyText(ctx, "লোড করা যায়নি।"));
                return;
            }
            ArrayList<TLRPC.Message> posts = new ArrayList<>();
            for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                if (m != null && m.views > 0) posts.add(m);
            }
            Collections.sort(posts, (a, b) -> Integer.compare(b.views, a.views));
            if (posts.isEmpty()) {
                statsBox.addView(emptyText(ctx, "ভিউসহ কোনো পোস্ট নেই।"));
                return;
            }
            long sum = 0;
            for (TLRPC.Message m : posts) sum += m.views;
            long avg = sum / posts.size();
            statsBox.addView(text(ctx, name + " · গড় ভিউ " + avg, 15, true, accent()));
            int rank = 1;
            for (TLRPC.Message m : posts) {
                final TLRPC.Message post = m;
                String tag = m.views >= avg * 1.5 ? "🔥 " : m.views < avg * 0.6 ? "🧊 " : "";
                String body = (m.message == null || m.message.isEmpty()) ? "(মিডিয়া পোস্ট)" : shorten(m.message, 110);
                statsBox.addView(itemCard(ctx, "#" + rank + "  " + tag + "👁 " + m.views + (m.forwards > 0 ? "  ↗️ " + m.forwards : ""),
                        body + "\n" + clock(m.date * 1000L), v -> {
                            abPick.add(post);
                            if (abPick.size() == 1) {
                                toast("A বেছে নেওয়া হলো, এবার B পোস্টে চাপ দিন");
                            } else {
                                showAb(abPick.get(0), abPick.get(1), avg);
                                abPick.clear();
                            }
                        }, null));
                rank++;
            }
        }));
    }

    private void showAb(TLRPC.Message a, TLRPC.Message b, long avg) {
        if (getParentActivity() == null) return;
        TLRPC.Message win = a.views >= b.views ? a : b;
        double diff = Math.abs(a.views - b.views) * 100.0 / Math.max(1, Math.min(a.views, b.views));
        java.util.Calendar ca = java.util.Calendar.getInstance();
        ca.setTimeInMillis(a.date * 1000L);
        java.util.Calendar cb = java.util.Calendar.getInstance();
        cb.setTimeInMillis(b.date * 1000L);
        String msg = "A: 👁 " + a.views + " · " + hourLabel(ca.get(java.util.Calendar.HOUR_OF_DAY)) + "\n" + shorten(a.message, 80)
                + "\n\nB: 👁 " + b.views + " · " + hourLabel(cb.get(java.util.Calendar.HOUR_OF_DAY)) + "\n" + shorten(b.message, 80)
                + "\n\n🏆 জয়ী: " + (win == a ? "A" : "B") + " (" + String.format(Locale.US, "%.0f", diff) + "% বেশি ভিউ)\nচ্যানেলের গড়: " + avg
                + "\n\nটিপস: জয়ী পোস্টের ধরন, লেখার স্টাইল আর সময় পরের পোস্টে ব্যবহার করুন।";
        AlertDialog.Builder bd = new AlertDialog.Builder(getParentActivity());
        bd.setTitle("📊 A/B তুলনা");
        bd.setMessage(msg);
        bd.setPositiveButton("ঠিক আছে", null);
        showDialog(bd.create());
    }

    // ---------------------------------------------------------------- channel -> website

    private void buildWebsite(Context ctx) {
        TextView intro = text(ctx, "আপনার চ্যানেলের শেষ ১০০টা লেখা-পোস্ট দিয়ে একটা সুন্দর ওয়েবসাইট (HTML ফাইল) বানাবে। ফাইলটা Netlify-তে আপলোড করলেই সাইট লাইভ।", 14, false, grayColor());
        intro.setLayoutParams(lp(8));
        content.addView(intro);
        Button pick = button(ctx, "📢 চ্যানেল বেছে নিন ও ওয়েবসাইট বানান", v -> pickChannel(ctx, (id, name) -> makeWebsite(ctx, id, name)));
        pick.setLayoutParams(lp(10));
        content.addView(pick);
        TextView steps = text(ctx, "Netlify-তে লাইভ করার ধাপ:\n১. app.netlify.com/drop খুলুন\n২. বানানো index.html ফাইলটা ড্রপ করুন\n৩. লিংক পেয়ে যাবেন", 13, false, grayColor());
        steps.setLayoutParams(lp(14));
        content.addView(steps);
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String linkify(String escaped) {
        return escaped.replaceAll("(https?://[^\\s<]+)", "<a href=\"$1\" target=\"_blank\" rel=\"noopener\">$1</a>").replace("\n", "<br>");
    }

    private void makeWebsite(Context ctx, long dialogId, String name) {
        toast("পোস্ট আনা হচ্ছে… ⏳");
        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialogId);
        String username = chat != null && ChatObject.getPublicUsername(chat) != null ? ChatObject.getPublicUsername(chat) : "";
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer(dialogId);
        req.limit = 100;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.messages_Messages)) {
                toast("পোস্ট আনা যায়নি");
                return;
            }
            String title = name.replace("📢 ", "");
            StringBuilder h = new StringBuilder();
            h.append("<!DOCTYPE html><html lang=\"bn\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
                    .append("<title>").append(esc(title)).append("</title>")
                    .append("<link href=\"https://fonts.googleapis.com/css2?family=Hind+Siliguri:wght@400;600;700&display=swap\" rel=\"stylesheet\">")
                    .append("<style>body{margin:0;font-family:'Hind Siliguri',sans-serif;background:#f4f6fb;color:#1e293b}")
                    .append("header{background:linear-gradient(135deg,#00B4DB,#6A3DE8);color:#fff;padding:42px 20px;text-align:center}")
                    .append("header h1{margin:0;font-size:30px}header a{display:inline-block;margin-top:14px;background:#fff;color:#6A3DE8;padding:10px 22px;border-radius:30px;text-decoration:none;font-weight:700}")
                    .append("main{max-width:760px;margin:0 auto;padding:18px}article{background:#fff;border-radius:16px;padding:18px 20px;margin:14px 0;box-shadow:0 2px 12px rgba(0,0,0,.06);line-height:1.7;font-size:17px}")
                    .append("article time{display:block;color:#94a3b8;font-size:13px;margin-top:10px}article a{color:#6A3DE8;word-break:break-all}")
                    .append("footer{text-align:center;color:#94a3b8;padding:30px;font-size:13px}</style></head><body>")
                    .append("<header><h1>").append(esc(title)).append("</h1>");
            if (!username.isEmpty()) h.append("<a href=\"https://t.me/").append(esc(username)).append("\">Telegram-এ জয়েন করুন</a>");
            h.append("</header><main>");
            int count = 0;
            for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                if (m == null || m.message == null || m.message.trim().isEmpty()) continue;
                h.append("<article>").append(linkify(esc(m.message))).append("<time>")
                        .append(new SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(new Date(m.date * 1000L)))
                        .append(m.views > 0 ? " · 👁 " + m.views : "").append("</time></article>");
                count++;
            }
            h.append("</main><footer>Made with Bartalap</footer></body></html>");
            if (count == 0) {
                toast("লেখা-পোস্ট পাওয়া যায়নি");
                return;
            }
            try {
                java.io.File dir = new java.io.File(ctx.getExternalFilesDir(null), "website");
                dir.mkdirs();
                java.io.File f = new java.io.File(dir, "index.html");
                try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) {
                    out.write(h.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(ctx, ApplicationLoader.getApplicationId() + ".provider", f);
                android.content.Intent share = new android.content.Intent(android.content.Intent.ACTION_SEND);
                share.setType("text/html");
                share.putExtra(android.content.Intent.EXTRA_STREAM, uri);
                share.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
                if (getParentActivity() != null) {
                    getParentActivity().startActivity(android.content.Intent.createChooser(share, count + " পোস্টের ওয়েবসাইট শেয়ার/সেভ করুন"));
                }
            } catch (Throwable t) {
                toast("ফাইল সেভ করা যায়নি: " + t.getMessage());
            }
        }));
    }

    // ---------------------------------------------------------------- media finder (sorted by type)

    private void buildMedia(Context ctx) {
        TextView intro = text(ctx, "সব চ্যাটে আসা ফাইল ধরন অনুযায়ী খুঁজুন। চাপ দিলে সেই মেসেজে নিয়ে যাবে।", 14, false, grayColor());
        intro.setLayoutParams(lp(8));
        content.addView(intro);
        LinearLayout results = new LinearLayout(ctx);
        results.setOrientation(LinearLayout.VERTICAL);
        String[] labels = {"🖼 ছবি", "🎬 ভিডিও", "📄 ডকুমেন্ট/ফাইল", "🎵 অডিও/গান", "🎙 ভয়েস মেসেজ", "🧾 পেমেন্ট/রসিদ (লেখা)"};
        for (int i = 0; i < labels.length; i++) {
            final int type = i;
            final String label = labels[i];
            View c = itemCard(ctx, label, null, v -> searchMedia(ctx, type, label, results), null);
            content.addView(c);
        }
        results.setLayoutParams(lp(12));
        content.addView(results);
    }

    private void searchMedia(Context ctx, int type, String label, LinearLayout results) {
        results.removeAllViews();
        results.addView(emptyText(ctx, label + " খোঁজা হচ্ছে… ⏳"));
        TLRPC.TL_messages_searchGlobal req = new TLRPC.TL_messages_searchGlobal();
        req.q = "";
        switch (type) {
            case 0: req.filter = new TLRPC.TL_inputMessagesFilterPhotos(); break;
            case 1: req.filter = new TLRPC.TL_inputMessagesFilterVideo(); break;
            case 2: req.filter = new TLRPC.TL_inputMessagesFilterDocument(); break;
            case 3: req.filter = new TLRPC.TL_inputMessagesFilterMusic(); break;
            case 4: req.filter = new TLRPC.TL_inputMessagesFilterVoice(); break;
            default:
                req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                req.q = "TrxID";
                break;
        }
        req.limit = 60;
        req.offset_peer = new TLRPC.TL_inputPeerEmpty();
        ConnectionsManager.getInstance(currentAccount).sendRequestTyped(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
            results.removeAllViews();
            if (res == null || res.messages.isEmpty()) {
                results.addView(emptyText(ctx, "কিছু পাওয়া যায়নি।"));
                return;
            }
            MessagesController.getInstance(currentAccount).putUsers(res.users, false);
            MessagesController.getInstance(currentAccount).putChats(res.chats, false);
            results.addView(text(ctx, label + ": " + res.messages.size() + " টি", 14, true, accent()));
            for (TLRPC.Message m : res.messages) {
                if (m == null) continue;
                final long did = MessageObject.getPeerId(m.peer_id);
                final int mid = m.id;
                String desc = m.message == null || m.message.isEmpty() ? "" : shorten(m.message, 80);
                if (m.media != null && m.media.document != null) {
                    String fn = org.telegram.messenger.FileLoader.getDocumentFileName(m.media.document);
                    if (fn != null && !fn.isEmpty()) desc = "📎 " + fn + (desc.isEmpty() ? "" : "\n" + desc);
                }
                results.addView(itemCard(ctx, dialogName(did) + "  ·  " + timeAgo(m.date), desc.isEmpty() ? null : desc,
                        v -> openChat(did, mid), null));
            }
        }));
    }

    // ---------------------------------------------------------------- backup / restore of Bartalap data

    private static final String[] BACKUP_KEYS = {KEY_TEMPLATES, KEY_NOTES, KEY_LEDGER, "saved_keywords", "tracked_channels"};

    private void backupDialog(Context ctx) {
        if (getParentActivity() == null) return;
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
        b.setTitle("💾 ব্যাকআপ / রিস্টোর");
        b.setItems(new CharSequence[]{"📤 Saved Messages-এ ব্যাকআপ পাঠান", "📋 ব্যাকআপ কপি করুন", "📥 কপি করা ব্যাকআপ থেকে রিস্টোর"}, (d, which) -> {
            JSONObject all = new JSONObject();
            try {
                for (String k : BACKUP_KEYS) all.put(k, loadArray(k));
            } catch (Exception ignored) {
            }
            String data = "#BartalapBackup\n" + all.toString();
            if (which == 0) {
                long self = UserConfig.getInstance(currentAccount).getClientUserId();
                for (int i = 0; i < data.length(); i += 3900) {
                    String part = data.substring(i, Math.min(data.length(), i + 3900));
                    org.telegram.messenger.SendMessagesHelper.getInstance(currentAccount)
                            .sendMessage(org.telegram.messenger.SendMessagesHelper.SendMessageParams.of(part, self));
                }
                toast("Saved Messages-এ ব্যাকআপ পাঠানো হয়েছে ✅");
            } else if (which == 1) {
                copy(data);
            } else {
                try {
                    ClipboardManager cm = (ClipboardManager) ApplicationLoader.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE);
                    CharSequence clip = cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0 ? cm.getPrimaryClip().getItemAt(0).getText() : null;
                    String s = clip == null ? "" : clip.toString().replace("#BartalapBackup", "").trim();
                    JSONObject o = new JSONObject(s);
                    for (String k : BACKUP_KEYS) {
                        JSONArray arr = o.optJSONArray(k);
                        if (arr != null) saveArray(k, arr);
                    }
                    toast("রিস্টোর হয়েছে ✅");
                    build(ctx);
                } catch (Exception e) {
                    toast("ক্লিপবোর্ডে সঠিক ব্যাকআপ নেই। আগে ব্যাকআপ লেখাটা পুরো কপি করুন।");
                }
            }
        });
        showDialog(b.create());
    }

    // backup card is added at the end of home

    // ---------------------------------------------------------------- live studio guide

    private void buildLive(Context ctx) {
        LinearLayout hero = new LinearLayout(ctx);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(16), dp(16), dp(16), dp(16));
        hero.setBackground(card(0xFFDC2626));
        hero.addView(text(ctx, "🔴 লাইভ স্টুডিও", 20, true, 0xFFFFFFFF));
        hero.addView(text(ctx, "ফোন থেকে লাইভ, স্ক্রিন শেয়ার লাইভ, আর একসাথে Telegram + YouTube + Facebook-এ লাইভ।", 13.5f, false, 0xEEFFFFFF));
        hero.setLayoutParams(lp(8));
        content.addView(hero);

        Button open = button(ctx, "📢 যে চ্যানেলে লাইভ করবেন সেটা খুলুন", v -> pickChannel(ctx, (id, name) -> {
            toast("উপরে চ্যানেলের নাম → ⋮ → Start Live Stream চাপুন");
            openChat(id, 0);
        }));
        open.setLayoutParams(lp(12));
        content.addView(open);

        TextView p0 = text(ctx, "⭐ নিজের আইডি থেকে লাইভ (Facebook-এর মতো)", 15, true, textColor());
        p0.setLayoutParams(lp(16));
        content.addView(p0);
        content.addView(text(ctx, "চ্যাট লিস্টে উপরে নিজের স্টোরির ঘরে (+) চাপুন, বা চ্যাট লিস্ট নিচে টেনে ক্যামেরা খুলুন → নিচে \"Live\" বেছে নিন → কারা দেখবে ঠিক করুন → Start Live Stream।\n"
                + "• উপরে দেখাবে কতজন দেখছে 👁\n• সবাই কমেন্ট করতে পারবে, আপনি সব দেখবেন (⚙️ থেকে Allow Comments)\n"
                + "• দর্শকরা Stars ⭐ পাঠাতে পারবে, চাইলে প্রতি কমেন্টে Stars দাম রাখতে পারবেন\n"
                + "• আপনার কন্টাক্টরা নোটিফিকেশন পাবে যে আপনি লাইভে আছেন\n• Keep this live in your profile চালু করলে লাইভ প্রোফাইলে থেকে যাবে", 13.5f, false, grayColor()));

        TextView v0 = text(ctx, "🎙 শুধু ভয়েস লাইভ (ক্যামেরা ছাড়া)", 15, true, textColor());
        v0.setLayoutParams(lp(16));
        content.addView(v0);
        content.addView(text(ctx, "নিজের গ্রুপ বা চ্যানেল খুলুন → উপরে নাম → ⋮ → Start Voice Chat / Live Stream। ক্যামেরা চালু না করলে শুধু ভয়েসে চলবে। "
                + "শ্রোতারা হাত তুলে কথা বলার অনুমতি চাইতে পারবে, উপরে কতজন শুনছে দেখাবে। চাইলে স্ক্রিন শেয়ারও করা যাবে।", 13.5f, false, grayColor()));

        TextView a = text(ctx, "১. চ্যানেলে ক্যামেরা বা স্ক্রিন দিয়ে লাইভ", 15, true, textColor());
        a.setLayoutParams(lp(16));
        content.addView(a);
        content.addView(text(ctx, "চ্যানেল খুলুন → উপরে চ্যানেলের নাম → ⋮ → Start Live Stream → Start Streaming। তারপর ক্যামেরা আইকন দিয়ে ভিডিও বা Share Screen দিয়ে স্ক্রিন লাইভ।", 13.5f, false, grayColor()));

        TextView b = text(ctx, "২. একসাথে Telegram + YouTube + Facebook (মাল্টি-স্ট্রিম)", 15, true, textColor());
        b.setLayoutParams(lp(16));
        content.addView(b);
        content.addView(text(ctx, "চ্যানেল → Start Live Stream-এর পাশে \"Stream With…\" চাপলে Server URL আর Stream Key পাবেন।\n"
                + "• Larix Broadcaster বা Prism Live (ফোন) / OBS (পিসি) অ্যাপে ৩টা জায়গার URL+Key বসান: Telegram, YouTube Studio → Go Live → Stream key, Facebook → Live Producer।\n"
                + "• অথবা restream.io-তে একবার Key বসালে সবগুলোতে একসাথে যাবে।", 13.5f, false, grayColor()));

        Button copyGuide = button(ctx, "📋 ধাপগুলো কপি করুন", v -> copy("Telegram + YouTube + Facebook একসাথে লাইভ:\n"
                + "1) Telegram চ্যানেল → ⋮ → Start Live Stream → Stream With → Server URL + Stream Key কপি\n"
                + "2) YouTube Studio → Create → Go Live → Stream key কপি\n"
                + "3) Facebook → Live video → Streaming software → Stream key কপি\n"
                + "4) Larix Broadcaster / Prism Live / OBS-এ ৩টা আলাদা Output-এ URL+Key বসিয়ে Start"));
        copyGuide.setLayoutParams(lp(12));
        content.addView(copyGuide);

        TextView c = text(ctx, "৩. লাইভের পরে", 15, true, textColor());
        c.setLayoutParams(lp(16));
        content.addView(c);
        content.addView(text(ctx, "Telegram লাইভ শেষ হলে রেকর্ডিং চ্যানেলে সেভ করা যায় (লাইভ শুরুর সময় Start Recording)। সেই ভিডিও থেকে Shorts কেটে নিতে পারবেন।", 13.5f, false, grayColor()));
    }


    // ---------------------------------------------------------------- AI auto-reply control panel

    private void buildAiAuto(Context ctx) {
        SharedPreferences p = org.telegram.messenger.BartalapAiAutoReply.prefs();
        boolean on = org.telegram.messenger.BartalapAiAutoReply.isEnabled();
        boolean keyOk = AiClient.isConfigured();

        LinearLayout status = new LinearLayout(ctx);
        status.setOrientation(LinearLayout.VERTICAL);
        status.setPadding(dp(18), dp(18), dp(18), dp(18));
        status.setBackground(card(on ? 0xFF16A34A : 0xFF475569));
        status.addView(text(ctx, on ? "🤖 AI অটো-রিপ্লাই চালু" : "⏸ AI অটো-রিপ্লাই বন্ধ", 20, true, 0xFFFFFFFF));
        status.addView(text(ctx, on
                ? (p.getBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_OFFLINE, true) ? "আপনি অ্যাপের বাইরে থাকলে প্রাইভেট মেসেজের উত্তর AI দেবে" : "সব প্রাইভেট মেসেজের উত্তর AI দেবে")
                : "চালু করলে আপনার হয়ে আপনার মতো করে উত্তর দেবে", 13.5f, false, 0xEEFFFFFF));
        status.setLayoutParams(lp(8));
        content.addView(status);

        Button toggle = button(ctx, on ? "⏸ বন্ধ করুন" : "▶️ চালু করুন", v -> {
            if (!AiClient.isConfigured()) {
                BartalapChatTools.aiSettings(getParentActivity(), () -> build(ctx));
                return;
            }
            org.telegram.messenger.BartalapAiAutoReply.setEnabled(!org.telegram.messenger.BartalapAiAutoReply.isEnabled());
            toast(org.telegram.messenger.BartalapAiAutoReply.isEnabled() ? "AI অটো-রিপ্লাই চালু ✅" : "বন্ধ হয়েছে");
            build(ctx);
        });
        toggle.setLayoutParams(lp(10));
        content.addView(toggle);
        if (!keyOk) {
            TextView w = text(ctx, "⚠️ আগে AI সেটিংসে Claude API Key দিন।", 14, true, 0xFFDC2626);
            w.setLayoutParams(lp(8));
            content.addView(w);
        }

        // Options
        TextView oh = text(ctx, "⚙️ নিয়ম", 16, true, textColor());
        oh.setLayoutParams(lp(18));
        content.addView(oh);
        boolean onlyOffline = p.getBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_OFFLINE, true);
        content.addView(itemCard(ctx, "কখন: " + (onlyOffline ? "শুধু আমি অ্যাপের বাইরে থাকলে" : "সবসময়"), "চাপ দিয়ে বদলান", v -> {
            p.edit().putBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_OFFLINE, !p.getBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_OFFLINE, true)).apply();
            build(ctx);
        }, null));
        boolean fast = org.telegram.messenger.BartalapAiAutoReply.isFast();
        content.addView(itemCard(ctx, "⚡ গতি: " + (fast ? "তাৎক্ষণিক (সাথে সাথে উত্তর)" : "মানুষের মতো (১৫-৪০ সেকেন্ড পরে)"), "চাপ দিয়ে বদলান", v -> {
            p.edit().putBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_FAST, !org.telegram.messenger.BartalapAiAutoReply.isFast()).apply();
            build(ctx);
        }, null));
        boolean onlyContacts = p.getBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_CONTACTS, false);
        content.addView(itemCard(ctx, "কাদের: " + (onlyContacts ? "শুধু কন্টাক্টে থাকা মানুষ" : "সবাই (Everyone)"), "গ্রুপ আর চ্যানেলে কখনো উত্তর দেবে না। চাপ দিয়ে বদলান", v -> {
            p.edit().putBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_CONTACTS, !p.getBoolean(org.telegram.messenger.BartalapAiAutoReply.KEY_ONLY_CONTACTS, false)).apply();
            build(ctx);
        }, null));
        int vp = p.getInt(org.telegram.messenger.BartalapAiAutoReply.KEY_VOICE_PERCENT, 20);
        content.addView(itemCard(ctx, "🎙 ভয়েসে উত্তর: " + (vp == 0 ? "কখনো না" : vp + "% সময়"), "ছোট উত্তরগুলো মাঝে মাঝে ভয়েস মেসেজ হিসেবে যাবে (ফোনের বাংলা কণ্ঠে)। চাপ দিয়ে বদলান", v -> {
            int cur = p.getInt(org.telegram.messenger.BartalapAiAutoReply.KEY_VOICE_PERCENT, 20);
            int next = cur == 0 ? 20 : cur == 20 ? 40 : cur == 40 ? 70 : 0;
            p.edit().putInt(org.telegram.messenger.BartalapAiAutoReply.KEY_VOICE_PERCENT, next).apply();
            build(ctx);
        }, null));
        int mx = p.getInt(org.telegram.messenger.BartalapAiAutoReply.KEY_MAX_PER_HOUR, 8);
        content.addView(itemCard(ctx, "সীমা: একজনকে ঘণ্টায় সর্বোচ্চ " + mx + " টা উত্তর", "API খরচ বাঁচাতে। চাপ দিয়ে বদলান", v -> {
            int cur = p.getInt(org.telegram.messenger.BartalapAiAutoReply.KEY_MAX_PER_HOUR, 8);
            int next = cur == 4 ? 8 : cur == 8 ? 15 : cur == 15 ? 30 : 4;
            p.edit().putInt(org.telegram.messenger.BartalapAiAutoReply.KEY_MAX_PER_HOUR, next).apply();
            build(ctx);
        }, null));

        // Profile
        TextView ph = text(ctx, "👤 আমার সম্পর্কে (AI এটা পড়ে উত্তর দেবে)", 16, true, textColor());
        ph.setLayoutParams(lp(18));
        content.addView(ph);
        EditText profile = input(ctx, "যেমন: আমি ইব্রাহিম, Free Help Now ইউটিউব চ্যানেল চালাই। ChatGPT Plus, Canva Pro বিক্রি করি। দাম: ... পেমেন্ট bKash-এ। সবাইকে 'ভাই' বলি। নামাজের সময় ব্যস্ত থাকি…", true);
        profile.setMinLines(5);
        profile.setText(p.getString(org.telegram.messenger.BartalapAiAutoReply.KEY_PROFILE, ""));
        content.addView(profile);
        Button saveProfile = button(ctx, "💾 সেভ করুন", v -> {
            p.edit().putString(org.telegram.messenger.BartalapAiAutoReply.KEY_PROFILE, profile.getText().toString().trim()).apply();
            toast("সেভ হয়েছে ✅");
        });
        saveProfile.setLayoutParams(lp(8));
        content.addView(saveProfile);

        // Style
        TextView sh = text(ctx, "🧠 আমার লেখার স্টাইল", 16, true, textColor());
        sh.setLayoutParams(lp(18));
        content.addView(sh);
        EditText style = input(ctx, "নিচের বাটন চাপলে আপনার আগের মেসেজ পড়ে AI নিজেই এখানে লিখে দেবে", true);
        style.setMinLines(4);
        style.setText(p.getString(org.telegram.messenger.BartalapAiAutoReply.KEY_STYLE, ""));
        content.addView(style);
        Button learn = button(ctx, "🧠 আগের মেসেজ পড়ে আমার স্টাইল শিখুন", v -> {
            if (!AiClient.isConfigured()) {
                BartalapChatTools.aiSettings(getParentActivity(), null);
                return;
            }
            toast("আপনার আগের মেসেজ পড়া হচ্ছে… ⏳ (এক মিনিট লাগতে পারে)");
            final int acc = currentAccount;
            new Thread(() -> {
                String r;
                boolean ok;
                try {
                    r = org.telegram.messenger.BartalapAiAutoReply.learnStyle(acc);
                    ok = true;
                } catch (Throwable e) {
                    r = e.getMessage();
                    ok = false;
                }
                final String result = r == null ? "" : r;
                final boolean success = ok;
                AndroidUtilities.runOnUIThread(() -> {
                    if (success) {
                        style.setText(result);
                        p.edit().putString(org.telegram.messenger.BartalapAiAutoReply.KEY_STYLE, result).apply();
                        toast("স্টাইল শেখা হয়েছে ✅ চাইলে এডিট করে সেভ করুন");
                    } else {
                        toast("শেখা যায়নি: " + result);
                    }
                });
            }).start();
        });
        learn.setLayoutParams(lp(8));
        content.addView(learn);
        Button saveStyle = button(ctx, "💾 স্টাইল সেভ করুন", v -> {
            p.edit().putString(org.telegram.messenger.BartalapAiAutoReply.KEY_STYLE, style.getText().toString().trim()).apply();
            toast("সেভ হয়েছে ✅");
        });
        saveStyle.setLayoutParams(lp(8));
        content.addView(saveStyle);

        // Test
        TextView th = text(ctx, "🧪 টেস্ট করুন (কিছু পাঠাবে না)", 16, true, textColor());
        th.setLayoutParams(lp(18));
        content.addView(th);
        EditText testIn = input(ctx, "কেউ যেন লিখেছে: ভাই ChatGPT Plus এর দাম কত?", false);
        content.addView(testIn);
        TextView testOut = text(ctx, "", 15, false, textColor());
        testOut.setPadding(dp(14), dp(10), dp(14), dp(10));
        testOut.setBackground(card(cardColor()));
        testOut.setTextIsSelectable(true);
        Button test = button(ctx, "🤖 AI কী উত্তর দিত দেখুন", v -> {
            String q = testIn.getText().toString().trim();
            if (q.isEmpty()) return;
            testOut.setText("ভাবছে… ⏳");
            String prof = profile.getText().toString().trim();
            String sty = style.getText().toString().trim();
            new Thread(() -> {
                String r;
                try {
                    r = AiClient.ask("You reply on Telegram on behalf of the account owner, exactly like him, short and natural. "
                                    + "Never promise money/prices not in his notes. Output only the reply.\nHIS NOTES:\n" + prof + "\nHIS STYLE:\n" + sty,
                            "Friend: " + q + "\nWrite his reply.");
                } catch (Throwable e) {
                    r = "⚠️ " + e.getMessage();
                }
                final String out = r;
                AndroidUtilities.runOnUIThread(() -> testOut.setText(out));
            }).start();
        });
        test.setLayoutParams(lp(8));
        content.addView(test);
        testOut.setLayoutParams(lp(8));
        content.addView(testOut);

        // Log
        TextView lh = text(ctx, "📜 AI যা যা উত্তর দিয়েছে", 16, true, textColor());
        lh.setLayoutParams(lp(18));
        content.addView(lh);
        JSONArray log;
        try {
            log = new JSONArray(p.getString(org.telegram.messenger.BartalapAiAutoReply.KEY_LOG, "[]"));
        } catch (Exception e) {
            log = new JSONArray();
        }
        if (log.length() == 0) content.addView(emptyText(ctx, "এখনো কোনো উত্তর দেয়নি।"));
        for (int i = log.length() - 1; i >= Math.max(0, log.length() - 40); i--) {
            JSONObject o = log.optJSONObject(i);
            if (o == null) continue;
            content.addView(itemCard(ctx, (o.optBoolean("voice") ? "🎙 " : "💬 ") + o.optString("chat") + "  ·  " + clock(o.optLong("time")),
                    "সে: " + shorten(o.optString("in"), 80) + "\nAI: " + o.optString("out"), null, null));
        }
        TextView tip = text(ctx, "কোনো নির্দিষ্ট চ্যাটে AI চান না? সেই চ্যাটে ⋮ → ⚡ Bartalap Tools → \"এই চ্যাটে AI অটো-রিপ্লাই বন্ধ\"।\n"
                + "নিরাপত্তা: AI টাকা/পেমেন্ট নিয়ে কোনো কথা দেবে না, পেমেন্ট পেয়েছি বলবে না, আর কেউ সত্যিই জিজ্ঞেস করলে বলবে এটা অটো-রিপ্লাই, আপনি পরে উত্তর দেবেন।", 12.5f, false, grayColor());
        tip.setLayoutParams(lp(16));
        content.addView(tip);
    }

}
