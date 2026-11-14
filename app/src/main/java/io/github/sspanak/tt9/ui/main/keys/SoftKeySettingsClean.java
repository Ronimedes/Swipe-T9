package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

/** Settings shortcut without the four legacy keyboard-resize arrows. */
public class SoftKeySettingsClean extends SoftKeySettings {
    public SoftKeySettingsClean(Context context) { super(context); }
    public SoftKeySettingsClean(Context context, AttributeSet attrs) { super(context, attrs); }
    public SoftKeySettingsClean(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override protected boolean isSwipeable() { return false; }
    @Override protected String getTopText() { return ""; }
    @Override protected String getRightText() { return ""; }
    @Override protected String getBottomText() { return ""; }
    @Override protected String getLeftText() { return ""; }
}
