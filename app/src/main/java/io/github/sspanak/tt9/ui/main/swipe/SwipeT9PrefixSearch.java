package io.github.sspanak.tt9.ui.main.swipe;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Swipe-T9 v0.2.15: dictionary-guided search ("Wortanfangs-Suche").
 *
 * Walks through the key visits of one trace. At every key it may drop the key (it was only in
 * the way), take it once, or take it several times (T9 puts up to 4 letters on one key, so
 * "interpretieren" needs the 7 three times in a row). Every partial result is checked against
 * the dictionary: only digit prefixes that start at least one real word survive. This finds
 * long words with several repeated keys that a fixed list of readings can never cover.
 */
public final class SwipeT9PrefixSearch {
	public interface Dictionary {
		boolean hasPrefix(@NonNull String digits);
		boolean isWord(@NonNull String digits);
	}

	public static final class Result {
		public final String digits;
		public final float cost;
		Result(String digits, float cost) { this.digits = digits; this.cost = cost; }
	}

	private static final float KEEP_TRANSIT_COST = 0.6f;
	private static final float FIRST_REPEAT_COST = 0.45f;
	private static final float NEXT_REPEAT_COST = 0.2f;
	private static final int MAX_REPEATS = 3;
	private static final int MAX_LENGTH = 32;

	private SwipeT9PrefixSearch() {}


	private static final class State {
		final String seq;
		final float cost;
		State(String seq, float cost) { this.seq = seq; this.cost = cost; }
	}


	@NonNull
	public static ArrayList<Result> search(@NonNull int[] digits, @NonNull float[] strength, @NonNull boolean[] doubled,
	                                       @NonNull Dictionary dictionary, int beam, int maxResults) {
		final HashMap<String, Boolean> prefixCache = new HashMap<>();
		ArrayList<State> states = new ArrayList<>();
		states.add(new State("", 0f));

		for (int i = 0; i < digits.length; i++) {
			final int d = digits[i];
			final float w = strength[i];
			final char c = (char) ('0' + d);
			HashMap<String, Float> next = new HashMap<>();

			for (State s : states) {
				// 1. drop this key: it was only crossed on the way
				put(next, s.seq, s.cost + w);

				// 2. take it (once, or twice for a loop/zigzag)
				final boolean sameAsLast = !s.seq.isEmpty() && s.seq.charAt(s.seq.length() - 1) == c;
				String taken;
				float takenCost = s.cost + (1f - w) * KEEP_TRANSIT_COST;
				if (sameAsLast && !doubled[i]) {
					// the finger came back to the same key: by default the same letter (no growth)
					taken = s.seq;
				} else {
					taken = s.seq + c + (doubled[i] ? String.valueOf(c) : "");
					if (taken.length() > MAX_LENGTH || !hasPrefix(dictionary, prefixCache, taken)) {
						continue;
					}
				}
				put(next, taken, takenCost);

				// 3. more letters from the same key (rr, ss, "rpr" in interpretieren, ...)
				String repeated = taken;
				float repeatCost = takenCost;
				for (int k = 1; k <= MAX_REPEATS; k++) {
					repeated = repeated + c;
					repeatCost += k == 1 ? FIRST_REPEAT_COST : NEXT_REPEAT_COST;
					if (repeated.length() > MAX_LENGTH || !hasPrefix(dictionary, prefixCache, repeated)) break;
					put(next, repeated, repeatCost);
				}
			}

			states = prune(next, beam);
			if (states.isEmpty()) break;
		}

		ArrayList<Result> results = new ArrayList<>();
		for (State s : states) {
			if (!s.seq.isEmpty() && dictionary.isWord(s.seq)) {
				results.add(new Result(s.seq, s.cost));
				if (results.size() >= maxResults) break;
			}
		}
		return results;
	}


	private static boolean hasPrefix(Dictionary dictionary, HashMap<String, Boolean> cache, String prefix) {
		Boolean known = cache.get(prefix);
		if (known == null) {
			known = dictionary.hasPrefix(prefix);
			cache.put(prefix, known);
		}
		return known;
	}


	private static void put(HashMap<String, Float> states, String seq, float cost) {
		Float old = states.get(seq);
		if (old == null || cost < old) states.put(seq, cost);
	}


	@NonNull
	private static ArrayList<State> prune(@NonNull HashMap<String, Float> next, int beam) {
		ArrayList<State> list = new ArrayList<>(next.size());
		for (Map.Entry<String, Float> e : next.entrySet()) list.add(new State(e.getKey(), e.getValue()));
		list.sort((a, b) -> Float.compare(a.cost, b.cost));
		while (list.size() > beam) list.remove(list.size() - 1);
		return list;
	}
}
