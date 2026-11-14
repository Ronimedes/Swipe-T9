package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.Toast;
import io.github.sspanak.tt9.ui.main.swipe.SwipeT9TouchLayout;

/** Commit a T9 word without adding a space, e.g. Haus + tuer. */
public class SoftKeyJoin extends SoftKey {
	public SoftKeyJoin(Context context) { super(context); }
	public SoftKeyJoin(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyJoin(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

	@Override protected String getTitle() { return "→"; }
	@Override protected String getAccessibilityText() { return "Wort ohne Leerzeichen bestaetigen"; }

	private boolean held = false;

	@Override protected boolean handlePress() {
		held = false;
		return super.handlePress();
	}

	@Override protected void handleHold() {
		preventRepeat();
		held = true;
		boolean enabled = SwipeT9TouchLayout.toggleEnabled(getContext());
		Toast.makeText(getContext(), enabled ? "Swipe-T9: EIN" : "Swipe-T9: AUS", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected boolean handleRelease() {
		if (held) { held = false; return true; }
		return tt9 != null && tt9.onJoinWithoutSpace();
	}
}
