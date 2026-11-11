package org.vibe.onnxcheck;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/** Proof-of-concept for the first 128 EffNet frames, not a production decoder.
 * Implements the documented Essentia TensorflowInputMusiCNN pipeline.
 */
public final class EffnetPreprocessor {
    private static final int N = 512, BANDS = 96, FRAMES = 128, HOP = 256;
    private EffnetPreprocessor() {}

    /** Return 128x96 row-major log-mel values from a mono 16-kHz PCM16 WAV. */
    public static float[] firstPatchFromWav(byte[] wav) {
        ByteBuffer b = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN);
        if (wav.length < 12 || !tag(wav, 0).equals("RIFF") || !tag(wav, 8).equals("WAVE"))
            throw new IllegalArgumentException("Expected RIFF/WAVE");
        int channels = 0, rate = 0, bits = 0, format = 0, dataStart = -1, dataLength = 0;
        for (int pos = 12; pos + 8 <= wav.length;) {
            String id = tag(wav, pos);
            int len = b.getInt(pos + 4);
            int payload = pos + 8;
            if (len < 0 || (long) payload + len > wav.length) throw new IllegalArgumentException("Invalid WAV chunk");
            if (id.equals("fmt ") && len >= 16) {
                format = b.getShort(payload) & 0xffff; channels = b.getShort(payload + 2) & 0xffff;
                rate = b.getInt(payload + 4); bits = b.getShort(payload + 14) & 0xffff;
            } else if (id.equals("data")) { dataStart = payload; dataLength = len; }
            long next = (long)payload + len + (len & 1);
            if (next > wav.length) break;
            pos = (int)next;
        }
        if (format != 1 || channels != 1 || rate != 16000 || bits != 16 || dataStart < 0 || dataLength % 2 != 0)
            throw new IllegalArgumentException("Expected mono PCM16 at 16 kHz");
        int samples = dataLength / 2;
        if (samples < (FRAMES - 1) * HOP + N / 2) throw new IllegalArgumentException("Audio too short for one full patch");
        double[] frequencies = new double[BANDS + 2];
        double logStep = Math.log(6.4) / 27.0;
        double highMel = 15.0 + Math.log(8.0) / logStep;
        for (int i = 0; i < frequencies.length; i++) {
            double m = highMel * i / (BANDS + 1);
            frequencies[i] = m < 15.0 ? m / 0.015 : 1000.0 * Math.exp(logStep * (m - 15.0));
        }
        double[][] filter = new double[BANDS][N / 2 + 1];
        for (int i = 0; i < BANDS; i++) {
            double lo = frequencies[i], mid = frequencies[i + 1], hi = frequencies[i + 2];
            for (int k = 0; k <= N / 2; k++) {
                double hz = k * 16000.0 / N;
                filter[i][k] = Math.max(0.0, Math.min((hz - lo) / (mid - lo), (hi - hz) / (hi - mid))) * 2.0 / (hi - lo);
            }
        }
        float[] out = new float[FRAMES * BANDS];
        double[] real = new double[N], imag = new double[N], power = new double[N / 2 + 1];
        for (int f = 0; f < FRAMES; f++) {
            int start = f * HOP - N / 2;
            for (int n = 0; n < N; n++) {
                int idx = start + n;
                double x = idx < 0 || idx >= samples ? 0.0 : b.getShort(dataStart + 2 * idx) / 32768.0;
                real[n] = x * (0.5 - 0.5 * Math.cos(2 * Math.PI * n / (N - 1)));
                imag[n] = 0.0;
            }
            fft(real, imag);
            for (int k = 0; k <= N / 2; k++) power[k] = real[k] * real[k] + imag[k] * imag[k];
            for (int band = 0; band < BANDS; band++) {
                double sum = 0.0;
                for (int k = 0; k <= N / 2; k++) sum += power[k] * filter[band][k];
                out[f * BANDS + band] = (float)Math.log10(1.0 + 10000.0 * sum);
            }
        }
        return out;
    }

    private static String tag(byte[] bytes, int offset) {
        return new String(bytes, offset, 4, StandardCharsets.US_ASCII);
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            while ((j & bit) != 0) { j ^= bit; bit >>= 1; }
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double angle = -2.0 * Math.PI / len, wr0 = Math.cos(angle), wi0 = Math.sin(angle);
            for (int i = 0; i < n; i += len) {
                double wr = 1.0, wi = 0.0;
                for (int j = 0; j < len / 2; j++) {
                    int a = i + j, c = a + len / 2;
                    double tr = wr * re[c] - wi * im[c], ti = wr * im[c] + wi * re[c];
                    re[c] = re[a] - tr; im[c] = im[a] - ti; re[a] += tr; im[a] += ti;
                    double nr = wr * wr0 - wi * wi0; wi = wr * wi0 + wi * wr0; wr = nr;
                }
            }
        }
    }
}
