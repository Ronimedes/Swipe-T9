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

    /**
     * v0.2.15: align with the arrow keys: same insets (so the background starts at the same
     * height) and the smiley lifted a little.
     */
    @Override
    public void render() {
        super.render();
        android.view.View parent = getParent() instanceof android.view.View ? (android.view.View) getParent() : null;
        android.view.View arrow = parent != null ? parent.findViewById(io.github.sspanak.tt9.R.id.soft_key_right_arrow) : null;
        if (arrow instanceof com.google.android.material.button.MaterialButton) {
            com.google.android.material.button.MaterialButton a = (com.google.android.material.button.MaterialButton) arrow;
            if (getInsetTop() != a.getInsetTop()) setInsetTop(a.getInsetTop());
            if (getInsetBottom() != a.getInsetBottom()) setInsetBottom(a.getInsetBottom());
        }
        int lift = Math.round(5 * getResources().getDisplayMetrics().density);
        if (getPaddingBottom() != lift || getPaddingTop() != 0) {
            setPadding(getPaddingLeft(), 0, getPaddingRight(), lift);
        }
    }
}
