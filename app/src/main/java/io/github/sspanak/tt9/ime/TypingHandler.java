package io.github.sspanak.tt9.ime;
import io.github.sspanak.tt9.R;

import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;

import io.github.sspanak.tt9.commands.CmdSuggestionNext;
import io.github.sspanak.tt9.commands.CmdAddWord;
import io.github.sspanak.tt9.commands.CmdSuggestionPrevious;
import io.github.sspanak.tt9.commands.Command;
import io.github.sspanak.tt9.db.words.DictionaryLoader;
import io.github.sspanak.tt9.hacks.InputType;
import io.github.sspanak.tt9.ime.helpers.CursorOps;
import io.github.sspanak.tt9.ime.helpers.InputConnectionAsync;
import io.github.sspanak.tt9.ime.helpers.InputModeValidator;
import io.github.sspanak.tt9.ime.helpers.SuggestionOps;
import io.github.sspanak.tt9.ime.helpers.TextField;
import io.github.sspanak.tt9.ime.helpers.TextSelection;
import io.github.sspanak.tt9.ime.mindreader.MindReader;
import io.github.sspanak.tt9.ime.modes.InputMode;
import io.github.sspanak.tt9.ime.modes.InputModeKind;
import io.github.sspanak.tt9.languages.Language;
import io.github.sspanak.tt9.languages.LanguageCollection;
import io.github.sspanak.tt9.languages.LanguageKind;
import io.github.sspanak.tt9.languages.NaturalLanguage;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9Memory;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9Blocklist;
import io.github.sspanak.tt9.ui.main.keys.SwipeT9WordMenu;
import io.github.sspanak.tt9.db.DataStore;
import io.github.sspanak.tt9.util.Text;
import io.github.sspanak.tt9.util.chars.Characters;

public abstract class TypingHandler extends KeyPadHandler {
	// internal settings/data
	@NonNull protected InputType inputType = new InputType(null, null);
	@NonNull protected TextField textField = new TextField(null, null, null);
	@NonNull protected TextSelection textSelection = new TextSelection(null, null);
	@NonNull protected SuggestionOps suggestionOps = new SuggestionOps(null, null, null, null, null, null, null, null, null, null);

	@NonNull protected CmdSuggestionNext cmdNextSuggestion = new CmdSuggestionNext();
	@NonNull protected CmdSuggestionPrevious cmdPreviousSuggestion = new CmdSuggestionPrevious();

	@Nullable private Handler shiftStateDebounceHandler;
	private boolean lowercaseNextJoinedPart = false;
	// v0.2.12: set by ▶ in the suggestion bar: the next swiped word is glued on without a space.
	private boolean joinNextSwipeWord = false;
	// Backspace directly after a swipe should delete that swiped word, not
	// shorten its digit sequence and unexpectedly show a longer candidate.
	private boolean eraseSwipeWordOnBackspace = false;
	@Nullable private float[] pendingSwipeDrawing;
	@Nullable private String pendingSwipeLanguage;

	protected void markSwipeT9Entered() {
		eraseSwipeWordOnBackspace = true;
	}

	protected void rememberPendingSwipe(float[] drawing) {
		pendingSwipeDrawing = drawing;
		pendingSwipeLanguage = mLanguage == null ? null : mLanguage.getLocale().getLanguage();
	}

	private void onSwipeCandidateTapped() {
		String selected = suggestionOps.getCurrent();
		float[] drawing = pendingSwipeDrawing;
		String language = pendingSwipeLanguage;
		boolean accepted = onOK();
		pendingSwipeDrawing = null;
		pendingSwipeLanguage = null;
		if (accepted && drawing != null && language != null && !shouldBeOff()) {
			SwipeT9Memory.remember(this, language, selected, drawing);
		}
		if (accepted) {
			onSwipeT9Selected(selected, drawing != null && language != null && SwipeT9Memory.isEnabled(this));
		}
	}

	/** v0.2.9: overridden to note in the swipe log which word was tapped and whether it was learned. */
	protected void onSwipeT9Selected(String word, boolean learned) {}

	// input
	@NonNull protected ArrayList<Integer> allowedInputModes = new ArrayList<>();
	@NonNull protected InputMode mInputMode = InputMode.getInstance(null, null, null, null, InputMode.MODE_PASSTHROUGH);

