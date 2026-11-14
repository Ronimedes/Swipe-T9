package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

import io.github.sspanak.tt9.commands.CmdUndo;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.util.chars.Characters;

public class SoftKeyTextLeft extends SoftKeyText {
	private final CmdUndo undo = new CmdUndo();
	private boolean questionHold = false;

	private boolean useQuestionHold() {
		return tt9 != null && tt9.getSettings().isMainLayoutNumpad()
			&& tt9.getLanguage() != null && "de".equals(tt9.getLanguage().getLocale().getLanguage())
			&& !tt9.isInputModeNumeric() && !isTextEditingOn() && !tt9.isFnPanelVisible();
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
			tt9.onText("?", false);
		} else {
			super.handleHold();
		}
	}

	@Override
	protected String getHoldText() {
		return useQuestionHold() ? "?" : super.getHoldText();
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
		if (tt9.isInputModeNumeric()) return ",";
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
