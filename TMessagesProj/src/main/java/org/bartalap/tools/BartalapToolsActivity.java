package org.bartalap.tools;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
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
            default: buildHome(ctx); break;
        }
    }

    // ---------------------------------------------------------------- home

    private void buildHome(Context ctx) {
        TextView intro = text(ctx, "Bartalap-এর নিজস্ব ফিচার। যেকোনোটায় চাপ দিন 👇", 14, false, grayColor());
        intro.setPadding(dp(4), dp(8), dp(4), dp(4));
        content.addView(intro);
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
}
