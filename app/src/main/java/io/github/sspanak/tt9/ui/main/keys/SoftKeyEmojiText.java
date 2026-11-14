package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

/** Small emoji entry in the existing candidate bar, without adding a keypad row. */
public class SoftKeyEmojiText extends SoftKeyShowEmojis {
    public SoftKeyEmojiText(Context context) { super(context); }
    public SoftKeyEmojiText(Context context, AttributeSet attrs) { super(context, attrs); }
    public SoftKeyEmojiText(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    @Override protected String getTitle() { return "☺"; }
    @Override protected int getCentralIcon() { return -1; }
}