	// language
	protected ArrayList<Integer> mEnabledLanguages;
	protected Language mLanguage;

	// output: suggestions
	abstract protected void onAcceptSuggestionsDelayed(String s);
	abstract protected void getSuggestions(double loadingId, @Nullable String currentWord, @Nullable Runnable onComplete);

	// output: mind-reading
	@NonNull protected final MindReader mindReader = new MindReader();
	abstract protected void autoCompleteOnNumber(double loadingId, @NonNull String[] surroundingChars, @Nullable String lastWord, int number);
	abstract protected void guessNextWord(@NonNull String[] surroundingText, @Nullable String lastWord);
	abstract protected boolean shouldAcceptGuessesOnNumber(int key);


	protected void createSuggestionBar() {
		suggestionOps = new SuggestionOps(this, settings, mainView, appHacks, inputType, textField, statusBar, this::onAcceptSuggestionsDelayed, this::onSwipeCandidateTapped, this::onSwipeT9SuggestionHold);
	}


	/** A tappable ABC-word chip, independent of the per-letter suggestion list. */
	protected void refreshSwipeT9AbcWord() {
		if (mainView == null || mainView.getView() == null) return;
		TextView chip = mainView.getView().findViewById(R.id.swipe_t9_abc_word);
		if (chip == null) return;
		if (!settings.isMainLayoutNumpad() || !InputModeKind.isABC(mInputMode)
			|| mLanguage == null || shouldBeOff() || !inputType.isText()) {
			chip.setVisibility(View.GONE);
			return;
		}
		String word = textField.getSurroundingWord(mLanguage).trim();
		if (word.length() < 2 || word.length() > 40) {
			chip.setVisibility(View.GONE);
			return;
		}
		chip.setText("＋ " + word);
		chip.setContentDescription(getString(R.string.swipe_t9_add_word_abc, word));
		chip.setOnClickListener(v -> {
			CmdAddWord command = new CmdAddWord();
			if (command.isAvailable(getFinalContext())) command.run(getFinalContext());
		});
		chip.setVisibility(View.VISIBLE);
	}

	private void onSwipeT9SuggestionHold() {
		CmdAddWord addWord = new CmdAddWord();
		final boolean german = settings.isMainLayoutNumpad() && mLanguage != null
			&& "de".equals(mLanguage.getLocale().getLanguage());
		final String word = suggestionOps.getCurrent();
		final String trimmed = word == null ? "" : word.trim();
		final boolean canHide = german && InputModeKind.isPredictive(mInputMode)
			&& trimmed.length() >= 2 && Character.isLetter(trimmed.codePointAt(0));
		final boolean canAdd = german && addWord.isAvailable(getFinalContext());

		if ((canHide || canAdd) && mainView != null && mainView.getView() != null) {
			final Language language = mLanguage;
			Runnable hide = !canHide ? null : () -> {
				SwipeT9Blocklist.block(this, trimmed);
				DataStore.deleteCustomWord(() -> {}, language, trimmed);
				if (!shouldBeOff()) getSuggestions(0, null, null);
			};
			Runnable add = !canAdd ? null : () -> {
				if (addWord.isAvailable(getFinalContext())) addWord.run(getFinalContext());
			};
			if (SwipeT9WordMenu.show(mainView.getView(), trimmed.isEmpty() ? "?" : trimmed,
				settings.getKeyboardTextColor(), settings.getKeyboardBackground(), hide, add)) {
				return;
			}
			if (canAdd) {
				addWord.run(getFinalContext());
				return;
			}
		}
		onOK(KeyEvent.KEYCODE_UNKNOWN);
	}

	public boolean shouldBeOff() {
		return getCurrentInputConnection() == null || InputModeKind.isPassthrough(mInputMode);
	}


	@Override
	protected void onInit() {
		super.onInit();
		mindReader.setSettings(settings);
	}


	protected void cleanUp() {
		super.cleanUp();
		InputConnectionAsync.destroy();
		mindReader.destroy();
	}


