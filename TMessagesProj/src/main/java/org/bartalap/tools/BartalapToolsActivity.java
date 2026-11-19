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
            default: buildHome(ctx); break;
        }
    }

    // ---------------------------------------------------------------- home

    private void buildHome(Context ctx) {
        TextView intro = text(ctx, "Bartalap-এর নিজস্ব ফিচার। যেকোনোটায় চাপ দিন 👇\nটিপস: যেকোনো চ্যাটে ⋮ → ⚡ Bartalap Tools থেকে Quick Reply, AI উত্তর, নোট সরাসরি পাবেন।", 14, false, grayColor());
        intro.setPadding(dp(4), dp(8), dp(4), dp(4));
        content.addView(intro);
        content.addView(itemCard(ctx, "🤖 AI সেটিংস", "Claude বা Gemini API Key দিন। তারপর যেকোনো চ্যাটে ⋮ → ⚡ Bartalap Tools থেকে AI উত্তর আর সারাংশ পাবেন",
                v -> BartalapChatTools.aiSettings(getParentActivity(), null), null));
        content.addView(itemCard(ctx, "📥 স্মার্ট ইনবক্স", "যাদের উত্তর দেননি, আর যাদের ফলো-আপ দরকার, আলাদা লিস্টে",
                v -> presentFragment(new BartalapToolsActivity(MODE_INBOX)), null));
        content.addView(itemCard(ctx, "▶️ YouTube অটো পোস্ট", "আপনার YouTube চ্যানেলে নতুন ভিডিও এলে নিজে থেকে Telegram চ্যানেলে পোস্ট হবে",
                v -> presentFragment(new BartalapToolsActivity(MODE_YOUTUBE)), null));
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

}
