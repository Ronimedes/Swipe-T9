package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.util.AttributeSet;
import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.commands.CmdMoveCursor;

/** One-tap cursor movement, repeatedly move left when held. */
public final class SoftKeyCursorLeft extends SoftKey {
    private boolean movedOnHold = false;
    private final CmdMoveCursor cursor = new CmdMoveCursor();

    public SoftKeyCursorLeft(Context c) { super(c); }
    public SoftKeyCursorLeft(Context c, AttributeSet a) { super(c, a); }
    public SoftKeyCursorLeft(Context c, AttributeSet a, int style) { super(c, a, style); }

    @Override protected String getTitle() { return "←"; }
    @Override protected String getAccessibilityText() { return "Cursor nach links"; }
    @Override protected boolean handlePress() { movedOnHold = false; return super.handlePress(); }
    @Override protected void handleHold() {
        movedOnHold = true;
        cursor.run(tt9, CmdMoveCursor.CURSOR_MOVE_LEFT);
    }
    @Override protected boolean handleRelease() {
        return movedOnHold || cursor.run(tt9, CmdMoveCursor.CURSOR_MOVE_LEFT);
    }
}