	@Override
	protected boolean onStart(EditorInfo field, boolean restarting) {
		lowercaseNextJoinedPart = false;
		eraseSwipeWordOnBackspace = false;
		pendingSwipeDrawing = null;
		pendingSwipeLanguage = null;
		boolean restart = restarting || textField.equals(getCurrentInputConnection(), field);

		setInputField(field);

		// 1. In case we are back from Settings screen, update the language list
		// 2. If the connected app hints it is in a language different than the current one,
		// we try to switch.
		boolean languageChanged = determineLanguage();

		// ignore multiple calls for the same field, caused by requestShowSelf() -> showWindow(),
		// or weirdly functioning apps, such as the Qin SMS app
		if (restart && !languageChanged && appHacks.isRestartForbidden() && mInputMode.getId() == determineInputModeId()) {
			super.onStart(field, restarting);
			return false;
		}
		settings.setDefaultChars(mLanguage, false);
		((NaturalLanguage) mLanguage).updateKeyCharacters(settings);
		resetKeyRepeat();
		mInputMode = determineInputMode();
		determineTextCase();
		suggestionOps.set(null);

		// don't use surroundingText cache on start up
		final String[] surroundingText = textField.getSurroundingStringForAutoAssistance(settings, mInputMode);
		updateShiftState(surroundingText[0], false, false);
		mindReader
			.setLanguage(mLanguage)
			.seed(getFinalContext(), mLanguage)
			.setContext(mInputMode, mLanguage, surroundingText, null);

		return super.onStart(field, restarting);
	}


	protected void setInputField(EditorInfo field) {
		if (textField.equals(getCurrentInputConnection(), field)) {
			return;
		}

		InputMethodService context = field != null ? this : null;
		inputType = new InputType(context, field);
		textField = new TextField(context, settings, field);
		textSelection = new TextSelection(context, inputType);

		// changing the TextField and notifying all interested classes is an atomic operation
		appHacks.setDependencies(inputType, textField, textSelection);
		suggestionOps.setDependencies(appHacks, inputType, textField, statusBar);
	}


	protected void validateLanguages() {
		mEnabledLanguages = InputModeValidator.validateEnabledLanguages(mEnabledLanguages);
		mLanguage = InputModeValidator.validateLanguage(mLanguage, mEnabledLanguages);
		settings.saveInputLanguage(mLanguage.getId());
		settings.saveEnabledLanguageIds(mEnabledLanguages);
	}


	protected void onFinishTyping(boolean willExitInput) {
		lowercaseNextJoinedPart = false;
		if (shiftStateDebounceHandler != null) {
			shiftStateDebounceHandler.removeCallbacksAndMessages(null);
			shiftStateDebounceHandler = null;
		}

		if (willExitInput) {
			mInputMode = InputMode.getInstance(null, null, null, null, InputMode.MODE_PASSTHROUGH);
			setInputField(null);
		} else {
			mInputMode.reset();
		}
	}


	/**
	 * Swipe-T9: remove the previous complete word on a single long press of Backspace.
	 * Reuses the same text deletion helper as the existing Fast Delete implementation.
	 */
	public boolean onDeletePreviousWord() {
		pendingSwipeDrawing = null;
		eraseSwipeWordOnBackspace = false;
		lowercaseNextJoinedPart = false;
		if (InputModeKind.isPassthrough(mInputMode)) {
			return false;
		}
		mindReader.clearContext();
		suggestionOps.cancelDelayedAccept();
		resetKeyRepeat();
		final boolean hadSelection = !textSelection.isEmpty();
		mInputMode.beforeDeleteText();
		suggestionOps.commitCurrent(false, true);
		if (hadSelection) {
			deleteText(false);
		} else {
			String before = textField.getStringBeforeCursor(100);
			int start = before.length();
			while (start > 0) {
				int ch = before.codePointBefore(start);
				if (!Character.isLetterOrDigit(ch) && ch != '_') break;
				start -= Character.charCount(ch);
			}
			// Keep preceding whitespace/punctuation intact. If no word, delete one character.
			textField.deleteChars(mLanguage, Math.max(before.length() - start, 1));
		}
		mInputMode.reset();
		updateShiftStateDebounced(null, mInputMode.noSuggestions(), false);
		recompose(0, hadSelection);
		forceShowWindow();
		return true;
	}

