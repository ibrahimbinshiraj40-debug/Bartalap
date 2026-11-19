package org.bartalap.tools;

import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.io.File;
import java.io.FileInputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bartalap: type Bengali text -> phone's TTS voice -> real Telegram voice message (OGG/Opus with waveform).
 * Uses the device's text-to-speech engine (Google TTS has Bengali), then Telegram's own Opus encoder.
 */
public final class VoiceNoteMaker {

    private VoiceNoteMaker() {}

    public interface Callback {
        void onDone(boolean ok, String error);
    }

    private static final int OUT_RATE = 48000;

    /** Runs in background; callback on UI thread. */
    public static void makeAndSend(int account, long dialogId, String text, float speed, float pitch, Callback cb) {
        new Thread(() -> {
            String err = null;
            try {
                File wav = new File(ApplicationLoader.applicationContext.getCacheDir(), "bartalap_tts_" + System.currentTimeMillis() + ".wav");
                synthesize(text, wav, speed, pitch);
                short[] pcm = readWavAsMono48k(wav);
                wav.delete();
                if (pcm == null || pcm.length < OUT_RATE / 4) throw new Exception("ভয়েস তৈরি হয়নি। ফোনে Google Text-to-Speech-এ বাংলা ভাষা আছে কিনা দেখুন।");
                TLRPC.TL_document doc = new TLRPC.TL_document();
                doc.file_reference = new byte[0];
                doc.dc_id = Integer.MIN_VALUE;
                doc.id = SharedConfig.getLastLocalId();
                doc.user_id = UserConfig.getInstance(account).getClientUserId();
                doc.mime_type = "audio/ogg";
                SharedConfig.saveConfig();
                File ogg = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO), System.currentTimeMillis() + "_" + FileLoader.getAttachFileName(doc));
                FileLoader.getDirectory(FileLoader.MEDIA_DIR_AUDIO).mkdirs();
                encodeOpus(pcm, ogg);
                if (!ogg.exists() || ogg.length() == 0) throw new Exception("ভয়েস ফাইল বানানো যায়নি");

                doc.date = ConnectionsManager.getInstance(account).getCurrentTime();
                doc.size = (int) ogg.length();
                TLRPC.TL_documentAttributeAudio attr = new TLRPC.TL_documentAttributeAudio();
                attr.voice = true;
                attr.waveform = MediaController.getWaveform(ogg.getAbsolutePath());
                if (attr.waveform != null) attr.flags |= 4;
                attr.duration = pcm.length / (double) OUT_RATE;
                doc.attributes.add(attr);

                String path = ogg.getAbsolutePath();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        SendMessagesHelper.SendMessageParams params = SendMessagesHelper.SendMessageParams.of(doc, null, path, dialogId,
                                null, null, null, null, null, null, true, 0, 0, 0, null, null, false);
                        SendMessagesHelper.getInstance(account).sendMessage(params);
                        cb.onDone(true, null);
                    } catch (Throwable t) {
                        FileLog.e("Bartalap voice send error", t);
                        cb.onDone(false, "পাঠানো যায়নি: " + t.getMessage());
                    }
                });
                return;
            } catch (Throwable e) {
                FileLog.e("Bartalap voice make error", e);
                err = e.getMessage() == null ? "ভয়েস তৈরি করা যায়নি" : e.getMessage();
            }
            final String error = err;
            AndroidUtilities.runOnUIThread(() -> cb.onDone(false, error));
        }).start();
    }

    // ------------------------------------------------------------ TTS

    private static void synthesize(String text, File out, float speed, float pitch) throws Exception {
        final CountDownLatch ready = new CountDownLatch(1);
        final CountDownLatch done = new CountDownLatch(1);
        final int[] status = {TextToSpeech.ERROR};
        final boolean[] failed = {false};
        final TextToSpeech[] holder = new TextToSpeech[1];
        AndroidUtilities.runOnUIThread(() -> holder[0] = new TextToSpeech(ApplicationLoader.applicationContext, s -> {
            status[0] = s;
            ready.countDown();
        }));
        if (!ready.await(15, TimeUnit.SECONDS) || status[0] != TextToSpeech.SUCCESS || holder[0] == null) {
            throw new Exception("ফোনের Text-to-Speech চালু হয়নি");
        }
        TextToSpeech tts = holder[0];
        try {
            int lang = tts.setLanguage(new Locale("bn", "BD"));
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                lang = tts.setLanguage(new Locale("bn", "IN"));
            }
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED) {
                throw new Exception("ফোনে বাংলা ভয়েস নেই। Settings → Text-to-speech → Google → বাংলা ভয়েস ডাউনলোড করুন।");
            }
            tts.setSpeechRate(speed);
            tts.setPitch(pitch);
            tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                @Override public void onStart(String id) {}
                @Override public void onDone(String id) { done.countDown(); }
                @Override public void onError(String id) {
                    failed[0] = true;
                    done.countDown();
                }
            });
            Bundle params = new Bundle();
            int r = tts.synthesizeToFile(text, params, out, "bartalap_voice");
            if (r != TextToSpeech.SUCCESS) throw new Exception("ভয়েস তৈরি শুরু করা যায়নি");
            if (!done.await(90, TimeUnit.SECONDS) || failed[0]) throw new Exception("ভয়েস তৈরি হতে সমস্যা হয়েছে");
        } finally {
            try {
                tts.shutdown();
            } catch (Throwable ignored) {
            }
        }
    }

    // ------------------------------------------------------------ WAV -> PCM 48k mono

    private static int le16(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8);
    }

    private static int le32(byte[] b, int o) {
        return (b[o] & 0xff) | ((b[o + 1] & 0xff) << 8) | ((b[o + 2] & 0xff) << 16) | ((b[o + 3] & 0xff) << 24);
    }

    static short[] readWavAsMono48k(File f) throws Exception {
        byte[] all;
        try (RandomAccessFile raf = new RandomAccessFile(f, "r")) {
            all = new byte[(int) raf.length()];
            raf.readFully(all);
        }
        if (all.length < 44) return null;
        int channels = 1, rate = 22050, bits = 16, dataOff = -1, dataLen = 0;
        int p = 12;
        while (p + 8 <= all.length) {
            String id = new String(all, p, 4, "US-ASCII");
            int size = le32(all, p + 4);
            if (id.equals("fmt ")) {
                channels = le16(all, p + 10);
                rate = le32(all, p + 12);
                bits = le16(all, p + 22);
            } else if (id.equals("data")) {
                dataOff = p + 8;
                dataLen = size <= 0 ? all.length - dataOff : Math.min(size, all.length - dataOff);
                break;
            }
            p += 8 + size + (size & 1);
        }
        if (dataOff < 0 || bits != 16 || channels < 1 || rate <= 0) return null;
        int frames = dataLen / (2 * channels);
        short[] mono = new short[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                sum += (short) le16(all, dataOff + (i * channels + c) * 2);
            }
            mono[i] = (short) (sum / channels);
        }
        if (rate == OUT_RATE) return mono;
        int outLen = (int) ((long) frames * OUT_RATE / rate);
        short[] out = new short[outLen];
        double step = rate / (double) OUT_RATE;
        for (int i = 0; i < outLen; i++) {
            double src = i * step;
            int i0 = (int) src;
            int i1 = Math.min(i0 + 1, frames - 1);
            double frac = src - i0;
            out[i] = (short) Math.round(mono[i0] * (1 - frac) + mono[i1] * frac);
        }
        return out;
    }

    // ------------------------------------------------------------ Opus via Telegram's own encoder

    private static final Object ENCODER_LOCK = new Object();

    private static void encodeOpus(short[] pcm, File out) throws Exception {
        synchronized (ENCODER_LOCK) {
            MediaController mc = MediaController.getInstance();
            Method start = MediaController.class.getDeclaredMethod("startRecord", String.class, int.class);
            Method write = MediaController.class.getDeclaredMethod("writeFrame", ByteBuffer.class, int.class);
            Method stop = MediaController.class.getDeclaredMethod("stopRecord");
            start.setAccessible(true);
            write.setAccessible(true);
            stop.setAccessible(true);
            Object ok = start.invoke(mc, out.getAbsolutePath(), OUT_RATE);
            if (ok instanceof Integer && (Integer) ok == 0) throw new Exception("ভয়েস এনকোডার চালু হয়নি");
            ByteBuffer frame = ByteBuffer.allocateDirect(1920);
            frame.order(ByteOrder.nativeOrder());
            int samplesPerFrame = 960;
            try {
                for (int i = 0; i < pcm.length; i += samplesPerFrame) {
                    frame.clear();
                    int n = Math.min(samplesPerFrame, pcm.length - i);
                    for (int k = 0; k < samplesPerFrame; k++) {
                        frame.putShort(k < n ? pcm[i + k] : 0);
                    }
                    frame.flip();
                    write.invoke(mc, frame, frame.limit());
                }
            } finally {
                stop.invoke(mc);
            }
        }
    }

    /** Unused helper kept for simple file reads on old devices. */
    static byte[] readAll(File f) throws Exception {
        try (FileInputStream in = new FileInputStream(f)) {
            byte[] b = new byte[(int) f.length()];
            int off = 0;
            while (off < b.length) {
                int r = in.read(b, off, b.length - off);
                if (r < 0) break;
                off += r;
            }
            return b;
        }
    }
}
