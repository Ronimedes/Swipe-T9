package io.github.sspanak.tt9.ui.main.swipe;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Swipe-T9 v0.2.9: words the user never wants to see as suggestions.
 * The dictionary stays untouched (a reload would bring deleted words back);
 * this list filters them out everywhere, for typing and for swiping.
 */
public final class SwipeT9Blocklist {
	private static final String FILE = "swipe_t9_blocklist";
	private static final String KEY = "words";

	@Nullable private static Set<String> cache = null;

	private SwipeT9Blocklist() {}


	@NonNull
	private static String normalize(@NonNull String word) {
		return word.trim().toLowerCase(Locale.ROOT);
	}


	@NonNull
	private static synchronized Set<String> load(@NonNull Context context) {
		if (cache == null) {
			SharedPreferences prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
			cache = new HashSet<>(prefs.getStringSet(KEY, new HashSet<>()));
		}
		return cache;
	}


	private static synchronized void save(@NonNull Context context) {
		if (cache == null) return;
		context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
			.putStringSet(KEY, new HashSet<>(cache)).apply();
	}


	public static synchronized boolean isBlocked(@NonNull Context context, @Nullable String word) {
		if (word == null || word.trim().isEmpty()) return false;
		Set<String> words = load(context);
		return !words.isEmpty() && words.contains(normalize(word));
	}


	public static synchronized void block(@NonNull Context context, @Nullable String word) {
		if (word == null || word.trim().isEmpty()) return;
		load(context).add(normalize(word));
		save(context);
	}


	public static synchronized void unblock(@NonNull Context context, @Nullable String word) {
		if (word == null) return;
		load(context).remove(normalize(word));
		save(context);
	}


	@NonNull
	public static synchronized List<String> getAll(@NonNull Context context) {
		ArrayList<String> words = new ArrayList<>(load(context));
		Collections.sort(words);
		return words;
	}


	/** Returns a copy without blocked words, or the same list when nothing is blocked. */
	@NonNull
	public static synchronized ArrayList<String> filter(@NonNull Context context, @NonNull ArrayList<String> words) {
		Set<String> blocked = load(context);
		if (blocked.isEmpty()) return words;
		ArrayList<String> out = new ArrayList<>(words.size());
		for (String w : words) {
			if (w == null || !blocked.contains(normalize(w))) out.add(w);
		}
		return out;
	}
}
