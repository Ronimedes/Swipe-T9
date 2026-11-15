package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.KeyEvent;

import androidx.annotation.NonNull;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdNextInputMode;
import io.github.sspanak.tt9.commands.CmdNextKeyboard;
import io.github.sspanak.tt9.commands.CmdNextLanguage;
import io.github.sspanak.tt9.commands.CmdSelectKeyboard;
import io.github.sspanak.tt9.ime.modes.InputModeKind;
import io.github.sspanak.tt9.ime.helpers.Key;
import io.github.sspanak.tt9.preferences.settings.SettingsStore;
import io.github.sspanak.tt9.ui.Vibration;

public class SoftKeyLF4 extends BaseSwipeableKey {
	private final CmdNextLanguage nextLanguage = new CmdNextLanguage();
	private boolean switchedModeByHold = false;

	public SoftKeyLF4(Context context) {
		super(context);
		isSwipeable = true;
	}
	public SoftKeyLF4(Context context, AttributeSet attrs) {
		super(context, attrs);
		isSwipeable = true;
	}
	public SoftKeyLF4(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
		isSwipeable = true;
	}

	@Override
	protected void initColors(@NonNull SettingsStore settings) {
		backgroundColor = settings.getKeyLf4BackgroundColor();
		borderColor = settings.getKeyLf4BorderColor();
		cornerElementColor = settings.getKeyLf4CornerElementColor();
		rippleColor = settings.getKeyLf4RippleColor();
		centralIconColor = textColor = settings.getKeyLf4TextColor();
	}

	private boolean areThereManyLanguages() {
		return tt9 != null && tt9.getSettings().areEnabledLanguagesMoreThanN(1);
	}

	private boolean setVisibility() {
		if (tt9 != null && tt9.getSettings().isMainLayoutClassic()) { // no change for other layouts
			final boolean isVisible = !tt9.isFnPanelVisible();

			getOverlayWrapper();
			overlay.setVisibility(isVisible ? VISIBLE : GONE);

			return isVisible;
		}

		return true;
	}

	private boolean isKeySmall() {
		return Math.max(getTT9Height(), getTT9Width()) < 0.9f;
	}

	private boolean isGermanNumpad() {
		return tt9 != null && tt9.getSettings().isMainLayoutNumpad()
			&& tt9.getLanguage() != null
			&& "de".equals(tt9.getLanguage().getLocale().getLanguage())
			&& !tt9.isInputModeNumeric() && !tt9.isFnPanelVisible();
	}

	@Override
	protected boolean handlePress() {
		switchedModeByHold = false;
		return super.handlePress();
	}

	@Override
	protected void handleHold() {
		preventRepeat();
		if (isGermanNumpad()) {
			switchedModeByHold = true;
			if (new CmdNextInputMode().run(tt9)) vibrate(Vibration.getHoldVibration());
			return;
		}
		if (nextLanguage.isAvailable(tt9) && nextLanguage.run(tt9)) {
			vibrate(Vibration.getHoldVibration());
		}
	}

	@Override
	protected boolean handleRelease() {
		if (switchedModeByHold) {
			switchedModeByHold = false;
			return true;
		}
		if (!notSwiped()) return false;
		if (isGermanNumpad()) {
			// Send the actual key-1 event so the normal punctuation chooser opens.
			int code = Key.numberToCode(tt9.getSettings(), 1);
			if (code < 0) return false;
			tt9.onKeyDown(code, new KeyEvent(KeyEvent.ACTION_DOWN, code));
			tt9.onKeyUp(code, new KeyEvent(KeyEvent.ACTION_UP, code));
			return true;
		}
		return new CmdNextInputMode().run(tt9);
	}

	@Override
	protected void handleEndSwipeX(float position, float delta) {
		new CmdNextKeyboard().run(tt9);
	}

	@Override
	protected void handleEndSwipeY(float position, float delta) {
		new CmdSelectKeyboard().run(tt9);
	}

	@Override
	protected String getAccessibilityText() {
		return isGermanNumpad() ? "Sonderzeichen; halten für T9 oder ABC"
			: getContext().getString(R.string.function_next_mode);
	}

	@Override
	protected String getTitle() {
		if (isGermanNumpad()) return "?123";
		return tt9 != null ? tt9.getInputModeName() : getContext().getString(R.string.virtual_key_input_mode);
	}

	@Override
	protected float getTitleScale() {
		if (isGermanNumpad()) return super.getTitleScale() * .68f;
		return super.getTitleScale() * (isBopomofo() && tt9.isInputModeABC() ? 0.63f : 0.9f);
	}

	@Override
	protected int getCornerIcon(int position) {
		return position == ICON_POSITION_TOP_RIGHT && areThereManyLanguages() ? new CmdNextLanguage().getIcon() : -1;
	}

	@Override
	protected float getCornerElementScale(int position) {
		if (position == ICON_POSITION_TOP_RIGHT) {
			return super.getCornerElementScale(ICON_POSITION_TOP_RIGHT) * 0.75f;
		}
		return super.getCornerElementScale(position);
	}

	@Override
	public boolean isHoldEnabled() {
		return isGermanNumpad() || nextLanguage.isAvailable(tt9);
	}

	@Override
	public void render() {
		if (!setVisibility()) {
			return;
		}

		if (tt9 != null && tt9.isInputModeNumeric()) {
			resetIconCache();
		}

		if (areThereManyLanguages() && isKeySmall()) {
			setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
			setPaddingRelative(0, 0, 0, 10);
		} else if (areThereManyLanguages()) {
			setPaddingRelative(0, 20, 0, 0);
			setGravity(Gravity.CENTER);
		} else {
			setPadding(0, 0, 0, 0);
			setGravity(Gravity.CENTER);
		}

		setEnabled(
			tt9 != null
			&& !tt9.isVoiceInputActive()
			&& !tt9.isInputTypeNumeric()
			&& !tt9.isInputTypePhone()
			&& !tt9.isFnPanelVisible()
		);

		super.render();
	}
}
