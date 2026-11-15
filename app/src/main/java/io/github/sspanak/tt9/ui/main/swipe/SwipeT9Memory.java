package io.github.sspanak.tt9.ui.main.swipe;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Opt-in, on-device supervised swipe examples.
 * A word is stored ONLY after the user deliberately accepts a suggestion.
 * Never write surrounding text, application names, or unconfirmed predictions.
 */
public final class SwipeT9Memory {
    private static final String FILE = "swipe_t9_examples_private";
    private static final String DATA = "examples_v1";
    private static final int SAMPLES = 16;
    private static final int MAX_EXAMPLES = 96;
    private static final int MAX_PER_WORD = 8;
    private static final float MATCH_LIMIT = .063f;

    private SwipeT9Memory() {}

    public static boolean isEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("swipe_t9_learn", false);
    }

    public static void clear(Context context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(DATA).apply();
    }

    private static final class Example {
        final String language;
        final String word;
        final float[] points;
        Example(String language, String word, float[] points) {
            this.language = language;
            this.word = word;
            this.points = points;
        }
    }

    /** Equal-distance sampling makes fast and slow drawings comparable. */
    private static float[] resample(float[] raw) {
        if (raw == null || raw.length < 4 || raw.length % 2 != 0) return null;
        final int n = raw.length / 2;
        float[] distance = new float[n];
        for (int i = 1; i < n; i++) {
            distance[i] = distance[i-1] + (float) Math.hypot(
                    raw[2*i] - raw[2*(i-1)], raw[2*i+1] - raw[2*(i-1)+1]);
        }
        if (distance[n-1] < .035f) return null;
        float[] result = new float[SAMPLES * 2];
        int segment = 1;
        for (int i = 0; i < SAMPLES; i++) {
            float target = distance[n-1] * i / (SAMPLES - 1);
            while (segment < n-1 && distance[segment] < target) segment++;
            float span = distance[segment] - distance[segment-1];
            float t = span <= 0 ? 0 : (target - distance[segment-1]) / span;
            result[2*i] = raw[2*(segment-1)] * (1-t) + raw[2*segment] * t;
            result[2*i+1] = raw[2*(segment-1)+1] * (1-t) + raw[2*segment+1] * t;
        }
        return result;
    }

    private static ArrayList<Example> load(Context context) {
        ArrayList<Example> examples = new ArrayList<>();
        String json = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(DATA, "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = Math.max(0, array.length() - MAX_EXAMPLES); i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                JSONArray p = o.getJSONArray("p");
                if (p.length() != SAMPLES * 2) continue;
                float[] points = new float[SAMPLES * 2];
                for (int j = 0; j < points.length; j++) points[j] = (float) p.getDouble(j);
                examples.add(new Example(o.getString("l"), o.getString("w"), points));
            }
        } catch (Exception ignored) {
            // Corrupted or old data must never block the keyboard.
        }
        return examples;
    }

    private static boolean isWord(String word) {
        return word != null && word.length() >= 2 && word.length() <= 32
                && word.codePoints().allMatch(Character::isLetter);
    }

    public static void remember(Context context, String language, String word, float[] raw) {
        if (!isEnabled(context) || !isWord(word) || language == null) return;
        float[] points = resample(raw);
        if (points == null) return;
        String normalized = word;
        ArrayList<Example> examples = load(context);
        int count = 0;
        for (int i = examples.size()-1; i >= 0; i--) {
            Example e = examples.get(i);
            if (e.word.equals(normalized) && e.language.equals(language) && ++count >= MAX_PER_WORD) {
                examples.remove(i);
            }
        }
        examples.add(new Example(language, normalized, points));
        while (examples.size() > MAX_EXAMPLES) examples.remove(0);
        JSONArray data = new JSONArray();
        try {
            for (Example e : examples) {
                JSONArray p = new JSONArray();
                for (float coordinate : e.points) p.put(Math.round(coordinate * 10000f) / 10000.0);
                data.put(new JSONObject().put("l", e.language).put("w", e.word).put("p", p));
            }
            context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(DATA, data.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static float distance(float[] a, float[] b) {
        float sum = 0f;
        for (int i = 0; i < SAMPLES; i++) {
            float dx = a[2*i] - b[2*i], dy = a[2*i+1] - b[2*i+1];
            sum += (float) Math.hypot(dx, dy);
        }
        return sum / SAMPLES;
    }

    /** Only trust a strong and unambiguous match confirmed by three separate selections. */
    public static String match(Context context, String language, float[] raw) {
        if (!isEnabled(context) || language == null) return null;
        float[] points = resample(raw);
        if (points == null) return null;
        HashMap<String, Integer> nearCount = new HashMap<>();
        HashMap<String, Float> best = new HashMap<>();
        for (Example e : load(context)) {
            if (!language.equals(e.language)) continue;
            float d = distance(points, e.points);
            if (d < MATCH_LIMIT) {
                nearCount.put(e.word, nearCount.getOrDefault(e.word, 0) + 1);
                best.put(e.word, Math.min(best.getOrDefault(e.word, 1f), d));
            }
        }
        String winner = null;
        float winningScore = Float.MAX_VALUE, runnerUp = Float.MAX_VALUE;
        for (String word : nearCount.keySet()) {
            if (nearCount.get(word) < 3) continue;
            float score = best.get(word);
            if (score < winningScore) {
                runnerUp = winningScore;
                winningScore = score;
                winner = word;
            } else {
                runnerUp = Math.min(runnerUp, score);
            }
        }
        return winner != null && (runnerUp == Float.MAX_VALUE || runnerUp - winningScore > .015f)
                ? winner : null;
    }
}
