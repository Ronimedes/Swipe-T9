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

    /** v0.2.12: smaller glyph so the key does not overflow the bar. */
    @Override protected float getTitleScale() { return super.getTitleScale() * 0.72f; }

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
