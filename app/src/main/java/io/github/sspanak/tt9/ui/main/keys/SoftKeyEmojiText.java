package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

/** Small emoji entry in the existing candidate bar, without adding a keypad row. */
public class SoftKeyEmojiText extends SoftKeyShowEmojis {
    public SoftKeyEmojiText(Context context) { super(context); }
    public SoftKeyEmojiText(Context context, AttributeSet attrs) { super(context, attrs); }
    public SoftKeyEmojiText(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

    // v0.2.14: plain one-colored smiley as text (U+FE0E = text style, not a colored emoji),
    // in the same color as the arrows. The icon of v0.2.13 did not show up.
    @Override protected String getTitle() { return "\u263A\uFE0E"; }
    @Override protected int getCentralIcon() { return -1; }
    @Override protected float getTitleScale() { return super.getTitleScale() * 0.9f; }

    /** v0.2.12: German numpad opens the Swipe-T9 picker with categories and recent emojis. */
    @Override protected boolean handleRelease() {
        if (tt9 != null && tt9.getSettings().isMainLayoutNumpad() && tt9.getLanguage() != null
                && "de".equals(tt9.getLanguage().getLocale().getLanguage())
                && SwipeT9EmojiPopup.show(this, tt9)) {
            return true;
        }
        return super.handleRelease();
    }
}