	@Override
	public boolean onBackspace(int repeat) {
		pendingSwipeDrawing = null;
		lowercaseNextJoinedPart = false;
		if (eraseSwipeWordOnBackspace && repeat == 0 && !shouldBeOff()) {
			eraseSwipeWordOnBackspace = false;
			return onDeletePreviousWord();
		}
		eraseSwipeWordOnBackspace = false;
		// Dialer fields seem to handle backspace on their own, and we must ignore it,
		// otherwise, a keyDown race condition occurs for all keys.
		if (InputModeKind.isPassthrough(mInputMode)) {
			return false;
		}

		mindReader.clearContext();

		if (appHacks.onBackspace(settings, mInputMode)) {
			mInputMode.reset();
			mainView.renderDynamicKeys();
			return false;
		}

		suggestionOps.cancelDelayedAccept();
		resetKeyRepeat();

		if (settings.getBackspaceAcceleration() && repeat > 0 && repeat % SettingsStore.BACKSPACE_ACCELERATION_REPEAT_DEBOUNCE != 0) {
			return true;
		}

		mInputMode.beforeDeleteText();

		// load new words only if there is no selected text, because it would be confusing
		if (repeat == 0 && mInputMode.onBackspace() && textSelection.isEmpty()) {
			final Runnable onLoad = InputModeKind.isRecomposing(mInputMode) ? null : () -> recompose(repeat, false);
			getSuggestions(0, null, onLoad);
		} else {
			suggestionOps.commitCurrent(false, true);
			mInputMode.reset();
			deleteText(settings.getBackspaceAcceleration() && repeat > 0);
			updateShiftStateDebounced(null, mInputMode.noSuggestions(), false); // backspace may change the text too much, so no beforeCursor cache for now
			recompose(repeat, !textSelection.isEmpty());
		}

		return true;
	}


	/**
	 * onNumber
	 *
	 * @param key     Must be a number from 1 to 9, not a "KeyEvent.KEYCODE_X"
	 * @param hold    If "true" we are calling the handler, because the key is being held.
	 * @param repeat  If "true" we are calling the handler, because the key was pressed more than once
	 * @return boolean
	 */
	protected boolean onNumber(int key, boolean hold, int repeat) {
		pendingSwipeDrawing = null;
		eraseSwipeWordOnBackspace = false;
		suggestionOps.cancelDelayedAccept();

		hold = hold && settings.getHoldToType();
		String[] surroundingChars = textField.getSurroundingStringForAutoAssistance(settings, mInputMode);
		String lastWord = null;

		// Automatically accept the previous word, when the next one is a space or punctuation,
		// instead of requiring "OK" before that.
		// First pass, analyze the incoming key press and decide whether it could be the start of
		// a new word. In case we do accept it, we preserve the suggestion list instead of clearing,
		// to prevent flashing while the next suggestions are being loaded.

		if (mInputMode.shouldAcceptPreviousSuggestion(suggestionOps.getCurrent(), key, hold) || shouldAcceptGuessesOnNumber(key)) {
			// WARNING! Ensure the code after "acceptIncompleteAndKeepList()" does not depend on
			// the suggestions in SuggestionOps, since we don't clear that list.
			lastWord = suggestionOps.acceptIncompleteAndKeepList();
			mInputMode.onAcceptSuggestion(lastWord);
			surroundingChars = autoCorrectSpace(lastWord, surroundingChars, false, key);
			mindReader.setContext(mInputMode, mLanguage, surroundingChars, lastWord);
		}

		// Auto-adjust the text case before each word/char, if the InputMode supports it.
		mInputMode.determineNextWordTextCase(surroundingChars[0], key);
		if (lowercaseNextJoinedPart) {
			// Do not start the second half of Haustuer in uppercase.
			if (key >= 2 && key <= 9 && !hold) {
				mInputMode.setTextCase(InputMode.CASE_LOWER);
			}
			lowercaseNextJoinedPart = false;
		}

		if (!mInputMode.onNumber(key, hold, repeat, surroundingChars)) {
			forceShowWindow();
			return false;
		}

		if (mInputMode.shouldSelectNextSuggestion() && !mInputMode.noSuggestions()) {
			final Command scroll = LanguageKind.isRTL(mLanguage) ? cmdPreviousSuggestion : cmdNextSuggestion;
			scroll.run(getFinalContext());
			suggestionOps.scheduleDelayedAccept(mInputMode.getAutoAcceptTimeout());
		} else {
			final double loadingId = Math.random();
			autoCompleteOnNumber(loadingId, surroundingChars, lastWord, key);
			getSuggestions(loadingId, null, null);
		}

		return true;
	}



