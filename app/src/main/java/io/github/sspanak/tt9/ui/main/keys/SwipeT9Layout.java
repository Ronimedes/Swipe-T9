package io.github.sspanak.tt9.ui.main.keys;

import androidx.annotation.Nullable;

import io.github.sspanak.tt9.ime.TraditionalT9;

/** Swipe-T9 v0.2.13: helpers for the German numpad layout. */
public final class SwipeT9Layout {
	private SwipeT9Layout() {}

	/**
	 * The bottom row looks the same in letter and number mode:
	 * mode key | ! or 0 | long space (hold: characters) | OK.
	 * Not for phone/PIN fields, the function panel or other languages.
	 */
	public static boolean isUnifiedBottomRow(@Nullable TraditionalT9 tt9) {
		return tt9 != null
			&& tt9.getSettings().isMainLayoutNumpad()
			&& tt9.getSettings().isNumpadShapeLongSpace()
			&& tt9.getLanguage() != null
			&& "de".equals(tt9.getLanguage().getLocale().getLanguage())
			&& !tt9.getLanguage().hasLettersOnAllKeys()
			&& !tt9.isInputTypePhone()
			&& !tt9.isInputTypeNumeric()
			&& !tt9.isFnPanelVisible();
	}
}
