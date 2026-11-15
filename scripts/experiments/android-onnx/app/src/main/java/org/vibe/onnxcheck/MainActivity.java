package org.vibe.onnxcheck;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/** Isolated first-patch PCM16 WAV and graph parity test; no app integration. */
public final class MainActivity extends Activity {
    private static final String TAG = "VibeOnnxSmoke";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(this::runTest).start();
    }

    private byte[] asset(String name) throws Exception {
        try (InputStream in = getAssets().open(name); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int count;
            while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
            return out.toByteArray();
        }
    }

    private File copyModel(String name) throws Exception {
        File file = new File(getCacheDir(), name);
        try (InputStream in = getAssets().open(name); FileOutputStream out = new FileOutputStream(file)) {
            byte[] buf = new byte[8192];
            int count;
            while ((count = in.read(buf)) != -1) out.write(buf, 0, count);
        }
        return file;
    }

    private FloatBuffer floats(String name, int expected) throws Exception {
        byte[] data = asset(name);
        if (data.length != expected * 4) throw new IllegalArgumentException("Bad float fixture size: " + name);
        return ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
    }

    private float[] infer(OrtEnvironment env, OrtSession backbone, OrtSession head, FloatBuffer input) throws Exception {
        try (OnnxTensor patch = OnnxTensor.createTensor(env, input, new long[]{1, 128, 96});
             OrtSession.Result backboneOutput = backbone.run(Collections.singletonMap("melspectrogram", patch))) {
            float[][] embeddings = (float[][]) backboneOutput.get("embeddings").get().getValue();
            if (embeddings.length != 1 || embeddings[0].length != 1280) throw new IllegalStateException("Unexpected embedding shape");
            try (OnnxTensor embedding = OnnxTensor.createTensor(env, embeddings);
                 OrtSession.Result output = head.run(Collections.singletonMap("embeddings", embedding))) {
                float[][] scores = (float[][]) output.get("activations").get().getValue();
                if (scores.length != 1 || scores[0].length != 56) throw new IllegalStateException("Unexpected score shape");
                return scores[0];
            }
        }
    }

    private double maxScoreError(float[] scores) throws Exception {
        FloatBuffer expected = floats("scores.f32", 56);
        double error = 0;
        for (float score : scores) {
            if (Float.isNaN(score) || Float.isInfinite(score) || score < 0 || score > 1) throw new IllegalStateException("Invalid score");
            error = Math.max(error, Math.abs(score - expected.get()));
        }
        return error;
    }

    private void runTest() {
        JSONObject result = new JSONObject();
        try {
            long start = System.nanoTime();
            File backbone = copyModel("backbone.onnx");
            File head = copyModel("head.onnx");
            OrtEnvironment env = OrtEnvironment.getEnvironment();
            try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
                 OrtSession backboneSession = env.createSession(backbone.getAbsolutePath(), opts);
                 OrtSession headSession = env.createSession(head.getAbsolutePath(), opts)) {
                double graphError = maxScoreError(infer(env, backboneSession, headSession, floats("patch.f32", 128 * 96)));
                float[] generated = EffnetPreprocessor.firstPatchFromWav(asset("audio.wav"));
                FloatBuffer expectedPatch = floats("patch.f32", 128 * 96);
                double melError = 0;
                for (float value : generated) melError = Math.max(melError, Math.abs(value - expectedPatch.get()));
                float[] wavScores = infer(env, backboneSession, headSession, FloatBuffer.wrap(generated));
                double endToEndError = maxScoreError(wavScores);
                JSONArray labels = new JSONObject(new String(asset("metadata.json"), java.nio.charset.StandardCharsets.UTF_8)).getJSONArray("classes");
                if (labels.length() != wavScores.length) throw new IllegalStateException("Unexpected label count");
                JSONArray top = new JSONArray();
                boolean[] chosen = new boolean[wavScores.length];
                for (int rank = 0; rank < 5; rank++) {
                    int best = -1;
                    for (int i = 0; i < wavScores.length; i++) if (!chosen[i] && (best < 0 || wavScores[i] > wavScores[best])) best = i;
                    chosen[best] = true;
                    top.put(new JSONObject().put("label", labels.getString(best)).put("score", wavScores[best]));
                }
                result.put("passed", graphError < 0.0001 && melError < 0.0001 && endToEndError < 0.0001);
                result.put("graph_score_max_absolute_error", graphError);
                result.put("wav_mel_max_absolute_error", melError);
                result.put("wav_to_score_max_absolute_error", endToEndError);
                result.put("embedding_shape", "1x1280");
                result.put("score_shape", "1x56");
                result.put("top_labels", top);
                result.put("elapsed_seconds_including_copy_setup_and_two_inferences", (System.nanoTime() - start) / 1e9);
            }
        } catch (Exception error) {
            try { result.put("passed", false); result.put("error", error.toString()); } catch (Exception ignored) { }
            Log.e(TAG, "Graph smoke test failed", error);
        }
        try {
            try (FileOutputStream out = openFileOutput("result.tmp", MODE_PRIVATE)) {
                out.write(result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            if (!new File(getFilesDir(), "result.tmp").renameTo(new File(getFilesDir(), "result.json"))) {
                throw new IllegalStateException("Could not publish result");
            }
        } catch (Exception error) { Log.e(TAG, "Could not save result", error); }
        Log.i(TAG, result.toString());
    }
}