	/** The same emoji spacing rule is used for direct text and the emoji suggestion tray. */
	protected boolean isSwipeEmoji(String text) {
		return text != null && settings.isMainLayoutNumpad()
			&& mLanguage != null && "de".equals(mLanguage.getLocale().getLanguage())
			&& (Characters.isBuiltInEmoji(text)
				|| text.codePoints().anyMatch(c -> (c >= 0x1F300 && c <= 0x1FAFF) || (c >= 0x2600 && c <= 0x27BF) || c == 0x2B50));
	}

	public boolean onText(String text, boolean validateOnly) {
		if (!validateOnly) {
			joinNextSwipeWord = false;
			pendingSwipeDrawing = null;
			eraseSwipeWordOnBackspace = false;
			lowercaseNextJoinedPart = false;
		}
		if (mInputMode.shouldIgnoreText(text)) {
			return false;
		}

		if (validateOnly) {
			return true;
		}

		suggestionOps.cancelDelayedAccept();
		final boolean spacedEmoji = isSwipeEmoji(text);

		String[] surroundingChars;

		// accept the previously typed word (if any)
		String lastWord = suggestionOps.acceptIncomplete();
		if (lastWord.isEmpty()) {
			surroundingChars = textField.getSurroundingStringForAutoAssistance(settings, mInputMode);
		} else {
			mInputMode.onAcceptSuggestion(lastWord);
			surroundingChars = autoCorrectSpace(
				lastWord,
				textField.getSurroundingStringForAutoAssistance(settings, mInputMode),
				false,
				-1
			);
		}

		mindReader.setContext(mInputMode, mLanguage, surroundingChars, text);

		// Insert spaces around smileys, but never duplicate an existing space.
		if (spacedEmoji && !surroundingChars[0].isEmpty()
			&& !Character.isWhitespace(surroundingChars[0].charAt(surroundingChars[0].length() - 1))) {
			textField.setText(" ");
			surroundingChars[0] += " ";
		}
		// "type" and accept the new word
		mInputMode.onAcceptSuggestion(text);
		textField.setText(text);
		surroundingChars[0] += text;
		surroundingChars = autoCorrectSpace(text, surroundingChars, true, -1);
		if (spacedEmoji && !surroundingChars[0].endsWith(" ")
			&& (surroundingChars[1].isEmpty() || !Character.isWhitespace(surroundingChars[1].charAt(0)))) {
			textField.setText(" ");
			surroundingChars[0] += " ";
		}

		if (surroundingChars[0].endsWith(Characters.getSpace(mLanguage))) {
			waitForSpaceTrimKey();
		}

		forceShowWindow();

		mInputMode.determineNextWordTextCase(surroundingChars[0], -1);
		updateShiftState(surroundingChars[0], false, false);

		return true;
	}


	/**
	 * Swipe-T9 v0.2.10: every swiped word starts as a new word.
	 * A word that is still being composed (e.g. the previous swipe) is accepted first,
	 * then a space is inserted unless the cursor already follows a space or an opening
	 * bracket/quote. Editing the previous word (backspace, suggestion bar) is unaffected,
	 * because this only runs when a new swipe arrives.
	 */
	protected void prepareSwipeT9WordStart() {
		if (shouldBeOff() || mLanguage == null) return;
		suggestionOps.cancelDelayedAccept();
		if (mInputMode.getSequenceLength() > 0) {
			String lastWord = suggestionOps.acceptIncomplete();
			if (!lastWord.isEmpty()) {
				onSwipeT9WordAccepted(lastWord);
				mInputMode.onAcceptSuggestion(lastWord);
				// v0.2.17: next-word predictions learn from swiped words too
				mindReader.setContext(mInputMode, mLanguage, textField.getSurroundingStringForAutoAssistance(settings, mInputMode), lastWord);
			}
		}
		if (joinNextSwipeWord) {
			joinNextSwipeWord = false;
			mInputMode.setTextCase(InputMode.CASE_LOWER);
			return;
		}
		String before = textField.getStringBeforeCursor(1);
		if (!before.isEmpty()) {
			char c = before.charAt(before.length() - 1);
			if (!Character.isWhitespace(c) && "([{„\"'‚«»¿¡-/".indexOf(c) < 0) {
				textField.setText(" ");
			}
		}
		String[] surrounding = textField.getSurroundingStringForAutoAssistance(settings, mInputMode);
		mInputMode.determineNextWordTextCase(surrounding[0], -1);
	}

