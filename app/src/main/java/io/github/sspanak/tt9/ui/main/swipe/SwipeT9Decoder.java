package io.github.sspanak.tt9.ui.main.swipe;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;

/**
 * Swipe-T9 v0.2.8: turns ONE finger trace into SEVERAL plausible digit sequences.
 *
 * A finger crossing the keypad has to pass over keys it does not mean. A key is
 * probably meant ("anchor") when the gesture starts or ends there, loops on it,
 * or clearly changes direction on it. A key that is crossed in a straight line,
 * especially only at its edge, is probably just in the way ("transit").
 *
 * The decoder does not decide alone. It produces a ranked list of sequences with
 * and without the doubtful keys; the dictionary then tells which of them are words.
 */
public final class SwipeT9Decoder {
	public static final int MAX_CANDIDATES = 100;
	private static final float DOUBLE_COST = 0.45f;
	private static final int MAX_OPTIONAL = 12;

	private SwipeT9Decoder() {}

	/** One key visit of the trace. */
	static final class Segment {
		int digit;
		boolean doubled;
		/** 0 = surely transit, 1 = surely meant. */
		float strength;
	}


	@NonNull
	public static ArrayList<String> decode(@NonNull SwipeT9Gesture g) {
		Segment[] segments = analyze(g);
		ArrayList<String> result = new ArrayList<>();
		if (segments.length == 0) {
			return result;
		}

		// Which segments may be dropped? The weakest ones, at most MAX_OPTIONAL.
		Integer[] order = new Integer[segments.length];
		for (int i = 0; i < order.length; i++) order[i] = i;
		Arrays.sort(order, Comparator.comparingDouble(i -> segments[i].strength));
		ArrayList<Integer> optional = new ArrayList<>();
		for (Integer i : order) {
			if (optional.size() >= MAX_OPTIONAL) break;
			if (segments[i].strength < 0.95f) optional.add(i);
		}

		// Enumerate every keep/drop choice of the optional segments and price it.
		final int combos = 1 << optional.size();
		ArrayList<Choice> choices = new ArrayList<>(combos);
		for (int mask = 0; mask < combos; mask++) {
			boolean[] dropped = new boolean[segments.length];
			float cost = 0;
			for (int b = 0; b < optional.size(); b++) {
				int idx = optional.get(b);
				if ((mask & (1 << b)) != 0) {
					dropped[idx] = true;
					cost += segments[idx].strength;             // dropping a meant key costs
				} else {
					cost += (1f - segments[idx].strength) * 0.6f; // keeping a transit key costs a bit
				}
			}
			// Dropping a transit key between two visits of the same key usually means the finger
			// only grazed past it: one letter. A real double letter is a loop or zigzag.
			String merged = build(segments, dropped, true);
			if (merged.length() >= 2) choices.add(new Choice(merged, cost));
			String repeated = build(segments, dropped, false);
			if (!repeated.equals(merged) && repeated.length() >= 2) choices.add(new Choice(repeated, cost + 0.35f));
			// v0.2.10: repeated letters without a loop. On T9 a run like "ede" (jedes) or "ffe"
			// (getroffen) is the same key 3 times. Try 1-3 extra presses on one meant key,
			// and one extra press on two different keys (Kaffee, Messer).
			ArrayList<Integer> meant = new ArrayList<>();
			for (int d = 0; d < segments.length; d++) {
				if (!dropped[d] && segments[d].strength >= 0.5f) meant.add(d);
			}
			int[] extra = new int[segments.length];
			for (int a = 0; a < meant.size(); a++) {
				for (int times = 1; times <= 3; times++) {
					extra[meant.get(a)] = times;
					addRepeated(choices, segments, dropped, extra, cost + DOUBLE_COST + 0.2f * (times - 1));
				}
				extra[meant.get(a)] = 0;
				for (int b = a + 1; b < meant.size(); b++) {
					extra[meant.get(a)] = 1;
					extra[meant.get(b)] = 1;
					addRepeated(choices, segments, dropped, extra, cost + DOUBLE_COST * 2.2f);
					extra[meant.get(b)] = 0;
				}
				extra[meant.get(a)] = 0;
			}
		}

		choices.sort(Comparator.comparingDouble(c -> c.cost));
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (Choice c : choices) {
			unique.add(c.digits);
			if (unique.size() >= MAX_CANDIDATES) break;
		}
		result.addAll(unique);
		return result;
	}


	private static final class Choice {
		final String digits;
		final float cost;
		Choice(String digits, float cost) { this.digits = digits; this.cost = cost; }
	}


