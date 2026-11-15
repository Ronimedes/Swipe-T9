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
		Preference clear = findPreference("swipe_t9_clear_learning");
		if (clear != null) clear.setOnPreferenceClickListener(p -> {
			SwipeT9Memory.clear(requireContext());
			return true;
		});
		EnhancedDropDownPreference dropdown = findPreference(DropDownOneKeyEmoji.NAME);
		if (dropdown != null && activity != null) dropdown.populate(activity.getSettings()).preview();

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
            String candidates = printWords(e.optJSONArray("dictionary"));
            names[i] = time + "  " + e.optString("digits", "?") +
                    (e.optBoolean("recognized") ? "" : " (nicht erkannt)") +
                    "  →  " + (candidates.isEmpty() ? "keine Wörter" : candidates);
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
        String dictionary = printWords(entry.optJSONArray("dictionary"));
        String details = "Tastenfolge: " + entry.optString("digits") +
                "\nBeginn: Taste " + entry.optInt("start", -1) +
                "  ·  Ende: Taste " + entry.optInt("end", -1) +
                "\nErkannt: " + (entry.optBoolean("recognized") ? "Ja" : "Nein (normaler Tastendruck)") +
                "\nKreise/Zickzack: " + (entry.optString("loops").isEmpty() ? "kein Wiederholungsmuster erkannt" : entry.optString("loops")) +
                "\nSichtbare Vorschläge: " + (suggestions.isEmpty() ? "keine / nicht erfasst" : suggestions) +
                "\nKandidaten aus der Vorschlagssuche: " + (dictionary.isEmpty() ? "keine Treffer / nicht erfasst" : dictionary) +
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
                .setPositiveButton("Schließen", null)
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
