package io.github.sspanak.tt9.preferences.screens.keypad;

import androidx.annotation.Nullable;
import android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import io.github.sspanak.tt9.db.DataStore;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import org.json.JSONArray;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9Memory;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9History;
import io.github.sspanak.tt9.preferences.PreferencesActivity;
import io.github.sspanak.tt9.preferences.custom.EnhancedDropDownPreference;
import io.github.sspanak.tt9.preferences.screens.BaseScreenFragment;

public class KeyPadScreen extends BaseScreenFragment {
	final public static String NAME = "KeyPad";

	public KeyPadScreen() { super(); }
	public KeyPadScreen(@Nullable PreferencesActivity activity) { super(activity); }

	@Override public String getName() { return NAME; }
	@Override protected int getTitle() { return R.string.pref_category_keypad; }
	@Override protected int getXml() { return R.xml.prefs_screen_keypad; }

	@Override
	protected void onCreate() {
		createTypingSection();
		createPhysicalKeysSection();
		createVirtualKeysSection();
		resetFontSize(true);
	}

    private void createTypingSection() {
        Preference history = findPreference("swipe_t9_history");
        if (history != null) history.setOnPreferenceClickListener(p -> {
            showHistory();
            return true;
        });
		Preference update = findPreference("swipe_t9_check_update");
		if (update != null) {
			update.setSummary("Installiert: " + io.github.sspanak.tt9.BuildConfig.VERSION_NAME
				+ ". Öffnet die Download-Seite im Browser. Die Tastatur selbst hat keinen Internetzugang.");
			update.setOnPreferenceClickListener(p -> {
				try {
					android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW,
						android.net.Uri.parse("https://github.com/Ronimedes/Swipe-T9/releases/latest"));
					intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(intent);
				} catch (Exception ignored) { }
				return true;
			});
		}
		Preference blocklist = findPreference("swipe_t9_blocklist");
		if (blocklist != null) blocklist.setOnPreferenceClickListener(p -> {
			showBlocklist();
			return true;
		});
		Preference clear = findPreference("swipe_t9_clear_learning");
		if (clear != null) clear.setOnPreferenceClickListener(p -> {
			SwipeT9Memory.clear(requireContext());
			return true;
		});
		EnhancedDropDownPreference dropdown = findPreference(DropDownOneKeyEmoji.NAME);
		if (dropdown != null && activity != null) dropdown.populate(activity.getSettings()).preview();

	}

    /** v0.2.9: hidden words. Tap one to bring it back; add new ones by hand. */
    private void showBlocklist() {
        Context context = requireContext();
        java.util.List<String> words = io.github.sspanak.tt9.ui.main.swipe.SwipeT9Blocklist.getAll(context);
        AlertDialog.Builder builder = new AlertDialog.Builder(context)
                .setTitle("Ausgeblendete Wörter (" + words.size() + ")")
                .setNeutralButton("Wort ausblenden…", (d, w) -> askBlockWord())
                .setPositiveButton("Schließen", null);
        if (words.isEmpty()) {
            builder.setMessage("Noch keine. Halte in der Vorschlagsleiste ein Wort gedrückt und wähle „Ausblenden“.");
        } else {
            CharSequence[] items = words.toArray(new CharSequence[0]);
            builder.setItems(items, (d, which) -> new AlertDialog.Builder(context)
                    .setTitle("„" + words.get(which) + "“ wieder vorschlagen?")
                    .setNegativeButton("Nein", (x, y) -> showBlocklist())
                    .setPositiveButton("Wiederherstellen", (x, y) -> {
                        io.github.sspanak.tt9.ui.main.swipe.SwipeT9Blocklist.unblock(context, words.get(which));
                        showBlocklist();
                    }).show());
        }
        builder.show();
    }

    private void askBlockWord() {
        Context context = requireContext();
        android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine(true);
        input.setHint("z. B. VDSt");
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(context);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(context).setTitle("Wort ausblenden")
                .setView(box)
                .setNegativeButton("Abbrechen", (d, w) -> showBlocklist())
                .setPositiveButton("Ausblenden", (d, w) -> {
                    String word = input.getText().toString().trim();
                    if (!word.isEmpty()) io.github.sspanak.tt9.ui.main.swipe.SwipeT9Blocklist.block(context, word);
                    showBlocklist();
                }).show();
    }

    /** Display entries in reverse chronological order, never the surrounding typed text. */
    private void showHistory() {
        ArrayList<JSONObject> entries = SwipeT9History.list(requireContext());
        if (entries.isEmpty()) {
            new AlertDialog.Builder(requireContext()).setTitle("Wischprotokoll")
                    .setMessage("Noch keine Wischbewegungen aufgezeichnet. Aktiviere zuerst 'Wischprotokoll aufzeichnen'.")
                    .setPositiveButton("OK", null).show();
            return;
        }
        CharSequence[] names = new CharSequence[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            JSONObject e = entries.get(i);
            String time = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(e.optLong("time", 0)));
            // v0.2.8: show what the keyboard really offered, not a lookup that never ran.
            JSONArray offered = e.optJSONArray("decoderWords");
            if (offered == null || offered.length() == 0) offered = e.optJSONArray("shown");
            String words = printWords(offered);
            String target = e.optString("target", "");
            String chosen = e.optString("chosen", "");
            String chosenInfo = chosen.isEmpty() ? "" : "\n   gewählt: " + chosen + (e.optBoolean("learned") ? "  ✓ gelernt" : "");
            String targetInfo = target.isEmpty() ? "" : "\n   gemeint: " + target + (containsWord(e, target) ? "  ✓" : "  ✗");
            names[i] = time + "  " + e.optString("digits", "?") +
                    (e.optBoolean("recognized") ? "" : " (nicht erkannt)") +
                    "\n   → " + (words.isEmpty() ? "keine Vorschläge" : words) + chosenInfo + targetInfo;
        }
        new AlertDialog.Builder(requireContext()).setTitle("Wischprotokoll (" + entries.size() + "/100)")
                .setItems(names, (dialog, which) -> showHistoryItem(entries.get(which)))
                .setNegativeButton("Schließen", null)
                .setNeutralButton("Alle löschen", (dialog, which) -> confirmClearHistory())
                .show();
    }

    private void confirmClearHistory() {
        new AlertDialog.Builder(requireContext()).setTitle("Alle Wischspuren löschen?")
                .setMessage("Entfernt die letzten 100 Protokolleinträge. Gelernte Wörter bleiben erhalten.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (dialog, which) -> SwipeT9History.clear(requireContext()))
                .show();
    }

    private static boolean containsWord(JSONObject entry, String target) {
        for (String key : new String[] {"decoderWords", "shown"}) {
            JSONArray words = entry.optJSONArray(key);
            if (words == null) continue;
            for (int i = 0; i < words.length(); i++) {
                if (target.equalsIgnoreCase(words.optString(i))) return true;
            }
        }
        return false;
    }

    private static String printList(JSONArray items, int max) {
        if (items == null || items.length() == 0) return "";
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < items.length() && i < max; i++) {
            if (i > 0) summary.append(", ");
            summary.append(items.optString(i));
        }
        return summary.toString();
    }

    private void askTarget(JSONObject entry) {
        Context context = requireContext();
        android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine(true);
        input.setHint("z. B. Liebe");
        input.setText(entry.optString("target", ""));
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        LinearLayout box = new LinearLayout(context);
        box.setPadding(pad, pad / 2, pad, 0);
        box.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(context).setTitle("Welches Wort war gemeint?")
                .setMessage("Wird bei diesem Eintrag gespeichert. Ist „Wischen lernen“ an, merkt sich die Tastatur diese Bewegung für das Wort.")
                .setView(box)
                .setNegativeButton("Abbrechen", (d, w) -> showHistoryItem(entry))
                .setPositiveButton("Speichern", (d, w) -> {
                    String word = input.getText().toString().trim();
                    long id = entry.optLong("id", -1);
                    SwipeT9History.setTarget(context, id, word);
                    learnTarget(context, entry, word);
                    JSONObject updated = SwipeT9History.find(context, id);
                    showHistoryItem(updated != null ? updated : entry);
                }).show();
    }

    private static void learnTarget(Context context, JSONObject entry, String word) {
        if (word.isEmpty()) return;
        JSONArray path = entry.optJSONArray("path");
        Language language = LanguageCollection.getLanguage(entry.optInt("language", -1));
        if (path == null || path.length() < 4 || language == null) return;
        float[] raw = new float[path.length()];
        for (int i = 0; i < raw.length; i++) raw[i] = (float) path.optDouble(i);
        SwipeT9Memory.remember(context, language.getLocale().getLanguage(),
                word.toLowerCase(language.getLocale()), raw);
    }

    private static String printWords(JSONArray words) {
        if (words == null || words.length() == 0) return "";
        StringBuilder summary = new StringBuilder();
        for (int i = 0; i < words.length() && i < 6; i++) {
            if (i > 0) summary.append(", ");
            summary.append(words.optString(i));
        }
        return summary.toString();
    }

    private void showHistoryItem(JSONObject entry) {
        Context context = requireContext();
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        String suggestions = printWords(entry.optJSONArray("shown"));
        String decoderWords = printList(entry.optJSONArray("decoderWords"), 30);
        String readings = printList(entry.optJSONArray("readings"), 12);
        String target = entry.optString("target", "");
        String chosenWord = entry.optString("chosen", "");
        String details = "Gewählt: " + (chosenWord.isEmpty() ? "– (nichts übernommen)" : chosenWord + (entry.optBoolean("learned") ? "  ✓ gelernt" : "")) +
                "\nGemeint: " + (target.isEmpty() ? "– (unten „Gemeint war…“ antippen)"
                        : target + (containsWord(entry, target) ? "  ✓ wurde angeboten" : "  ✗ nicht angeboten")) +
                "\n\nRohe Tastenfolge: " + entry.optString("digits") +
                "\nGewählte Lesart: " + (entry.optString("best").isEmpty() ? "–" : entry.optString("best")) +
                "\nLesarten mit Wörtern: " + (readings.isEmpty() ? "keine" : readings) +
                "\nBeginn: Taste " + entry.optInt("start", -1) +
                "  ·  Ende: Taste " + entry.optInt("end", -1) +
                "\nErkannt: " + (entry.optBoolean("recognized") ? "Ja" : "Nein (normaler Tastendruck)") +
                "\nKreise/Zickzack: " + (entry.optString("loops").isEmpty() ? "kein Wiederholungsmuster erkannt" : entry.optString("loops")) +
                "\nVorschlagsleiste: " + (suggestions.isEmpty() ? "nicht erfasst" : suggestions) +
                "\nAlle Wisch-Wörter: " + (decoderWords.isEmpty() ? "keine" : decoderWords) +
                "\n\nGrün = Anfang · Orange = Ende. Die Linie zeigt den aufgezeichneten Weg.";
        TextView text = new TextView(context);
        text.setText(details);
        text.setTextSize(14);
        layout.addView(text);
        View sketch = new SwipePreview(context, entry.optJSONArray("path"));
        layout.addView(sketch, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Math.round(210 * getResources().getDisplayMetrics().density)));
        new AlertDialog.Builder(context).setTitle("Wischspur untersuchen")
                .setView(layout)
                .setNegativeButton("Zurück zur Liste", (dialog, which) -> showHistory())
                .setPositiveButton("Gemeint war…", (dialog, which) -> askTarget(entry))
                .setNeutralButton("Details kopieren", (dialog, which) -> {
                    ClipboardManager manager = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (manager != null) manager.setPrimaryClip(ClipData.newPlainText("Swipe-T9 Diagnose", details));
                }).show();
    }

    /** Read-only miniature route drawing from normalized, locally stored coordinates. */
    private static final class SwipePreview extends View {
        private final JSONArray points;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        SwipePreview(Context context, JSONArray points) {
            super(context);
            this.points = points;
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(Color.rgb(28, 35, 43));
            if (points == null || points.length() < 4) return;
            float minX = 2f, minY = 2f, maxX = -1f, maxY = -1f;
            for (int i=0; i+1<points.length(); i+=2) {
                float x = (float) points.optDouble(i), y = (float) points.optDouble(i+1);
                minX = Math.min(minX, x); minY = Math.min(minY, y);
                maxX = Math.max(maxX, x); maxY = Math.max(maxY, y);
            }
            float margin = 26 * getResources().getDisplayMetrics().density;
            float scale = Math.min((getWidth()-2*margin)/Math.max(.05f, maxX-minX),
                    (getHeight()-2*margin)/Math.max(.05f, maxY-minY));
            if (scale <= 0 || Float.isNaN(scale)) return;
            float offsetX = (getWidth() - (maxX-minX)*scale) / 2;
            float offsetY = (getHeight() - (maxY-minY)*scale) / 2;
            Path line = new Path();
            for (int i=0; i+1<points.length(); i+=2) {
                float x = offsetX + ((float)points.optDouble(i)-minX)*scale;
                float y = offsetY + ((float)points.optDouble(i+1)-minY)*scale;
                if (i == 0) line.moveTo(x,y); else line.lineTo(x,y);
            }
            paint.setColor(Color.rgb(0,195,216)); paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(4*getResources().getDisplayMetrics().density);
            canvas.drawPath(line, paint); paint.setStyle(Paint.Style.FILL);
            float sx = offsetX + ((float)points.optDouble(0)-minX)*scale;
            float sy = offsetY + ((float)points.optDouble(1)-minY)*scale;
            int end = points.length()-2;
            float ex = offsetX + ((float)points.optDouble(end)-minX)*scale;
            float ey = offsetY + ((float)points.optDouble(end+1)-minY)*scale;
            paint.setColor(Color.GREEN); canvas.drawCircle(sx,sy,9,paint);
            paint.setColor(Color.rgb(255,160,50)); canvas.drawCircle(ex,ey,9,paint);
        }
    }

	private void createPhysicalKeysSection() {
		Preference debounceTime = findPreference(DropDownKeyPadDebounceTime.NAME);
		if (debounceTime instanceof DropDownKeyPadDebounceTime && activity != null) {
			((DropDownKeyPadDebounceTime) debounceTime).populate(activity.getSettings()).preview();
		}
	}

	protected void createVirtualKeysSection() {
		if (activity == null) {
			return;
		}

		// hide the entire category when the settings show no interest in it
		final boolean isVisible = activity.getSettings().isMainLayoutLarge() || activity.getSettings().isMainLayoutSmall();
		final PreferenceCategory category = findPreference("category_virtual_keys");
		if (category != null) {
			category.setVisible(isVisible);
		}
	}
}
