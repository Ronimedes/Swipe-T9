package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

import io.github.sspanak.tt9.commands.CmdUndo;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.chars.Characters;

public class SoftKeyTextLeft extends SoftKeyText {
	private final CmdUndo undo = new CmdUndo();
	private boolean questionHold = false;

	@Override
	protected String getTopText() {
		if (isUnified()) return tt9.isInputModeNumeric() ? "," : "0";
		return super.getTopText();
	}


	/**
	 * v0.2.13: same position in both modes. Letters: tap "!", hold "0".
	 * Numbers: tap "0", hold ",". The characters picker moved to holding the space bar.
	 */
	private boolean isUnified() {
		return SwipeT9Layout.isUnifiedBottomRow(tt9) && !isTextEditingOn();
	}

	private boolean useQuestionHold() {
		return isUnified();
	}

	@Override
	protected boolean handlePress() {
		questionHold = false;
		return super.handlePress();
	}

	@Override
	protected void handleHold() {
		if (useQuestionHold()) {
			questionHold = true;
			preventRepeat();
			vibrate(io.github.sspanak.tt9.ui.Vibration.getHoldVibration());
			tt9.onText(tt9.isInputModeNumeric() ? "," : "0", false);
		} else {
			super.handleHold();
		}
	}

	@Override
	protected String getHoldText() {
		return useQuestionHold() ? (tt9.isInputModeNumeric() ? "Komma" : "Null") : super.getHoldText();
	}


	public SoftKeyTextLeft(Context context) { super(context); }
	public SoftKeyTextLeft(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyTextLeft(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

	@Override
	protected boolean handleRelease() {
		if (questionHold) {
			questionHold = false;
			return true;
		}
		return isTextEditingOn() ? undo.run(tt9) : super.handleRelease();
	}

	@Override
	protected String getAccessibilityText() {
		return isTextEditingOn() ? undo.getName(tt9) : super.getAccessibilityText();
	}

	@Override
	protected String getKeyChar() {
		if (tt9 == null) return "";
		if (tt9.isInputTypePhone()) return "*";
		if (tt9.isInputModeNumeric()) return isUnified() ? "0" : ",";
		if (tt9.isTextEditingActive()) return CmdUndo.iconTxt;

		return Characters.getChar(tt9.getLanguage(), SettingsStore.SOFT_KEY_TEXT_LEFT_DEFAULT);
	}

	@Override
	protected String getTitle() {
		return isTextEditingOn() ? "" : super.getTitle();
	}

	@Override
	protected int getCentralIcon() {
		return isTextEditingOn() && !shouldHide() ? undo.getIcon() : -1;
	}
}