	/**
	 * v0.2.12: the arrows in the suggestion bar finish the current word.
	 * ▶ (joinNext): no space; the next swiped word is appended directly (Haus ▶ Tür = Haustür).
	 * ◀: finish with a space.
	 * @return false when nothing is being typed, so the arrows keep moving the cursor.
	 */
	public boolean finishSwipeT9Word(boolean joinNext) {
		if (shouldBeOff() || !InputModeKind.isPredictive(mInputMode) || mInputMode.getSequenceLength() <= 0) {
			return false;
		}
		final String word = suggestionOps.getCurrent();
		if (!onJoinWithoutSpace()) {
			return false;
		}
		onSwipeT9WordAccepted(word);
		if (joinNext) {
			joinNextSwipeWord = true;
			return true;
		}
		joinNextSwipeWord = false;
		lowercaseNextJoinedPart = false;
		textField.setText(" ");
		String[] surrounding = textField.getSurroundingStringForAutoAssistance(settings, mInputMode);
		mInputMode.determineNextWordTextCase(surrounding[0], -1);
		updateShiftState(surrounding[0], false, false);
		return true;
	}

	/**
	 * v0.2.16 Enter key: accept the word being typed (one press) and start a new line.
	 * @return false in single-line fields, where Enter keeps triggering the field's action.
	 */
	public boolean swipeT9NewLine() {
		if (shouldBeOff() || !inputType.isMultilineText()) return false;
		suggestionOps.cancelDelayedAccept();
		if (mInputMode.getSequenceLength() > 0) {
			String lastWord = suggestionOps.acceptIncomplete();
			if (!lastWord.isEmpty()) {
				onSwipeT9WordAccepted(lastWord);
				mInputMode.onAcceptSuggestion(lastWord);
				// v0.2.17: next-word predictions learn from swiped words too
				mindReader.setContext(mInputMode, mLanguage, textField.getSurroundingStringForAutoAssistance(settings, mInputMode), lastWord);
			}
		}
		textField.setText("\n");
		String[] surrounding = textField.getSurroundingStringForAutoAssistance(settings, mInputMode);
		mInputMode.determineNextWordTextCase(surrounding[0], -1);
		return true;
	}

	/** v0.2.10: overridden to note the accepted word in the swipe log. */
	protected void onSwipeT9WordAccepted(String word) {}


	/**
	 * Swipe-T9: accept the visible suggestion WITHOUT the automatic trailing space.
	 * The next T9 sequence can therefore continue a compound word.
	 */
	public boolean onJoinWithoutSpace() {
		if (shouldBeOff() || !InputModeKind.isPredictive(mInputMode)
			|| mInputMode.getSequenceLength() <= 0 || suggestionOps.isEmpty()
			|| suggestionOps.containsOnlyGuesses()) {
			return false;
		}

		suggestionOps.cancelDelayedAccept();
		final String word = suggestionOps.acceptCurrent();
		if (word.isEmpty() || Characters.PLACEHOLDER.equals(word)) {
			return false;
		}

		mInputMode.onAcceptSuggestion(word);
		lowercaseNextJoinedPart = true;
		mInputMode.setTextCase(InputMode.CASE_LOWER);
		mindReader.clearContext();
		resetKeyRepeat();
		updateShiftState(textField.getStringBeforeCursor(), false, false);
		forceShowWindow();
		return true;
	}

