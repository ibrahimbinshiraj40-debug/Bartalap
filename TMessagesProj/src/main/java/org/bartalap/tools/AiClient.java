package org.bartalap.tools;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.ApplicationLoader;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Bartalap: tiny AI client. Works with the Anthropic (Claude) Messages API, or with any
 * OpenAI-compatible endpoint (OpenAI, Gemini OpenAI endpoint, OpenRouter, resellers).
 * The user brings their own API key + base URL. Call from a background thread.
 */
public final class AiClient {

    private AiClient() {}

    public static final String KEY_FORMAT = "ai_format";      // "anthropic" or "openai"
    public static final String KEY_BASE = "ai_base_url";
    public static final String KEY_API_KEY = "ai_api_key";
    public static final String KEY_MODEL = "ai_model";

    public static final String DEFAULT_ANTHROPIC_BASE = "https://api.anthropic.com";
    public static final String DEFAULT_ANTHROPIC_MODEL = "claude-haiku-5-5";
    public static final String DEFAULT_OPENAI_BASE = "https://generativelanguage.googleapis.com/v1beta/openai";
    public static final String DEFAULT_OPENAI_MODEL = "gemini-2.5-flash";

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("bartalap_tools", Context.MODE_PRIVATE);
    }

    public static boolean isConfigured() {
        return !prefs().getString(KEY_API_KEY, "").trim().isEmpty();
    }

    public static boolean isOpenAiFormat() {
        return "openai".equals(prefs().getString(KEY_FORMAT, "anthropic"));
    }

    /** Sends one prompt and returns the model's text answer. Throws with a readable message on failure. */
    public static String ask(String system, String prompt) throws Exception {
        return ask(system, prompt, null);
    }

    /** Reads an image file, shrinks it and returns base64 JPEG (for AI vision). */
    public static String imageToBase64(String path) throws Exception {
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        android.graphics.BitmapFactory.decodeFile(path, o);
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / sample > 1600) sample *= 2;
        android.graphics.BitmapFactory.Options o2 = new android.graphics.BitmapFactory.Options();
        o2.inSampleSize = sample;
        android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeFile(path, o2);
        if (bmp == null) throw new Exception("ছবি খোলা যায়নি");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 82, out);
        bmp.recycle();
        return android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP);
    }

    /** imageBase64Jpeg may be null for text-only questions. */
    public static String ask(String system, String prompt, String imageBase64Jpeg) throws Exception {
        SharedPreferences p = prefs();
        String key = p.getString(KEY_API_KEY, "").trim();
        if (key.isEmpty()) throw new Exception("AI API Key দেওয়া হয়নি");
        boolean openai = isOpenAiFormat();
        String base = p.getString(KEY_BASE, "").trim();
        if (base.isEmpty()) base = openai ? DEFAULT_OPENAI_BASE : DEFAULT_ANTHROPIC_BASE;
        while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        String model = p.getString(KEY_MODEL, "").trim();
        if (model.isEmpty()) model = openai ? DEFAULT_OPENAI_MODEL : DEFAULT_ANTHROPIC_MODEL;

        String url;
        JSONObject body = new JSONObject();
        if (openai) {
            url = base.endsWith("/chat/completions") ? base : base + "/chat/completions";
            JSONArray msgs = new JSONArray();
            msgs.put(new JSONObject().put("role", "system").put("content", system));
            if (imageBase64Jpeg == null) {
                msgs.put(new JSONObject().put("role", "user").put("content", prompt));
            } else {
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("type", "text").put("text", prompt));
                parts.put(new JSONObject().put("type", "image_url").put("image_url",
                        new JSONObject().put("url", "data:image/jpeg;base64," + imageBase64Jpeg)));
                msgs.put(new JSONObject().put("role", "user").put("content", parts));
            }
            body.put("model", model).put("messages", msgs).put("max_tokens", 800);
        } else {
            url = base.endsWith("/v1/messages") ? base : (base.endsWith("/v1") ? base + "/messages" : base + "/v1/messages");
            JSONArray msgs = new JSONArray();
            if (imageBase64Jpeg == null) {
                msgs.put(new JSONObject().put("role", "user").put("content", prompt));
            } else {
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("type", "image").put("source", new JSONObject()
                        .put("type", "base64").put("media_type", "image/jpeg").put("data", imageBase64Jpeg)));
                parts.put(new JSONObject().put("type", "text").put("text", prompt));
                msgs.put(new JSONObject().put("role", "user").put("content", parts));
            }
            body.put("model", model).put("system", system).put("messages", msgs).put("max_tokens", 800);
        }

        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        if (openai) {
            c.setRequestProperty("Authorization", "Bearer " + key);
        } else {
            c.setRequestProperty("x-api-key", key);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("anthropic-version", "2023-06-01");
        }
        try (OutputStream os = c.getOutputStream()) {
            os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        int code = c.getResponseCode();
        String resp = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        c.disconnect();
        if (code >= 400) {
            String msg = resp;
            try {
                JSONObject e = new JSONObject(resp).optJSONObject("error");
                if (e != null) msg = e.optString("message", resp);
            } catch (Exception ignored) {
            }
            throw new Exception("AI error " + code + ": " + (msg.length() > 200 ? msg.substring(0, 200) : msg));
        }
        JSONObject j = new JSONObject(resp);
        if (openai) {
            JSONArray choices = j.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject m = choices.getJSONObject(0).optJSONObject("message");
                if (m != null) return m.optString("content", "").trim();
            }
        } else {
            JSONArray content = j.optJSONArray("content");
            if (content != null) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < content.length(); i++) {
                    JSONObject part = content.optJSONObject(i);
                    if (part != null && "text".equals(part.optString("type"))) sb.append(part.optString("text"));
                }
                return sb.toString().trim();
            }
        }
        throw new Exception("AI থেকে কোনো উত্তর আসেনি");
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) > 0) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