	@NonNull
	private static String build(@NonNull Segment[] segments, @NonNull boolean[] dropped, boolean collapseRepeats) {
		StringBuilder sb = new StringBuilder();
		int last = -1;
		for (int i = 0; i < segments.length; i++) {
			if (dropped[i]) continue;
			Segment s = segments[i];
			if (collapseRepeats && s.digit == last && !s.doubled) continue;
			sb.append((char) ('0' + s.digit));
			if (s.doubled) sb.append((char) ('0' + s.digit));
			last = s.digit;
			if (sb.length() >= 32) break;
		}
		return sb.toString();
	}


	private static void addRepeated(@NonNull ArrayList<Choice> choices, @NonNull Segment[] segments, @NonNull boolean[] dropped, @NonNull int[] extra, float cost) {
		StringBuilder sb = new StringBuilder();
		int last = -1;
		for (int i = 0; i < segments.length; i++) {
			if (dropped[i]) continue;
			Segment s = segments[i];
			if (s.digit == last && !s.doubled && extra[i] == 0) continue;
			sb.append((char) ('0' + s.digit));
			if (s.doubled) sb.append((char) ('0' + s.digit));
			for (int k = 0; k < extra[i]; k++) sb.append((char) ('0' + s.digit));
			last = s.digit;
			if (sb.length() >= 32) break;
		}
		if (sb.length() >= 3 && sb.length() <= 32) choices.add(new Choice(sb.toString(), cost));
	}


	@NonNull
	static Segment[] analyze(@NonNull SwipeT9Gesture g) {
		final int n = g.segCount;
		Segment[] out = new Segment[n];
		final float key = Math.max(1f, g.lastKeySize);

		for (int i = 0; i < n; i++) {
			Segment s = new Segment();
			s.digit = g.segDigit[i];
			s.doubled = g.segDoubled[i];

			int from = g.segStart[i];
			int to = (i + 1 < n ? g.segStart[i + 1] : g.gCount) - 1; // inclusive
			to = Math.max(from, Math.min(to, g.gCount - 1));

			if (i == 0 || i == n - 1 || s.doubled) {
				s.strength = 1f;
				out[i] = s;
				continue;
			}

			// Path inside the key and how far the key was actually entered.
			float path = 0;
			for (int k = from + 1; k <= to; k++) {
				path += (float) Math.hypot(g.gX[k] - g.gX[k - 1], g.gY[k] - g.gY[k - 1]);
			}

			// Direction change: compare travel direction before and after this key.
			float reach = 0.6f * key;
			int before = walkBack(g, from, reach);
			int after = walkForward(g, to, reach);
			float inX = g.gX[from] - g.gX[before], inY = g.gY[from] - g.gY[before];
			float outX = g.gX[after] - g.gX[to], outY = g.gY[after] - g.gY[to];
			// Inside the key the finger may already turn; include the key path in both halves.
			int mid = (from + to) / 2;
			inX += g.gX[mid] - g.gX[from]; inY += g.gY[mid] - g.gY[from];
			outX += g.gX[to] - g.gX[mid]; outY += g.gY[to] - g.gY[mid];
			float angle = angleDeg(inX, inY, outX, outY);

			float turnScore = clamp((angle - 20f) / 50f);       // 20° -> 0, 70° -> 1
			float depthScore = clamp((path / key - 0.35f) / 0.75f); // edge clip -> 0, long stay -> 1
			s.strength = clamp(0.65f * turnScore + 0.35f * depthScore * (0.5f + 0.5f * turnScore) + 0.1f * depthScore);
			out[i] = s;
		}
		return out;
	}


	private static int walkBack(@NonNull SwipeT9Gesture g, int index, float distance) {
		float d = 0;
		int k = index;
		while (k > 0 && d < distance) {
			d += (float) Math.hypot(g.gX[k] - g.gX[k - 1], g.gY[k] - g.gY[k - 1]);
			k--;
		}
		return k;
	}


	private static int walkForward(@NonNull SwipeT9Gesture g, int index, float distance) {
		float d = 0;
		int k = index;
		while (k < g.gCount - 1 && d < distance) {
			d += (float) Math.hypot(g.gX[k + 1] - g.gX[k], g.gY[k + 1] - g.gY[k]);
			k++;
		}
		return k;
	}


	private static float angleDeg(float ax, float ay, float bx, float by) {
		double la = Math.hypot(ax, ay), lb = Math.hypot(bx, by);
		if (la < 1e-3 || lb < 1e-3) return 0f;
		double cos = (ax * bx + ay * by) / (la * lb);
		cos = Math.max(-1, Math.min(1, cos));
		return (float) Math.toDegrees(Math.acos(cos));
	}


	private static float clamp(float v) {
		return v < 0 ? 0 : (v > 1 ? 1 : v);
	}
}
