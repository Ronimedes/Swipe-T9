package io.github.sspanak.tt9.ime;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import io.github.sspanak.tt9.db.DataStore;
import io.github.sspanak.tt9.db.words.DictionaryLoader;
import io.github.sspanak.tt9.hacks.InputType;
import io.github.sspanak.tt9.ime.modes.InputModeKind;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.UI;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9Memory;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9History;
import java.util.ArrayList;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.util.Logger;
import io.github.sspanak.tt9.util.SupremeExecutor;
import io.github.sspanak.tt9.util.sys.DeviceInfo;
import io.github.sspanak.tt9.util.sys.SystemSettings;

public class TraditionalT9 extends PremiumHandler {
	private static final String LOG_TAG = "MAIN";
    private long pendingSwipeLogId = -1;
    private int pendingSwipeSequenceLength = -1;
    // v0.2.9: which history entry waits for the word the user finally takes.
    private long chosenSwipeLogId = -1;
    // Swipe-T9 v0.2.8: more words from alternative readings of the same trace.
    @NonNull private final Handler swipeLookupHandler = new Handler(Looper.getMainLooper());
    private int swipeLookupToken = 0;
    private ArrayList<String> swipeExtraWords = null;
    private int swipeExtraSequenceLength = -1;

	private Future<?> asyncInitThread;

	@NonNull private final Handler backgroundTasks = new Handler(Looper.getMainLooper());
	@NonNull private final Handler zombieDetector = new Handler(Looper.getMainLooper());
	@NonNull private final Handler heartbeatDetector = new Handler(Looper.getMainLooper());
	private boolean isDead = false;
	private int zombieChecks = 0;

	// A String to be committed after successfully starting in an input field.
	@NonNull private final StringBuffer onAfterStartText = new StringBuffer();


	@Override
	public View onCreateInputView() {
		// This may get called even when not switching IMEs, but we can't reuse the previous view
		// because it will cause: "IllegalStateException: The specified child already has a parent"
		mainView.forceCreate();
		initTray();
		statusBar.setText(mInputMode);
		suggestionOps.set(mInputMode.getSuggestions(), mInputMode.containsGeneratedSuggestions());
		mainView.render();
		refreshSwipeT9AbcWord();

		return mainView.getView();
	}


	@Override
	public void onComputeInsets(Insets outInsets) {
		super.onComputeInsets(outInsets);
		if (settings.clearInsets() && shouldBeVisible()) {
			// otherwise the MainView wouldn't show up on Sonim XP3900,
			// or it expands the application window past the edge of the screen
			outInsets.contentTopInsets = 0;
		}
	}


	@Override
	public void onStartInput(EditorInfo inputField, boolean restarting) {
		Logger.i(
			LOG_TAG,
			"===> Start Up; packageName: " + inputField.packageName + " inputType: " + inputField.inputType + " actionId: " + inputField.actionId + " imeOptions: " + inputField.imeOptions + " privateImeOptions: " + inputField.privateImeOptions + " extras: " + inputField.extras
		);
		onStart(inputField, restarting);
	}


	@Override
	public void onStartInputView(EditorInfo inputField, boolean restarting) {
		onStart(inputField, restarting);
	}


	@Override
	public void onFinishInputView(boolean finishingInput) {
		super.onFinishInputView(finishingInput);
		io.github.sspanak.tt9.ui.main.keys.SwipeT9SymbolPopup.dismiss();
		io.github.sspanak.tt9.ui.main.keys.SwipeT9WordMenu.dismiss();
		onFinishTyping(finishingInput);
	}


	@Override
	public void onFinishInput() {
		super.onFinishInput();
		onStop();
	}


	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		int result = super.onStartCommand(intent, flags, startId);

		final String command = intent != null ? intent.getStringExtra(UI.COMMAND) : null;

		switch (command == null ? "" : command) {
			case UI.COMMAND_WAKEUP_MAIN -> forceShowWindow();
			case UI.COMMAND_PRINT_VOICE_INPUT -> {
				final String text = intent.getStringExtra(UI.COMMAND_PRINT_VOICE_INPUT_TEXT);
				if (text != null) {
					onAfterStartText.append(text);
				}
			}
		}

