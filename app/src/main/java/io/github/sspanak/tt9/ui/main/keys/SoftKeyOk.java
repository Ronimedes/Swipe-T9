package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import io.github.sspanak.tt9.preferences.settings.SettingsStore;

public class SoftKeyOk extends BaseSwipeableKey {
	public SoftKeyOk(Context context) { super(context); }
	public SoftKeyOk(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyOk(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

	@Override
	protected void initColors(@NonNull SettingsStore settings) {
		backgroundColor = settings.getKeyOkBackgroundColor();
		borderColor = settings.getKeyOkBorderColor();
		rippleColor = settings.getKeyOkRippleColor();
		textColor = settings.getKeyOkTextColor();
	}

	private boolean actionByHold = false;

	/** v0.2.16: multi-line fields (WhatsApp, notes, e-mail): tap = new line, hold = the field's action. */
	private boolean isEnterKey() {
		return tt9 != null && tt9.getSettings().isMainLayoutNumpad() && tt9.getInputType().isMultilineText()
			&& !tt9.isTextEditingActive() && !tt9.isFnPanelVisible();
	}

	@Override protected String getTitle() {
		if (isEnterKey()) return "\u21B5";
		CharSequence layoutTitle = getText();
		return layoutTitle.length() == 0 ? "OK" : layoutTitle.toString();
	}

	@Override
	protected String getHoldText() {
		return isEnterKey() ? "OK" : super.getHoldText();
	}

	@Override
	public boolean isHoldEnabled() {
		return isEnterKey() || super.isHoldEnabled();
	}

	@Override
	protected boolean handlePress() {
		actionByHold = false;
		return super.handlePress();
	}

	@Override
	protected void handleHold() {
		if (isEnterKey()) {
			preventRepeat();
			actionByHold = true;
			vibrate(io.github.sspanak.tt9.ui.Vibration.getHoldVibration());
			if (!tt9.onOK()) tt9.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
			return;
		}
		super.handleHold();
	}

	@Override
	protected boolean handleRelease() {
		if (actionByHold) {
			actionByHold = false;
			return true;
		}
		if (validateTT9Handler() && isEnterKey() && tt9.swipeT9NewLine()) {
			return true;
		}
		if (validateTT9Handler() && !tt9.onOK()) {
			// If no standard editor action was performed, it probably means we can only type a new line,
			// so we simulate the hardware ENTER key.
			tt9.sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
			return true;
		}

		return false;
	}

	@Override
	protected float getTitleScale() {
		return tt9 != null && tt9.getSettings().isMainLayoutLarge() ? super.getTitleScale() : 1;
	}

	@Override
	public void render() {
		if (tt9 != null) {
			setEnabled(!tt9.isVoiceInputActive());
		}
		super.render();
	}
}
