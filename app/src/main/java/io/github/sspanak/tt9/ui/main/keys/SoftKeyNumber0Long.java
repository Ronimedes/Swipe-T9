package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

import io.github.sspanak.tt9.ui.Vibration;
import io.github.sspanak.tt9.util.chars.Characters;

public class SoftKeyNumber0Long extends SoftKeyNumber0 {
	private boolean symbolsOpened = false;

	public SoftKeyNumber0Long(Context context) { super(context); }
	public SoftKeyNumber0Long(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyNumber0Long(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }


	/** v0.2.13: in the unified German layout this is always the space bar; hold opens the characters. */
	private boolean isUnified() {
		return SwipeT9Layout.isUnifiedBottomRow(tt9) && !tt9.isTextEditingActive();
	}


	@Override
	public boolean isHoldEnabled() {
		return isUnified() || super.isHoldEnabled();
	}


	@Override
	protected boolean handlePress() {
		symbolsOpened = false;
		return super.handlePress();
	}


	@Override
	protected void handleHold() {
		if (isUnified()) {
			preventRepeat();
			symbolsOpened = true;
			vibrate(Vibration.getHoldVibration());
			SwipeT9SymbolPopup.show(this, tt9, tt9.isInputModeNumeric());
			return;
		}
		super.handleHold();
	}


	@Override
	protected boolean handleRelease() {
		if (symbolsOpened) {
			symbolsOpened = false;
			return true;
		}
		if (isUnified() && tt9.isInputModeNumeric()) {
			return tt9.onText(" ", false);
		}
		return super.handleRelease();
	}


	@Override
	protected String getTitle() {
		if (isUnified()) {
			return Characters.SPACE;
		}
		return super.getTitle();
	}


	@Override
	protected String getHoldText() {
		if (isUnified()) {
			return "?#";
		}
		return super.getHoldText();
	}
}
