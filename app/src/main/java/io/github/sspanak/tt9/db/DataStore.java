package io.github.sspanak.tt9.db;

import android.content.Context;
import android.os.CancellationSignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import io.github.sspanak.tt9.db.entities.AddWordResult;
import io.github.sspanak.tt9.db.entities.CustomWord;
import io.github.sspanak.tt9.db.wordPairs.WordPairStore;
import io.github.sspanak.tt9.db.words.WordStore;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.SupremeExecutor;

public class DataStore {
	private final static String LOG_TAG = DataStore.class.getSimpleName();

	private static ExecutorService executor;

	@Nullable private static Future<?> getWordsTask;
	@NonNull private static CancellationSignal getWordsCancellationSignal = new CancellationSignal();

	private static WordPairStore pairs;
	private static WordStore words;


	public static void init(@NonNull Context context) {
		executor = executor == null ? SupremeExecutor.get() : executor;
		pairs = pairs == null ? new WordPairStore(context.getApplicationContext()) : pairs;
		words = words == null ? new WordStore(context.getApplicationContext()) : words;
	}


	@Nullable
	private static Future<?> runInThread(@NonNull Runnable action) {
		if (executor == null || executor.isShutdown() || executor.isTerminated()) {
			Logger.e(LOG_TAG, "Cannot run a DataStore task when the executor is shutdown or NULL.");
			return null;
		}

		try {
			return executor.submit(() -> {
				try {
					action.run();
				} catch (Exception e) {
					Logger.ex(LOG_TAG, "Error in DataStore task.", e);
				}
			});
		} catch (RejectedExecutionException e) {
			Logger.e(LOG_TAG, "Failed running async DataStore task. " + e);
			return null;
		}
	}


	private static void runInTransaction(@NonNull Runnable action, @NonNull Runnable onFinish, @NonNull String errorMessagePrefix) {
		runInThread(() -> {
			try {
				words.startTransaction();
				action.run();
				words.finishTransaction();
			} catch (Exception e) {
				words.failTransaction();
				Logger.e(LOG_TAG, errorMessagePrefix + " " + e.getMessage());
			}
			onFinish.run();
		});
	}


	public static void normalizeNext() {
		words.normalizeNext();
	}


	public static void getLastLanguageUpdateTime(Consumer<String> notification, Language language) {
		runInThread(() -> notification.accept(words.getLanguageFileHash(language)));
	}


	public static void deleteCustomWord(Runnable notification, Language language, String word) {
		runInThread(() -> {
			words.removeCustomWord(language, word);
			notification.run();
		});
	}


	public static void put(Consumer<AddWordResult> statusHandler, Language language, String word, boolean makeTopWord) {
		runInThread(() -> statusHandler.accept(words.put(language, word, makeTopWord)));
	}


	public static void makeTopWord(@NonNull Language language, @NonNull String word, @NonNull String sequence) {
		runInThread(() -> words.makeTopWord(language, word, sequence));
	}


	public static void getWords(Consumer<ArrayList<String>> dataHandler, Language language, String sequence, boolean onlyExactSequence, String filter, boolean orderByLength, int minWords, int maxWords) {
		if (getWordsTask != null && !getWordsTask.isDone()) {
			getWordsCancellationSignal.cancel();
		}

		getWordsCancellationSignal = new CancellationSignal();
		getWordsTask = runInThread(() -> getWordsSync(dataHandler, language, sequence, onlyExactSequence, filter, orderByLength, minWords, maxWords));
		runInThread(DataStore::setGetWordsTimeout);
	}