	@NonNull
	protected String[] autoCorrectSpace(@Nullable String currentWord, @NonNull String[] surroundingChars, boolean isWordAcceptedManually, int nextKey) {
		if (currentWord == null || currentWord.isEmpty() || !settings.isAutoAssistanceOn(mInputMode)) {
			return surroundingChars;
		}

		String previousChars = surroundingChars[0];
		final String nextChars = surroundingChars[1];

		if (!inputType.isRustDesk() && mInputMode.shouldDeletePrecedingSpace(previousChars)) {
			textField.deletePrecedingSpace(currentWord);
			if (previousChars.endsWith(" " + currentWord) && previousChars.length() > currentWord.length()) {
				final int precedingSpace = previousChars.length() - currentWord.length() - 1;
				previousChars = previousChars.substring(0, precedingSpace) + currentWord;
			}
		}

		if (mInputMode.shouldAddPrecedingSpace(previousChars)) {
			textField.addPrecedingSpace(currentWord);
			if (previousChars.endsWith(currentWord)) {
				final int startOfWord = previousChars.length() - currentWord.length();
				previousChars = previousChars.substring(0, startOfWord) + " " + currentWord;
			}
		}

		if (mInputMode.shouldAddTrailingSpace(currentWord, previousChars, nextChars, isWordAcceptedManually, nextKey)) {
			textField.setText(" ");
			previousChars += " ";
		}

		return new String[] { previousChars, nextChars };
	}


	private void deleteText(boolean deleteMany) {
		int charsToDelete = 1;

		if (!textSelection.isEmpty()) {
			charsToDelete = textSelection.length();
			textSelection.clear(false);
		} else if (deleteMany) {
			charsToDelete = textField.getComposingText().length();
			charsToDelete = charsToDelete > 0 ? charsToDelete : Math.max(textField.getPaddedWordBeforeCursorLength(), 1);
		}

		textField.deleteChars(mLanguage, charsToDelete);
	}


	/**
	 * determineLanguage
	 * Restore the last language or auto-select a more appropriate one, if the application hints so.
	 * In case the settings are not valid, we will fall back to the default language.
	 */
	private boolean determineLanguage() {
		mEnabledLanguages = settings.getEnabledLanguageIds();

		int oldLang = mLanguage != null ? mLanguage.getId() : -1;
		mLanguage = LanguageCollection.getLanguage(settings.getInputLanguage());
		validateLanguages();

		Language appLanguage = textField.getLanguage(mEnabledLanguages);
		if (appLanguage != null) {
			mLanguage = appLanguage;
		}

		return oldLang != mLanguage.getId();
	}


	/**
	 * determineTextCase
	 * Restore the last used text case or auto-select a new one based on the input field properties.
	 */
	protected void determineTextCase() {
		InputModeValidator.validateTextCase(mInputMode, settings.getTextCase());
	}


	/**
	 * determineInputModeId
	 * Return the last input mode ID or choose a more appropriate one.
	 * Some input fields support only numbers or are not suited for predictions (e.g. password fields).
	 * Others do not support text retrieval or composing text, or the AppHacks detected them as incompatible with us.
	 * We do not want to handle any of these, hence we pass through all input to the system.
	 */
	protected int determineInputModeId() {
		if (!inputType.isValid() || (inputType.isLimited() && !inputType.isTeamsInitial() && !inputType.isTermux())) {
			return InputMode.MODE_PASSTHROUGH;
		}

		allowedInputModes = new ArrayList<>(inputType.determineInputModes(getApplicationContext()));
		if (LanguageKind.isJapanese(mLanguage)) {
			determineJapaneseInputModes();
		}

		if (!mLanguage.hasABC()) {
			allowedInputModes.remove((Integer) InputMode.MODE_ABC);
		}

		if (!settings.getPredictiveMode()) {
			allowedInputModes.remove((Integer) InputMode.MODE_PREDICTIVE);
		}

		return InputModeValidator.validateMode(settings.getInputMode(), allowedInputModes);
	}


	/**
	 * In Japanese, Hiragana and Katakana modes are the equivalents of ABC mode in other languages.
	 * So when typing letters is possible (ABC mode allowed), we replace ABC with these two modes.
	 */
	private void determineJapaneseInputModes() {
		if (allowedInputModes.contains(InputMode.MODE_ABC)) {
			allowedInputModes.add(InputMode.MODE_HIRAGANA);
			allowedInputModes.add(InputMode.MODE_KATAKANA);
		}
	}


