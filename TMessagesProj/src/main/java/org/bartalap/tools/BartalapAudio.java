package org.bartalap.tools;

import android.media.AudioRecord;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;

/**
 * Bartalap: optional background-noise reduction for recorded voice messages,
 * using the phone's built-in noise suppressor and auto volume.
 */
public final class BartalapAudio {

    private BartalapAudio() {}

    private static NoiseSuppressor ns;
    private static AutomaticGainControl agc;

    public static boolean isNoiseFilterEnabled() {
        return AiClient.prefs().getBoolean("voice_noise_filter", true);
    }

    public static void setNoiseFilterEnabled(boolean on) {
        AiClient.prefs().edit().putBoolean("voice_noise_filter", on).apply();
    }

    public static void applyNoiseFilter(AudioRecord recorder) {
        try {
            release();
            if (recorder == null || !isNoiseFilterEnabled()) return;
            int session = recorder.getAudioSessionId();
            if (NoiseSuppressor.isAvailable()) {
                ns = NoiseSuppressor.create(session);
                if (ns != null) ns.setEnabled(true);
            }
            if (AutomaticGainControl.isAvailable()) {
                agc = AutomaticGainControl.create(session);
                if (agc != null) agc.setEnabled(true);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void release() {
        try {
            if (ns != null) ns.release();
        } catch (Throwable ignored) {
        }
        try {
            if (agc != null) agc.release();
        } catch (Throwable ignored) {
        }
        ns = null;
        agc = null;
    }
}
