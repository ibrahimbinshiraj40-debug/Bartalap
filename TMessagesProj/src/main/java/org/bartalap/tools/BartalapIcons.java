package org.bartalap.tools;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import java.util.HashMap;

/**
 * Bartalap: replaces emoji in titles/buttons with Telegram's own line icons inside a coloured
 * rounded square (same look as Telegram settings), for a clean professional UI.
 */
public final class BartalapIcons {

    private BartalapIcons() {}

    public static final class Icon {
        public final int res;
        public final int color;

        Icon(int res, int color) {
            this.res = res;
            this.color = color;
        }
    }

    private static final int BLUE = 0xFF2F80ED, GREEN = 0xFF27AE60, ORANGE = 0xFFF2994A, RED = 0xFFEB5757,
            PURPLE = 0xFF8E54E9, TEAL = 0xFF00A8B5, PINK = 0xFFE5487D, GRAY = 0xFF6B7A90, INDIGO = 0xFF5B5FE3, AMBER = 0xFFE0A100;

    private static final HashMap<String, Icon> MAP = new HashMap<>();

    private static void put(String emoji, int res, int color) {
        MAP.put(emoji, new Icon(res, color));
    }

    static {
        put("🤖", R.drawable.msg_bot, PURPLE);
        put("📲", R.drawable.msg_message, GREEN);
        put("⭐", R.drawable.msg_fave, AMBER);
        put("👻", R.drawable.msg_stories_stealth, INDIGO);
        put("🎙", R.drawable.msg_voicechat, PINK);
        put("🌙", R.drawable.msg_notifications, INDIGO);
        put("📥", R.drawable.msg_markunread, BLUE);
        put("▶", R.drawable.msg_round_play_m, RED);
        put("⏸", R.drawable.msg_round_pause_m, GRAY);
        put("🚀", R.drawable.msg_share, BLUE);
        put("📈", R.drawable.msg_stats, GREEN);
        put("🔴", R.drawable.msg_voicechat2, RED);
        put("🔥", R.drawable.msg_views, ORANGE);
        put("🌐", R.drawable.msg_language, TEAL);
        put("🗂", R.drawable.msg_media, ORANGE);
        put("📊", R.drawable.msg_stats, BLUE);
        put("🔗", R.drawable.msg_link, TEAL);
        put("🔔", R.drawable.msg_notifications, ORANGE);
        put("⌨", R.drawable.msg_photo_text_regular, BLUE);
        put("⚡", R.drawable.msg_premium_speed, AMBER);
        put("📝", R.drawable.msg_edit, ORANGE);
        put("💰", R.drawable.msg_payment_card, GREEN);
        put("🔎", R.drawable.msg_search, BLUE);
        put("💾", R.drawable.msg_saved, BLUE);
        put("💬", R.drawable.msg_discussion, BLUE);
        put("⏰", R.drawable.msg_mute_period, ORANGE);
        put("✅", R.drawable.msg_text_check, GREEN);
        put("🧪", R.drawable.msg_retry, TEAL);
        put("🧠", R.drawable.msg_premium_emoji, PURPLE);
        put("👤", R.drawable.msg_contacts, BLUE);
        put("📜", R.drawable.msg_pinnedlist, GRAY);
        put("⚙", R.drawable.msg_settings, GRAY);
        put("✋", R.drawable.msg_block, ORANGE);
        put("🗑", R.drawable.msg_delete, RED);
        put("🔄", R.drawable.msg_retry, BLUE);
        put("📋", R.drawable.msg_copy, BLUE);
        put("🔓", R.drawable.msg_secret, GREEN);
        put("🧹", R.drawable.msg_clear, RED);
        put("📢", R.drawable.msg_channel, BLUE);
        put("👥", R.drawable.msg_groups, TEAL);
        put("🏆", R.drawable.msg_premium_badge, AMBER);
        put("📅", R.drawable.msg_calendar2, BLUE);
        put("🧾", R.drawable.msg_view_file, TEAL);
        put("💳", R.drawable.msg_payment_card, GREEN);
        put("😴", R.drawable.msg_mute, INDIGO);
        put("➕", R.drawable.msg_addfolder, BLUE);
        put("✏", R.drawable.msg_edit, ORANGE);
        put("🎬", R.drawable.msg_video, RED);
        put("🖼", R.drawable.msg_photos, BLUE);
        put("📄", R.drawable.msg_view_file, GRAY);
        put("🎵", R.drawable.msg_voice_headphones, PINK);
        put("📤", R.drawable.msg_shareout, BLUE);
        put("📱", R.drawable.msg_message, GREEN);
        put("⚠", R.drawable.msg_info, RED);
        put("👁", R.drawable.msg_views, GRAY);
        put("🟢", R.drawable.msg_check_s, GREEN);
        put("🔒", R.drawable.msg_secret, GRAY);
        put("💡", R.drawable.msg_info, AMBER);
        put("📍", R.drawable.msg_pin, RED);
    }

    private static boolean isEmojiChar(int cp) {
        return (cp >= 0x2190 && !Character.isLetterOrDigit(cp)) || cp == 0xFE0F || cp == 0x200D || cp == 0x20E3;
    }

    /** Returns {leadingEmoji, rest} — leadingEmoji may be "" when the text starts normally. */
    public static String[] split(String text) {
        if (text == null) return new String[]{"", ""};
        int i = 0;
        int n = text.length();
        while (i < n) {
            int cp = text.codePointAt(i);
            if (Character.isWhitespace(cp) || !isEmojiChar(cp)) break;
            i += Character.charCount(cp);
        }
        String lead = text.substring(0, i);
        String rest = text.substring(i).trim();
        return new String[]{lead, rest};
    }

    /** Removes emoji anywhere at the start of a label (used for buttons, menu items, toasts). */
    public static String clean(String text) {
        String[] s = split(text);
        String rest = s[1];
        // strip trailing emoji too (e.g. "হয়েছে ✅")
        int end = rest.length();
        while (end > 0) {
            int cp = rest.codePointBefore(end);
            if (Character.isWhitespace(cp) || isEmojiChar(cp) && cp != 0x2026) {
                end -= Character.charCount(cp);
            } else {
                break;
            }
        }
        return rest.substring(0, end).trim();
    }

    public static Icon iconFor(String lead) {
        if (lead == null || lead.isEmpty()) return null;
        String key = lead.replace("️", "").trim();
        Icon icon = MAP.get(key);
        if (icon == null && key.length() > 0) {
            int cp = key.codePointAt(0);
            icon = MAP.get(new String(Character.toChars(cp)));
        }
        return icon == null ? new Icon(R.drawable.msg_settings, GRAY) : icon;
    }

    public static Drawable tinted(Context ctx, int res, int color) {
        Drawable d = ctx.getResources().getDrawable(res).mutate();
        d.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
        return d;
    }

    /** A 36dp coloured rounded square with a white Telegram icon inside. */
    public static FrameLayout badge(Context ctx, Icon icon) {
        FrameLayout box = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(icon.color);
        bg.setCornerRadius(AndroidUtilities.dp(10));
        box.setBackground(bg);
        ImageView iv = new ImageView(ctx);
        iv.setImageDrawable(tinted(ctx, icon.res, 0xFFFFFFFF));
        iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int p = AndroidUtilities.dp(6);
        iv.setPadding(p, p, p, p);
        box.addView(iv, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER));
        return box;
    }
}
