package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

/** A single-tap hyphen/minus key for the Swipe-T9 numpad. */
public class SoftKeyMinus extends SoftKey {
	public SoftKeyMinus(Context context) { super(context); }
	public SoftKeyMinus(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyMinus(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

	@Override protected String getTitle() { return "−"; }
	@Override protected String getAccessibilityText() { return "Minus oder Bindestrich"; }

	@Override
	protected boolean handleRelease() {
		// Use U+002D for compatibility with search, filenames and compound words.
		return tt9 != null && tt9.onText("-", false);
	}
}
