package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

/** Commit a T9 word without adding a space, e.g. Haus + tuer. */
public class SoftKeyJoin extends SoftKey {
	public SoftKeyJoin(Context context) { super(context); }
	public SoftKeyJoin(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyJoin(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

	@Override protected String getTitle() { return "→"; }
	@Override protected String getAccessibilityText() { return "Wort ohne Leerzeichen bestaetigen"; }

	@Override
	protected boolean handleRelease() {
		return tt9 != null && tt9.onJoinWithoutSpace();
	}
}
