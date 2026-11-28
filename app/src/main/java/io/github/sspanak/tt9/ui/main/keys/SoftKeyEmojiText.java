package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;

import io.github.sspanak.tt9.commands.CmdShowEmojis;

/**
 * v0.2.16: smiley key in the suggestion bar. Built on the same base class as the arrow keys,
 * so background, height and corners are identical; the smiley is plain centred text.
 */
public class SoftKeyEmojiText extends BaseSoftKeyCustomizable {
	private final CmdShowEmojis showEmojis = new CmdShowEmojis();

	public SoftKeyEmojiText(Context context) { super(context); }
	public SoftKeyEmojiText(Context context, AttributeSet attrs) { super(context, attrs); }
	public SoftKeyEmojiText(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); }

	@Override protected String getTitle() { return "☺︎"; }

	@Override protected String getAccessibilityText() { return showEmojis.getName(tt9); }

	@Override protected boolean handleRelease() {
		if (tt9 != null && tt9.getSettings().isMainLayoutNumpad() && tt9.getLanguage() != null
				&& "de".equals(tt9.getLanguage().getLocale().getLanguage())
				&& SwipeT9EmojiPopup.show(this, tt9)) {
			return true;
		}
		return showEmojis.run(tt9);
	}
}
