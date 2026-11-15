package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.sspanak.tt9.R;

/**
 * Swipe-T9 v0.2.9: small menu shown when a suggestion is held.
 * "Hide word" and "Add word" as two large buttons over the keypad.
 */
public final class SwipeT9WordMenu {
	@Nullable private static PopupWindow current = null;

	private SwipeT9WordMenu() {}


	public static void dismiss() {
		if (current != null) {
			try {
				current.dismiss();
			} catch (Exception ignored) {}
			current = null;
		}
	}


	/**
	 * @param root any view of the keyboard
	 * @param onHide null hides that button
	 * @param onAdd null hides that button
	 */
	public static boolean show(@NonNull View root, @NonNull String word, int textColor, int background,
	                           @Nullable Runnable onHide, @Nullable Runnable onAdd) {
		dismiss();
		View container = root.getRootView() != null ? root.getRootView().findViewById(R.id.keyboard_container) : null;
		View anchor = container != null ? container : root;
		if (anchor.getWindowToken() == null || anchor.getWidth() == 0) {
			return false;
		}

		Context context = anchor.getContext();
		LinearLayout content = new LinearLayout(context);
		content.setOrientation(LinearLayout.VERTICAL);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(background | 0xFF000000);
		bg.setCornerRadius(dp(context, 10));
		bg.setStroke(dp(context, 1), (textColor & 0x00FFFFFF) | 0x55000000);
		content.setBackground(bg);
		int pad = dp(context, 8);
		content.setPadding(pad, pad, pad, pad);

		TextView title = new TextView(context);
		title.setText("„" + word + "“");
		title.setTextColor(textColor);
		title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
		title.setGravity(Gravity.CENTER);
		content.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 40)));

		final PopupWindow popup = new PopupWindow(content, anchor.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, false);
		popup.setOutsideTouchable(true);
		popup.setBackgroundDrawable(new ColorDrawable(0x00000000));

		int buttons = 1;
		if (onHide != null) {
			content.addView(makeButton(context, "Ausblenden – nie mehr vorschlagen", textColor, () -> { dismiss(); onHide.run(); }), buttonParams(context));
			buttons++;
		}
		if (onAdd != null) {
			content.addView(makeButton(context, "Wort hinzufügen", textColor, () -> { dismiss(); onAdd.run(); }), buttonParams(context));
			buttons++;
		}
		content.addView(makeButton(context, "Abbrechen", textColor, SwipeT9WordMenu::dismiss), buttonParams(context));

		int height = dp(context, 40 + buttons * 54) + 2 * pad;
		int[] location = new int[2];
		anchor.getLocationInWindow(location);
		int y = Math.max(0, location[1] + anchor.getHeight() - height);
		try {
			popup.setHeight(height);
			popup.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], y);
			current = popup;
			return true;
		} catch (Exception e) {
			current = null;
			return false;
		}
	}


	@NonNull
	private static LinearLayout.LayoutParams buttonParams(@NonNull Context context) {
		LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50));
		lp.setMargins(0, dp(context, 4), 0, 0);
		return lp;
	}


	@NonNull
	private static TextView makeButton(@NonNull Context context, @NonNull String text, int textColor, @NonNull Runnable action) {
		TextView button = new TextView(context);
		button.setText(text);
		button.setTextColor(textColor);
		button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
		button.setGravity(Gravity.CENTER);
		GradientDrawable d = new GradientDrawable();
		d.setColor((textColor & 0x00FFFFFF) | 0x18000000);
		d.setCornerRadius(dp(context, 8));
		button.setBackground(d);
		button.setOnClickListener(v -> action.run());
		return button;
	}


	private static int dp(@NonNull Context context, int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
}
