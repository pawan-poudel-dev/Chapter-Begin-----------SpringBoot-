package com.aidesktopagent.voice;

import com.aidesktopagent.logging.LoggingService;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Captures PCM audio from the default microphone using {@code javax.sound.sampled}.
 *
 * <p><strong>Privacy:</strong> Audio stays in process memory until a {@link SpeechToText}
 * provider is invoked. Only then may bytes be sent to an external API (e.g. Whisper).
 * Nothing is recorded or uploaded in the background.
 */
public final class MicrophoneManager implements AutoCloseable {

    public static final float SAMPLE_RATE = 16_000f;
    public static final int SAMPLE_SIZE_BITS = 16;
    public static final int CHANNELS = 1;

    private final LoggingService log;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private TargetDataLine line;

    public MicrophoneManager() {
        this(LoggingService.forComponent("Microphone"));
    }

    public MicrophoneManager(LoggingService log) {
        this.log = log != null ? log : LoggingService.forComponent("Microphone");
    }

    public static AudioFormat defaultFormat() {
        return new AudioFormat(SAMPLE_RATE, SAMPLE_SIZE_BITS, CHANNELS, true, false);
    }

    public boolean isAvailable() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, defaultFormat());
            return AudioSystem.isLineSupported(info);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isActive() {
        return active.get();
    }

    /**
     * Record until silence after speech, or until {@code maxMillis}, whichever first.
     * Simple energy-based end-of-utterance detection.
     */
    public byte[] captureUtterance(int maxMillis, int silenceMillis) throws SpeechRecognitionException {
        if (!isAvailable()) {
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.MICROPHONE_UNAVAILABLE,
                    "No microphone available or line not supported");
        }

        AudioFormat format = defaultFormat();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        TargetDataLine localLine;
        try {
            localLine = (TargetDataLine) AudioSystem.getLine(info);
            localLine.open(format);
        } catch (LineUnavailableException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            if (msg.contains("permission") || msg.contains("access")) {
                throw new SpeechRecognitionException(
                        SpeechRecognitionException.Kind.PERMISSION_DENIED,
                        "Microphone permission denied", e);
            }
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.MICROPHONE_UNAVAILABLE,
                    "Cannot open microphone: " + e.getMessage(), e);
        }

        synchronized (this) {
            this.line = localLine;
        }
        localLine.start();
        active.set(true);
        log.info("Microphone ACTIVE — capturing (max " + maxMillis + " ms)");

        ByteArrayOutputStream pcm = new ByteArrayOutputStream();
        byte[] buffer = new byte[localLine.getBufferSize() / 5];
        long start = System.currentTimeMillis();
        long lastLoud = start;
        boolean heardSpeech = false;
        int silenceThreshold = 400; // RMS-ish threshold for 16-bit mono

        try {
            while (System.currentTimeMillis() - start < maxMillis) {
                if (!active.get()) {
                    throw new SpeechRecognitionException(
                            SpeechRecognitionException.Kind.CANCELLED, "Capture cancelled");
                }
                int n = localLine.read(buffer, 0, buffer.length);
                if (n > 0) {
                    pcm.write(buffer, 0, n);
                    int energy = averageAbsEnergy(buffer, n);
                    if (energy > silenceThreshold) {
                        heardSpeech = true;
                        lastLoud = System.currentTimeMillis();
                    } else if (heardSpeech && System.currentTimeMillis() - lastLoud > silenceMillis) {
                        break; // end of utterance
                    }
                }
            }
        } finally {
            stopCapture();
        }

        byte[] raw = pcm.toByteArray();
        if (raw.length < 1600) { // ~50ms
            throw new SpeechRecognitionException(
                    SpeechRecognitionException.Kind.UNCLEAR, "No speech detected");
        }
        return wrapWav(raw, format);
    }

    public void stopCapture() {
        active.set(false);
        synchronized (this) {
            if (line != null) {
                try {
                    line.stop();
                    line.close();
                } catch (Exception ignored) {
                }
                line = null;
            }
        }
        log.info("Microphone inactive");
    }

    @Override
    public void close() {
        stopCapture();
    }

    private static int averageAbsEnergy(byte[] buf, int len) {
        long sum = 0;
        int samples = len / 2;
        for (int i = 0; i + 1 < len; i += 2) {
            int sample = (buf[i + 1] << 8) | (buf[i] & 0xff);
            sum += Math.abs(sample);
        }
        return samples == 0 ? 0 : (int) (sum / samples);
    }

    /** Minimal PCM → WAV header wrapper. */
    static byte[] wrapWav(byte[] pcm, AudioFormat format) {
        int sampleRate = (int) format.getSampleRate();
        int channels = format.getChannels();
        int bits = format.getSampleSizeInBits();
        int byteRate = sampleRate * channels * bits / 8;
        int dataLen = pcm.length;
        byte[] wav = new byte[44 + dataLen];
        // RIFF header
        System.arraycopy(new byte[]{'R', 'I', 'F', 'F'}, 0, wav, 0, 4);
        writeInt(wav, 4, 36 + dataLen);
        System.arraycopy(new byte[]{'W', 'A', 'V', 'E'}, 0, wav, 8, 4);
        System.arraycopy(new byte[]{'f', 'm', 't', ' '}, 0, wav, 12, 4);
        writeInt(wav, 16, 16); // PCM chunk size
        writeShort(wav, 20, (short) 1); // PCM
        writeShort(wav, 22, (short) channels);
        writeInt(wav, 24, sampleRate);
        writeInt(wav, 28, byteRate);
        writeShort(wav, 32, (short) (channels * bits / 8));
        writeShort(wav, 34, (short) bits);
        System.arraycopy(new byte[]{'d', 'a', 't', 'a'}, 0, wav, 36, 4);
        writeInt(wav, 40, dataLen);
        System.arraycopy(pcm, 0, wav, 44, dataLen);
        return wav;
    }

    private static void writeInt(byte[] b, int off, int v) {
        b[off] = (byte) (v);
        b[off + 1] = (byte) (v >> 8);
        b[off + 2] = (byte) (v >> 16);
        b[off + 3] = (byte) (v >> 24);
    }

    private static void writeShort(byte[] b, int off, short v) {
        b[off] = (byte) (v);
        b[off + 1] = (byte) (v >> 8);
    }
}