	/**
	 * determineInputMode
	 * Same as determineInputModeId(), but returns an actual InputMode.
	 */
	protected InputMode determineInputMode() {
		return InputMode.getInstance(settings, mLanguage, inputType, textField, determineInputModeId());
	}


	/**
	 * Try to recompose the current word after a backspace operation. If successful, load new
	 * suggestions. Otherwise, reset the InputMode.
	 */
	private void recompose(int backspaceRepeat, boolean isTextSelected) {
		if (!settings.getBackspaceRecomposing() || backspaceRepeat > 0 || isFnPanelVisible() || isTextSelected || !suggestionOps.isEmpty() || DictionaryLoader.isRunning()) {
			return;
		}

		final String previousWord = mInputMode.recompose();
		if (textField.recompose(previousWord)) {
			getSuggestions(0, previousWord, null);
		} else {
			mInputMode.reset();
		}
	}


	@Override
	public void onUpdateSelection(int oldSelStart, int oldSelEnd, int newSelStart, int newSelEnd, int candidatesStart, int candidatesEnd) {
//		Logger.d("onUpdateSelection", "old (" + oldSelStart + ", " + oldSelEnd + ") => new (" + newSelStart + ", " + newSelEnd + "); candidates = (" + candidatesStart + ", " + candidatesEnd + ")");

		super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd);
		textSelection.onSelectionUpdate(newSelStart, newSelEnd);

		// in case the app has modified the InputField and moved the cursor without notifying us...
		if (CursorOps.isInputReset(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
			stopWaitingForSpaceTrimKey();
			mindReader.clearContext();
			if (!appHacks.acceptComposingTextOnCursorReset(mInputMode, suggestionOps, textField)) {
				suggestionOps.clear();
			}
			return;
		}

		// If the cursor moves while composing a word (usually, because the user has touched the screen outside the word), we must
		// end typing end accept the word. Otherwise, the cursor would jump back at the end of the word, after the next key press.
		// This is confusing from user perspective, so we want to avoid it.
		if (CursorOps.isMovedWhileTyping(newSelStart, newSelEnd, candidatesStart, candidatesEnd)) {
			stopWaitingForSpaceTrimKey();
			mInputMode.onCursorMove(suggestionOps.acceptIncomplete());
			mindReader.clearContext();
			return;
		}

		// Prevent deleting a space using the left arrow key, if the user has moved the cursor to another
		// location. This prevents undesired deletion of the space, in the middle of the text.
		if (CursorOps.isMovedFar(newSelStart, newSelEnd, oldSelStart, oldSelEnd)) {
			stopWaitingForSpaceTrimKey();
		}
	}


	protected void updateShiftStateDebounced(@Nullable String beforeCursor, boolean determineTextCase, boolean onlyWhenLetters) {
		if (shiftStateDebounceHandler == null) {
			shiftStateDebounceHandler = new Handler(Looper.getMainLooper());
		} else {
			shiftStateDebounceHandler.removeCallbacksAndMessages(null);
		}
		shiftStateDebounceHandler.postDelayed(() -> updateShiftState(beforeCursor, determineTextCase, onlyWhenLetters), SettingsStore.SHIFT_STATE_DEBOUNCE_TIME);
	}


	protected void updateShiftState(@Nullable String beforeCursor, boolean determineTextCase, boolean onlyWhenLetters) {
		if (onlyWhenLetters && !new Text(suggestionOps.getCurrent()).isAlphabetic()) {
			return;
		}

		if (determineTextCase) {
			beforeCursor = beforeCursor != null ? beforeCursor : textField.getStringBeforeCursor();
			mInputMode.determineNextWordTextCase(beforeCursor, -1);
		}

		getDisplayTextCase(mLanguage, mInputMode.getTextCase());
		setStatusIcon(mInputMode, mLanguage);
		mainView.renderDynamicKeys();
		if (!mainView.isTextEditingPaletteShown() && !mainView.isCommandPaletteShown()) {
			statusBar.setText(mInputMode);
		}
	}
}
