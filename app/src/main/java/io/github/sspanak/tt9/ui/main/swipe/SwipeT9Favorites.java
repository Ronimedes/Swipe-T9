package io.github.sspanak.tt9.ui.main.swipe;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Locale;

/** v0.2.16: words the user took after swiping. They move to the front of later swipe suggestions. */
public final class SwipeT9Favorites {
	private static final String PREFS = "swipe_t9_favorites";
	private static final int MAX_COUNT = 20;

	private SwipeT9Favorites() {}

	private static String key(String word) { return word.trim().toLowerCase(Locale.ROOT); }

	public static void add(@NonNull Context context, String word) {
		if (word == null || word.trim().length() < 2) return;
		SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
		String k = key(word);
		p.edit().putInt(k, Math.min(MAX_COUNT, p.getInt(k, 0) + 1)).apply();
	}

	public static int count(@NonNull Context context, String word) {
		if (word == null) return 0;
		return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(key(word), 0);
	}

	/** Already chosen words first (most chosen first), everything else keeps its order. */
	@NonNull
	public static ArrayList<String> sort(@NonNull Context context, @NonNull ArrayList<String> words) {
		ArrayList<String> chosen = new ArrayList<>();
		ArrayList<String> rest = new ArrayList<>();
		for (String w : words) (count(context, w) > 0 ? chosen : rest).add(w);
		chosen.sort((a, b) -> Integer.compare(count(context, b), count(context, a)));
		chosen.addAll(rest);
		return chosen;
	}

	public static void clear(@NonNull Context context) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
	}
}
