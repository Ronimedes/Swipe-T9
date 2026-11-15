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

    public void recordSwipeT9ShownSuggestions(ArrayList<String> suggestions) {
        if (pendingSwipeLogId < 0 || mInputMode == null) return;
        if (mInputMode.getSequenceLength() < pendingSwipeSequenceLength) return;
        SwipeT9History.updateCandidates(this, pendingSwipeLogId, "shown", suggestions);
        pendingSwipeLogId = -1;
    }

    public boolean onSwipeT9Sequence(String digits, float[] drawing, long logId) {
		if (digits == null || digits.isEmpty() || digits.length() > 32 || shouldBeOff()
			|| isFnPanelVisible() || !InputModeKind.isPredictive(mInputMode)) return false;
        pendingSwipeLogId = -1;
        if (logId != -1 && isSwipeT9DiagnosticAllowed()) {
            pendingSwipeLogId = logId;
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
                    pendingSwipeLogId = -1;
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
