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
    private static final int KEEP_UNRESOLVED = 3;
    private static final String RANK_PREFS = "swipe_t9_rank_stats";
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
                // v0.2.13: correction log. Keep only swipes that were deleted afterwards,
                // plus the newest few that may still be corrected.
                JSONArray existing = read(context);
                ArrayList<JSONObject> kept = new ArrayList<>();
                for (int i = 0; i < existing.length(); i++) {
                    JSONObject e = existing.optJSONObject(i);
                    if (e == null) continue;
                    boolean recent = i >= existing.length() - KEEP_UNRESOLVED;
                    if (e.optBoolean("deleted", false) || recent) kept.add(e);
                }
                JSONArray entries = new JSONArray();
                for (int i = Math.max(0, kept.size() - LIMIT + 1); i < kept.size(); i++) {
                    entries.put(kept.get(i));
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

    private interface EntryEditor { void edit(JSONObject entry) throws Exception; }

    private static void editEntry(Context context, long id, EntryEditor editor) {
        if (id == -1) return;
        synchronized (LOCK) {
            try {
                JSONArray entries = read(context);
                for (int i = 0; i < entries.length(); i++) {
                    JSONObject entry = entries.optJSONObject(i);
                    if (entry == null || entry.optLong("id", -1) != id) continue;
                    editor.edit(entry);
                    write(context, entries);
                    break;
                }
            } catch (Exception ignored) { }
        }
    }

    private static JSONArray toJson(ArrayList<String> items, int max) {
        JSONArray array = new JSONArray();
        if (items == null) return array;
        for (int i = 0; i < items.size() && i < max; i++) {
            String item = items.get(i);
            if (item != null && item.length() <= 40) array.put(item);
        }
        return array;
    }

    /** Swipe-T9 v0.2.8: what the decoder chose, which readings had words, and all its words. */
    public static void updateDecoder(Context context, long id, String best, ArrayList<String> readings, ArrayList<String> words) {
        editEntry(context, id, entry -> entry
                .put("best", best == null ? "" : best)
                .put("readings", toJson(readings, 12))
                .put("decoderWords", toJson(words, 30)));
    }

    /** Swipe-T9 v0.2.8: the word the user actually meant with this swipe. */
    public static void setTarget(Context context, long id, String word) {
        editEntry(context, id, entry -> entry.put("target", word == null ? "" : word.trim()));
    }

    /** v0.2.9: the word the user took after this swipe. */
    public static void setChosen(Context context, long id, String word, boolean learned) {
        editEntry(context, id, entry -> entry.put("chosen", word == null ? "" : word).put("learned", learned));
    }

    /** v0.2.13: the swiped word was deleted right afterwards. */
    public static void markDeleted(Context context, long id) {
        editEntry(context, id, entry -> entry.put("deleted", true));
    }

    /** v0.2.13: what was written instead of the deleted swipe word. */
    public static void setReplacement(Context context, long id, String word, boolean swiped) {
        editEntry(context, id, entry -> entry.put("replacement", word == null ? "" : word).put("replacementSwiped", swiped));
    }

    /** v0.2.13: counts on which place of the suggestion list the taken word was. */
    public static void countRank(Context context, long id, String word) {
        if (id == -1 || word == null) return;
        JSONObject entry = find(context, id);
        if (entry == null) return;
        int rank = -1;
        for (String key : new String[] {"decoderWords", "shown"}) {
            JSONArray words = entry.optJSONArray(key);
            if (words == null || words.length() == 0) continue;
            for (int i = 0; i < words.length(); i++) {
                if (word.equalsIgnoreCase(words.optString(i))) { rank = i + 1; break; }
            }
            break;
        }
        String bucket = rank == 1 ? "p1" : rank >= 2 && rank <= 3 ? "p2_3" : rank >= 4 ? "p4" : "none";
        android.content.SharedPreferences prefs = context.getSharedPreferences(RANK_PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(bucket, prefs.getInt(bucket, 0) + 1).apply();
    }

    public static int[] getRankStats(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(RANK_PREFS, Context.MODE_PRIVATE);
        return new int[] { prefs.getInt("p1", 0), prefs.getInt("p2_3", 0), prefs.getInt("p4", 0), prefs.getInt("none", 0) };
    }

    public static void clearRankStats(Context context) {
        context.getSharedPreferences(RANK_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }

    /** v0.2.14: nothing was suggested for this swipe; counts as a correction. */
    public static void markNoSuggestion(Context context, long id) {
        editEntry(context, id, entry -> entry.put("deleted", true).put("noSuggestion", true));
    }
}