		return result;
	}


	@Override
	protected void onInit() {
		isDead = false;
		zombieChecks = 0;
		settings.setDemoMode(false);
		Logger.setLevel(settings.getLogLevel());

		asyncInitThread = asyncInitThread == null ? SupremeExecutor.submit(this::runHeavyInitTasks) : asyncInitThread;

		super.onInit();
	}


	@Override
	protected boolean onStart(EditorInfo field, boolean restarting) {
		Logger.setLevel(settings.getLogLevel());

		if (SystemSettings.isTT9Selected(this)) {
			startHeartbeatCheck();
		} else {
			if (zombieChecks == 0) {
				startZombieCheck();
			}
			return false;
		}

		try {
			if (asyncInitThread != null && !asyncInitThread.isCancelled() && !asyncInitThread.isDone()) {
				asyncInitThread.get();
			}
		} catch (InterruptedException | ExecutionException e) {
			Logger.w(LOG_TAG, "Async initialization failed. " + e.getMessage() + ". Retrying on main thread.");
			runHeavyInitTasks();
		} finally {
			asyncInitThread = null;
		}

		appHacks.onBeforeStart(this, settings, mLanguage, field, mInputMode, suggestionOps, restarting);

		if (isDead || !super.onStart(field, restarting)) {
			getDisplayTextCase();
			setStatusIcon(mInputMode, mLanguage);
			return false;
		}

		if (InputModeKind.isPassthrough(mInputMode)) {
			onStop();
		}	else {
			backgroundTasks.removeCallbacksAndMessages(null);
			initUi(mInputMode);
		}

		onAfterStart(field);

		return true;
	}


	private void onAfterStart(EditorInfo field) {
		final InputType newInputType = new InputType(this, field);

		if (newInputType.isText()) {
			DataStore.loadWordPairs(LanguageCollection.getAll(settings.getEnabledLanguageIds()));
		}

		if (!newInputType.isUs()) {
			DictionaryLoader.autoLoad(this, settings, mLanguage);
		}

		if (onAfterStartText.length() > 0) {
			onText(onAfterStartText.toString(), false);
			onAfterStartText.setLength(0);
		}
	}


	@Override
	protected void onStop() {
		stopVoiceInput();
		onFinishTyping(true);
		statusBar.setText(mInputMode);

		if (isInputViewShown()) {
			updateInputViewShown();
		}

		if (SystemSettings.isTT9Selected(this)) {
			backgroundTasks.removeCallbacksAndMessages(null);
			backgroundTasks.postDelayed(this::runBackgroundTasks, SettingsStore.WORD_BACKGROUND_TASKS_DELAY);
		}

		if (zombieChecks == 0) {
			startZombieCheck();
		}

		stopHeartbeatCheck();
	}


	@Override
	protected void onFinishTyping(boolean willExitInput) {
		super.onFinishTyping(willExitInput);
		getDisplayTextCase();
		setStatusIcon(mInputMode, mLanguage);
	}


	/**
	 * On Android 11+ onStop() and onDestroy() are sometimes not called when the user switches to a
	 * different IME. Here we attempt to detect if we are disabled, then hide and kill ourselves.
	 */
	private void startHeartbeatCheck() {
		if (!SystemSettings.isTT9Selected(this)) {
			onZombie();
		} else if (!isDead && !InputModeKind.isPassthrough(mInputMode)) {
			heartbeatDetector.postDelayed(this::startHeartbeatCheck, SettingsStore.ZOMBIE_HEARTBEAT_INTERVAL);
			Logger.v(LOG_TAG, "===> Heart is beating");
		}
	}


	private void stopHeartbeatCheck() {
		if (!DeviceInfo.AT_LEAST_ANDROID_10 || heartbeatDetector.hasCallbacks(this::startHeartbeatCheck)) {
			heartbeatDetector.removeCallbacksAndMessages(null);
			Logger.d(LOG_TAG, "===> Heartbeat check stopped");
		}
	}


	/**
	 * Similar to the heartbeat check, but detects if we are on when invisible or after re-init.
	 */
	private void startZombieCheck() {
		if (zombieChecks > 0 && !SystemSettings.isTT9Selected(this)) {
			zombieChecks = 0;
			onZombie();
			return;
		}

		if (!isDead && ++zombieChecks < SettingsStore.ZOMBIE_CHECK_MAX) {
			zombieDetector.postDelayed(this::startZombieCheck, SettingsStore.ZOMBIE_CHECK_INTERVAL);
		} else {
			Logger.d(LOG_TAG, "Not a zombie after " + zombieChecks + " checks");
			zombieChecks = 0;
		}
	}


	private void onZombie() {
		if (isDead) {
			Logger.w(LOG_TAG, "===> Already dead. Cannot kill self.");
			return;
		}

		Logger.w(LOG_TAG, "===> Killing self");
		requestHideSelf(0);
		cleanUp();
		stopSelf();
		isDead = true;
	}


	@Override
	protected void cleanUp() {
		stopHeartbeatCheck();
		zombieDetector.removeCallbacksAndMessages(null);
		zombieChecks = SettingsStore.ZOMBIE_CHECK_MAX;
		backgroundTasks.removeCallbacksAndMessages(null);
		super.cleanUp();
		setInputField(null);
		Logger.d(LOG_TAG, "===> Final cleanup completed");
	}


	@Override
	public void onDestroy() {
		if (isDead) {
			Logger.w(LOG_TAG, "===> Already dead. Not destroying self.");
			return;
		}

		cleanUp();
		isDead = true;

		try {
			super.onDestroy();
		} catch (Exception e) {
			if (mainView != null && mainView.getView() != null) {
				Logger.e(LOG_TAG, "===> MainView destroy failed: " + e.getMessage() + ". Destroying manually.");
				mainView.destroy();
			}
		}

		Logger.d(LOG_TAG, "===> Shutdown completed");
	}


	@Override
	public void onTimeout(int startId) {
		onZombie();
		super.onTimeout(startId);
	}


	/** Swipe-T9 first test: apply a detected digit sequence via the existing T9 word engine. */
	public boolean onSwipeT9Sequence(String digits) {
		return onSwipeT9Sequence(digits, null);
	}

    public boolean onSwipeT9Sequence(String digits, float[] drawing) {
        return onSwipeT9Sequence(digits, drawing, -1);
    }

    public boolean isSwipeT9DiagnosticAllowed() {
        EditorInfo info = getCurrentInputEditorInfo();
        return isSwipeT9Allowed() && (inputType == null || !inputType.isPassword())
                && (info == null || (info.imeOptions & EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) == 0);
    }

    /** v0.2.9: remembers in the swipe log which word the user accepted after a swipe. */
    public void recordSwipeT9Chosen(String word) {
        if (chosenSwipeLogId < 0) return;
        long id = chosenSwipeLogId;
        chosenSwipeLogId = -1;
        if (word == null || word.trim().isEmpty()) return;
        SwipeT9History.setChosen(this, id, word.trim(), false);
        lastChosenSwipeLogId = id;
        lastChosenSwipeTime = System.currentTimeMillis();
    }

    private long lastChosenSwipeLogId = -1;
    private long lastChosenSwipeTime = 0;

    @Override
    protected void onSwipeT9WordAccepted(String word) {
        recordSwipeT9Chosen(word);
    }

    @Override
    protected void onSwipeT9Selected(String word, boolean learned) {
        boolean fresh = System.currentTimeMillis() - lastChosenSwipeTime < 2000;
        long id = lastChosenSwipeLogId >= 0 && fresh ? lastChosenSwipeLogId : chosenSwipeLogId;
        lastChosenSwipeLogId = -1;
        chosenSwipeLogId = -1;
        if (id < 0 || word == null || word.trim().isEmpty()) return;
        SwipeT9History.setChosen(this, id, word.trim(), learned);
    }

    public void recordSwipeT9ShownSuggestions(ArrayList<String> suggestions) {
        if (pendingSwipeLogId < 0 || mInputMode == null) return;
        if (mInputMode.getSequenceLength() < pendingSwipeSequenceLength) return;
        SwipeT9History.updateCandidates(this, pendingSwipeLogId, "shown", suggestions);
        pendingSwipeLogId = -1;
    }

    /**
     * Swipe-T9 v0.2.8: the decoder gives several readings of one trace. All of them are looked
     * up in the dictionary (in the background, without disturbing live suggestions). The best
     * reading that has words is typed as usual; words from the other readings are appended to
     * the suggestion bar, so the user can pick and the swipe memory can learn from that choice.
     */
    public void onSwipeT9Candidates(String raw, ArrayList<String> candidates, ArrayList<Float> costs, float[] drawing, long logId) {
        if (raw == null || raw.isEmpty() || !isSwipeT9Allowed() || mLanguage == null) return;
        String language = mLanguage.getLocale().getLanguage();
        if (candidates == null || candidates.isEmpty()
                || (drawing != null && SwipeT9Memory.match(this, language, drawing) != null)) {
            onSwipeT9Sequence(raw, drawing, logId);
            return;
        }
        final ArrayList<String> sequences = new ArrayList<>(candidates);
        final ArrayList<Float> sequenceCosts = costs == null ? new ArrayList<>() : new ArrayList<>(costs);
        if (!sequences.contains(raw)) { sequences.add(raw); sequenceCosts.add(4f); }
        final int token = ++swipeLookupToken;
        final io.github.sspanak.tt9.languages.Language lang = mLanguage;
        DataStore.getRankedWordsForSequences(
            ranked -> swipeLookupHandler.post(() -> applySwipeT9Lookup(token, ranked, drawing, logId)),
            lang, sequences, sequenceCosts, 6, 40
        );
    }

    /**
     * v0.2.11: the words come ranked by reading likelihood AND frequency. The best word's own
     * digit sequence is typed, so it is first in the bar; the other ranked words follow.
     * If nothing fits, nothing is typed (no more "w, x, y, z").
     */
    private void applySwipeT9Lookup(int token, ArrayList<String> ranked, float[] drawing, long logId) {
        if (token != swipeLookupToken || !isSwipeT9Allowed() || mInputMode == null || mLanguage == null) return;
        ArrayList<String> words = new ArrayList<>();
        for (String w : ranked) {
            if (w != null && !io.github.sspanak.tt9.ui.main.swipe.SwipeT9Blocklist.isBlocked(this, w)) words.add(w);
        }
        String best = null;
        for (String w : words) {
            try {
                best = mLanguage.getDigitSequenceForWord(w);
                if (best != null && !best.isEmpty() && best.matches("[2-9]+")) break;
                best = null;
            } catch (Exception ignored) {
                best = null;
            }
        }
        if (logId != -1 && isSwipeT9DiagnosticAllowed()) {
            SwipeT9History.updateDecoder(this, logId, best == null ? "" : best, new ArrayList<>(), words);
        }
        if (best == null) {
            swipeExtraWords = null;
            return;
        }
        prepareSwipeT9WordStart();
        swipeExtraWords = words;
        swipeExtraSequenceLength = mInputMode.getSequenceLength() + best.length();
        onSwipeT9Sequence(best, drawing, logId);
    }

    /** Puts the ranked swipe words first, then whatever T9 offers for the typed sequence. */
    public ArrayList<String> mergeSwipeT9Extras(ArrayList<String> suggestions) {
        if (swipeExtraWords == null || mInputMode == null) return suggestions;
        int length = mInputMode.getSequenceLength();
        if (length == 0 || length > swipeExtraSequenceLength) {
            swipeExtraWords = null;
            return suggestions;
        }
        if (length != swipeExtraSequenceLength) return suggestions;
        ArrayList<String> merged = new ArrayList<>();
        int textCase = mInputMode.getTextCaseRaw();
        for (String word : swipeExtraWords) {
            String shown = textCase == InputMode.CASE_CAPITALIZE || textCase == InputMode.CASE_UPPER
                ? new Text(mLanguage, word).toTextCase(textCase) : word;
            if (!containsIgnoreCase(merged, shown)) merged.add(shown);
        }
        for (String s : suggestions) {
            if (s != null && !containsIgnoreCase(merged, s)) merged.add(s);
        }
        return merged;
    }

    private static boolean containsIgnoreCase(ArrayList<String> list, String word) {
        for (String s : list) {
            if (s != null && s.equalsIgnoreCase(word)) return true;
        }
        return false;
    }

    public boolean onSwipeT9Sequence(String digits, float[] drawing, long logId) {
		if (digits == null || digits.isEmpty() || digits.length() > 32 || shouldBeOff()
			|| isFnPanelVisible() || !InputModeKind.isPredictive(mInputMode)) return false;
        prepareSwipeT9WordStart();
        pendingSwipeLogId = -1;
        chosenSwipeLogId = -1;
        if (logId != -1 && isSwipeT9DiagnosticAllowed()) {
            pendingSwipeLogId = logId;
            chosenSwipeLogId = logId;
            pendingSwipeSequenceLength = mInputMode.getSequenceLength() + digits.length();
            // Do not launch a second getWords() request here. DataStore cancels
            // concurrent dictionary searches, which would break live suggestions.
            // The live suggestions are saved by recordSwipeT9ShownSuggestions().
        }
		// Use confirmed, local prototypes only when at least three selections agree.
		// If there is any doubt, preserve the original T9 dictionary behavior.
		String language = mLanguage == null ? null : mLanguage.getLocale().getLanguage();
		String learned = drawing == null ? null : SwipeT9Memory.match(this, language, drawing);
		if (learned != null) {
			int textCase = mInputMode.getTextCaseRaw();
			String chosen = textCase == InputMode.CASE_CAPITALIZE || textCase == InputMode.CASE_UPPER
				? new Text(mLanguage, learned).toTextCase(textCase) : learned;
			if (onText(chosen, false)) {
                if (logId != -1) {
                    ArrayList<String> learnedChoices = new ArrayList<>();
                    learnedChoices.add(chosen);
                    SwipeT9History.updateCandidates(this, logId, "shown", learnedChoices);
                    SwipeT9History.setChosen(this, logId, chosen, true);
                    pendingSwipeLogId = -1;
                    chosenSwipeLogId = -1;
                }
				markSwipeT9Entered();
				return true;
			}
		}
		boolean result = false;
		for (int i = 0; i < digits.length(); i++) {
			int number = digits.charAt(i) - '0';
			if (number < 2 || number > 9) return false;
			result = onNumber(number, false, 0) || result;
		}
		if (result) {
			markSwipeT9Entered();
			if (drawing != null) rememberPendingSwipe(drawing);
		}
		return result;
	}

	/** Swipe is only active in normal predictive text fields, never in number/password modes. */
	public boolean isSwipeT9Allowed() {
		return !shouldBeOff() && !isFnPanelVisible() && (inputType == null || !inputType.isPassword()) && InputModeKind.isPredictive(mInputMode)
			&& settings.isMainLayoutNumpad() && !isTouchExplorationEnabled();
	}

	@Override
	protected boolean onNumber(int key, boolean hold, int repeat) {
		if (InputModeKind.isPredictive(mInputMode) && DictionaryLoader.autoLoad(this, settings, mLanguage)) {
			return true;
		}
		return super.onNumber(key, hold, repeat);
	}


	@Override
	protected TraditionalT9 getFinalContext() {
		return this;
	}


	private void runHeavyInitTasks() {
		LanguageCollection.init(getApplicationContext());
		DataStore.init(getApplicationContext());
		mindReader.init(getApplicationContext()); // create the database tables, if they don't exist already
		Logger.d(LOG_TAG, "Heavy initialization tasks completed successfully");
	}


	private void runBackgroundTasks() {
		SupremeExecutor.submit(() -> {
			mindReader.persist();
			voiceInputOps.forceAlternativeInput(false).enableOfflineMode();
			if (!DictionaryLoader.isRunning()) {
				DataStore.saveWordPairs();
				DataStore.normalizeNext();
			}
		});
	}
}
