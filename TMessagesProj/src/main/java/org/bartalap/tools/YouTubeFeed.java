package org.bartalap.tools;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bartalap: reads a YouTube channel's public RSS feed (no API key needed).
 * Pure Java so the parsing can be unit tested anywhere.
 */
public final class YouTubeFeed {

    private YouTubeFeed() {}

    public static final class Video {
        public final String id;
        public final String title;

        public Video(String id, String title) {
            this.id = id;
            this.title = title;
        }

        public String url() {
            return "https://youtu.be/" + id;
        }
    }

    private static final Pattern CHANNEL_ID = Pattern.compile("(UC[A-Za-z0-9_-]{22})");
    private static final Pattern PAGE_CHANNEL_ID = Pattern.compile("\"(?:channelId|externalId)\"\\s*:\\s*\"(UC[A-Za-z0-9_-]{22})\"");
    private static final Pattern ENTRY = Pattern.compile("<entry>(.*?)</entry>", Pattern.DOTALL);
    private static final Pattern VIDEO_ID = Pattern.compile("<yt:videoId>([^<]+)</yt:videoId>");
    private static final Pattern TITLE = Pattern.compile("<title>([^<]*)</title>");

    /** Accepts a channel ID (UC...), a channel URL, or an @handle / handle URL. Network may be used for handles. */
    public static String resolveChannelId(String input) throws Exception {
        if (input == null) return null;
        String s = input.trim();
        if (s.isEmpty()) return null;
        Matcher m = CHANNEL_ID.matcher(s);
        if (m.find()) return m.group(1);
        String url;
        if (s.startsWith("http")) {
            url = s;
        } else {
            String handle = s.startsWith("@") ? s : "@" + s;
            url = "https://www.youtube.com/" + handle;
        }
        String page = httpGet(url);
        m = PAGE_CHANNEL_ID.matcher(page);
        if (m.find()) return m.group(1);
        return null;
    }

    public static String feedUrl(String channelId) {
        return "https://www.youtube.com/feeds/videos.xml?channel_id=" + channelId;
    }

    /** Latest video in the feed, or null. */
    public static Video fetchLatest(String channelId) throws Exception {
        return parseLatest(httpGet(feedUrl(channelId)));
    }

    public static Video parseLatest(String xml) {
        if (xml == null) return null;
        Matcher e = ENTRY.matcher(xml);
        if (!e.find()) return null;
        String entry = e.group(1);
        Matcher id = VIDEO_ID.matcher(entry);
        if (!id.find()) return null;
        Matcher t = TITLE.matcher(entry);
        String title = t.find() ? unescape(t.group(1)) : "";
        return new Video(id.group(1).trim(), title.trim());
    }

    public static String unescape(String s) {
        return s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">");
    }

    public static String fillTemplate(String template, Video v) {
        String t = (template == null || template.trim().isEmpty()) ? DEFAULT_TEMPLATE : template;
        return t.replace("{title}", v.title).replace("{শিরোনাম}", v.title)
                .replace("{link}", v.url()).replace("{লিংক}", v.url());
    }

    public static final String DEFAULT_TEMPLATE = "🎬 নতুন ভিডিও এসেছে!\n\n{title}\n\n▶️ দেখুন: {link}";

    static String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) Bartalap");
        c.setRequestProperty("Accept-Language", "en");
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
                if (out.size() > 3_000_000) break;
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            c.disconnect();
        }
    }
}