	/**
	 * Swipe-T9 v0.2.11: looks up all readings of one swipe and ranks every found word by
	 * how likely the reading is (decoder cost) AND how common the word is (frequency).
	 * If no reading is a whole word, the best readings are tried as word beginnings.
	 * Runs in its own task with its own CancellationSignal, so live suggestions are not disturbed.
	 * The handler receives the words, best first; empty when nothing fits at all.
	 */
	public static void getRankedWordsForSequences(Consumer<ArrayList<String>> dataHandler, Language language, java.util.List<String> sequences, java.util.List<Float> costs, int maxWordsPerSequence, int maxWordsTotal) {
		final ArrayList<String> seqs = new ArrayList<>(sequences);
		final ArrayList<Float> cost = new ArrayList<>(costs);
		runInThread(() -> {
			java.util.HashMap<String, Double> scores = new java.util.HashMap<>();
			try {
				CancellationSignal own = new CancellationSignal();
				for (int i = 0; i < seqs.size(); i++) {
					float c = i < cost.size() ? cost.get(i) : 3f;
					for (io.github.sspanak.tt9.db.entities.Word w : words.getManyWithFrequency(own, language, seqs.get(i), true, maxWordsPerSequence)) {
						score(scores, w, c);
					}
				}
				if (scores.isEmpty()) {
					// Nothing complete: offer the most likely words that START like the best readings.
					for (int i = 0; i < seqs.size() && i < 8; i++) {
						float c = (i < cost.size() ? cost.get(i) : 3f) + 0.8f;
						String seq = seqs.get(i);
						for (io.github.sspanak.tt9.db.entities.Word w : words.getManyWithFrequency(own, language, seq, false, maxWordsPerSequence)) {
							if (w.word == null) continue;
							score(scores, w, c + 0.3f * Math.max(0, w.word.length() - seq.length()));
						}
					}
				}
			} catch (Exception e) {
				Logger.e(LOG_TAG, "Swipe-T9 lookup failed: " + e.getMessage());
			}
			ArrayList<String> ranked = new ArrayList<>(scores.keySet());
			ranked.sort((a, b) -> Double.compare(scores.get(b), scores.get(a)));
			while (ranked.size() > maxWordsTotal) ranked.remove(ranked.size() - 1);
			dataHandler.accept(ranked);
		});
	}


	private static void score(java.util.HashMap<String, Double> scores, io.github.sspanak.tt9.db.entities.Word w, float cost) {
		if (w == null || w.word == null || w.word.isEmpty()) return;
		double s = Math.log1p(Math.max(0, w.frequency)) - 2.0 * cost;
		Double old = scores.get(w.word);
		if (old == null || s > old) scores.put(w.word, s);
	}


	private static void getWordsSync(Consumer<ArrayList<String>> dataHandler, Language language, String sequence, boolean onlyExactSequence, String filter, boolean orderByLength, int minWords, int maxWords) {
		try {
			ArrayList<String> data = words.getMany(getWordsCancellationSignal, language, sequence, onlyExactSequence, filter, orderByLength, minWords, maxWords);
			dataHandler.accept(data);
		} catch (Exception e) {
			Logger.e(LOG_TAG, "Error fetching words: " + e.getMessage());
		}
	}


	private static void setGetWordsTimeout() {
		if (getWordsTask == null) {
			return;
		}

		try {
			getWordsTask.get(SettingsStore.SLOW_QUERY_TIMEOUT, TimeUnit.MILLISECONDS);
		} catch (Exception e) {
			getWordsCancellationSignal.cancel();
			Logger.e(LOG_TAG, "Word loading timed out after " + SettingsStore.SLOW_QUERY_TIMEOUT + " ms.");
		}
	}


	public static void getCustomWords(Consumer<ArrayList<CustomWord>> dataHandler, String wordFilter, int maxWords) {
		runInThread(() -> dataHandler.accept(words.getSimilarCustom(wordFilter, maxWords)));
	}


	public static void countCustomWords(Consumer<Long> dataHandler) {
		runInThread(() -> dataHandler.accept(words.countCustom()));
	}


	public static void exists(Consumer<ArrayList<Integer>> dataHandler, ArrayList<Language> languages) {
		runInThread(() -> dataHandler.accept(words.exists(languages)));
	}


	@Nullable
	public static String getWord(@NonNull Language language, @NonNull String word, @NonNull String sequence) {
		return words.getWord(language, word, sequence);
	}


	public static void addWordPair(Language language, String word1, String word2, String sequence2) {
		pairs.add(language, word1, word2, sequence2);
	}


	public static String getWord2(Language language, String word1, String sequence2) {
		return pairs.getWord2(language, word1, sequence2);
	}


	public static void saveWordPairs() {
		pairs.save();
	}


	public static void loadWordPairs(ArrayList<Language> languages) {
		runInThread(() -> pairs.load(languages));
	}


	public static void clearWordPairCache() {
		pairs.clearCache();
	}


	public static void deleteWordPairs(@NonNull ArrayList<Language> languages, @NonNull Runnable onDeleted) {
		runInTransaction(() -> pairs.remove(languages), onDeleted, "Failed deleting word pairs.");
	}


	public static String getWordPairStats() {
		return pairs.toString();
	}
}
