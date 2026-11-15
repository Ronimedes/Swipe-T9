package io.github.sspanak.tt9.ui.main.swipe;

import android.content.Context;
import androidx.preference.PreferenceManager;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

/**
 * Local, opt-in gesture diagnostics. Never store surrounding text, application
 * identity, or passwords. Files in getNoBackupFilesDir() are excluded from Android backup.
 * All writes are serialized because dictionary callbacks can arrive asynchronously.
 */
public final class SwipeT9History {
    public static final String ENABLED = "swipe_t9_debug_history";
    private static final String FILE = "swipe_t9_history.json";
    private static final int LIMIT = 100;
    private static final int MAX_POINTS = 80;
    private static final Object LOCK = new Object();

    private SwipeT9History() { }

    public static boolean enabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED, false);
    }

    private static File file(Context context) {
        return new File(context.getNoBackupFilesDir(), FILE);
    }

    private static JSONArray read(Context context) {
        try {
            File input = file(context);
            if (!input.isFile() || input.length() > 600_000) return new JSONArray();
            try (FileInputStream stream = new FileInputStream(input)) {
                byte[] bytes = new byte[(int) input.length()];
                int read = 0;
                while (read < bytes.length) {
                    int n = stream.read(bytes, read, bytes.length - read);
                    if (n < 0) break;
                    read += n;
                }
                return new JSONArray(new String(bytes, 0, read, StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) { return new JSONArray(); }
    }

    private static void write(Context context, JSONArray entries) {
        try (FileOutputStream out = new FileOutputStream(file(context), false)) {
            out.write(entries.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) { /* Diagnostics must never interrupt typing. */ }
    }

    public static void clear(Context context) {
        synchronized (LOCK) {
            //noinspection ResultOfMethodCallIgnored
            file(context).delete();
        }
    }

    public static ArrayList<JSONObject> list(Context context) {
        synchronized (LOCK) {
            JSONArray history = read(context);
            ArrayList<JSONObject> result = new ArrayList<>();
            for (int i = history.length()-1; i >= 0; i--) {
                JSONObject item = history.optJSONObject(i);
                if (item != null) result.add(item);
            }
            return result;
        }
    }

    /** Returns a unique record ID, or -1 when diagnostics are off. */
    public static long add(Context context, String digits, float[] trace, String loops,
                           int startKey, int endKey, boolean recognized, int languageId) {
        if (!enabled(context)) return -1;
        if (digits == null || digits.length() > 32 || trace == null) return -1;
        synchronized (LOCK) {
            try {
                JSONArray existing = read(context);
                JSONArray entries = new JSONArray();
                for (int i = Math.max(0, existing.length() - LIMIT + 1); i < existing.length(); i++) {
                    entries.put(existing.get(i));
                }
                long id = System.currentTimeMillis() * 1000 + System.nanoTime() % 1000;
                JSONArray points = new JSONArray();
                int count = trace.length / 2;
                int stored = Math.min(MAX_POINTS, count);
                for (int i = 0; i < stored; i++) {
                    int index = stored == 1 ? 0 : i * (count-1) / (stored-1);
                    points.put(Math.round(trace[index*2] * 10000f) / 10000.0);
                    points.put(Math.round(trace[index*2+1] * 10000f) / 10000.0);
                }
                JSONObject item = new JSONObject()
                        .put("id", id).put("time", System.currentTimeMillis())
                        .put("digits", digits).put("start", startKey).put("end", endKey)
                        .put("loops", loops == null ? "" : loops)
                        .put("recognized", recognized).put("language", languageId).put("path", points)
                        .put("shown", new JSONArray()).put("dictionary", new JSONArray()).put("dictionaryChecked", false);
                entries.put(item);
                write(context, entries);
                return id;
            } catch (Exception ignored) { return -1; }
        }
    }

    public static JSONObject find(Context context, long id) {
        synchronized (LOCK) {
            JSONArray entries = read(context);
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.optJSONObject(i);
                if (item != null && item.optLong("id", -1) == id) return item;
            }
            return null;
        }
    }

    /** Candidate words are stored only if they belong to this recorded swipe. */
    public static void updateCandidates(Context context, long id, String which, ArrayList<String> candidates) {
        if (id == -1 || !("shown".equals(which) || "dictionary".equals(which))) return;
        synchronized (LOCK) {
            try {
                JSONArray entries = read(context);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null || entry.optLong("id", -1) != id) continue;
                    JSONArray words = new JSONArray();
                    if (candidates != null) {
                        int max = Math.min(8, candidates.size());
                        for (int j = 0; j < max; j++) {
                            String word = candidates.get(j);
                            if (word != null && word.length() <= 40) words.put(word);
                        }
                    }
                    entry.put(which, words);
                    if ("dictionary".equals(which)) entry.put("dictionaryChecked", true);
                    write(context, entries);
                    break;
                }
            } catch (Exception ignored) { }
        }
    }
}
