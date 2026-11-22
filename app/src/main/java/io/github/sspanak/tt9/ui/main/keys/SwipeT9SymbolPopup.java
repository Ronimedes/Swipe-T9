package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.graphics.Typeface;
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
import io.github.sspanak.tt9.ime.TraditionalT9;

/**
 * Swipe-T9: a character picker that stays open until a character is tapped
 * or the user taps outside of it. Two tabs: punctuation and other symbols.
 */
public final class SwipeT9SymbolPopup {
	static final String[] PUNCTUATION = {
		"?", ",", ";", ":", "'", "\"", "„", "“",
		"‚", "‘", "-", "–", "…", "(", ")", "¡",
	};

	/** v0.2.13: first tab in number mode. */
	static final String[] NUMERIC = {
		",", ".", "-", "+", "/", ":", "€", "%",
		"(", ")", "=", "*", "#", "@", "&", "°",
	};

	static final String[] SYMBOLS = {
		"@", "#", "€", "$", "%", "&", "/", "\\",
		"+", "=", "*", "_", "§", "°", "^", "~",
		"<", ">", "[", "]", "{", "}", "|", "£",
	};

	private static final int COLUMNS = 8;
	private static final int CELL_HEIGHT_DP = 46;
	private static final int TAB_HEIGHT_DP = 38;

	@Nullable private static PopupWindow current = null;

	private SwipeT9SymbolPopup() {}


	static boolean isShowing() {
		return current != null && current.isShowing();
	}


	public static void dismiss() {
		if (current != null) {
			try {
				current.dismiss();
			} catch (Exception ignored) {}
			current = null;
		}
	}


	static void show(@NonNull View key, @Nullable TraditionalT9 tt9, boolean numeric) {
		if (tt9 == null) {
			return;
		}
		dismiss();

		View root = key.getRootView();
		View container = root != null ? root.findViewById(R.id.keyboard_container) : null;
		View anchor = container != null ? container : key;
		if (anchor.getWindowToken() == null) {
			return;
		}

		final Context context = key.getContext();
		final int textColor = tt9.getSettings().getKeyboardTextColor();
		final int background = tt9.getSettings().getKeyboardBackground() | 0xFF000000;

		LinearLayout content = new LinearLayout(context);
		content.setOrientation(LinearLayout.VERTICAL);
		GradientDrawable bg = new GradientDrawable();
		bg.setColor(background);
		bg.setCornerRadius(dp(context, 10));
		bg.setStroke(dp(context, 1), (textColor & 0x00FFFFFF) | 0x55000000);
		content.setBackground(bg);
		int pad = dp(context, 4);
		content.setPadding(pad, pad, pad, pad);

		LinearLayout tabs = new LinearLayout(context);
		tabs.setOrientation(LinearLayout.HORIZONTAL);
		final String[] firstTab = numeric ? NUMERIC : PUNCTUATION;
		TextView tabPunctuation = makeTab(context, numeric ? "Zahlenzeichen  , . €" : "Satzzeichen  ! ? ,", textColor);
		TextView tabSymbols = makeTab(context, "Sonderzeichen  @ # €", textColor);
		tabs.addView(tabPunctuation, new LinearLayout.LayoutParams(0, dp(context, TAB_HEIGHT_DP), 1));
		tabs.addView(tabSymbols, new LinearLayout.LayoutParams(0, dp(context, TAB_HEIGHT_DP), 1));
		content.addView(tabs);

		LinearLayout grid = new LinearLayout(context);
		grid.setOrientation(LinearLayout.VERTICAL);
		content.addView(grid);

		final PopupWindow popup = new PopupWindow(content, anchor.getWidth(), ViewGroup.LayoutParams.WRAP_CONTENT, false);
		popup.setOutsideTouchable(true);
		popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
		popup.setClippingEnabled(true);

		Runnable showPunctuation = () -> {
			fillGrid(grid, firstTab, textColor, tt9);
			markTab(tabPunctuation, true, textColor);
			markTab(tabSymbols, false, textColor);
		};
		Runnable showSymbols = () -> {
			fillGrid(grid, SYMBOLS, textColor, tt9);
			markTab(tabPunctuation, false, textColor);
			markTab(tabSymbols, true, textColor);
		};
		tabPunctuation.setOnClickListener(v -> showPunctuation.run());
		tabSymbols.setOnClickListener(v -> showSymbols.run());

		showPunctuation.run();

		// Place the picker over the lower part of the keyboard, so it never leaves the IME window.
		int rowsMax = (Math.max(PUNCTUATION.length, SYMBOLS.length) + COLUMNS - 1) / COLUMNS;
		int popupHeight = dp(context, TAB_HEIGHT_DP + rowsMax * CELL_HEIGHT_DP) + 2 * pad;
		int[] location = new int[2];
		anchor.getLocationInWindow(location);
		int y = Math.max(0, location[1] + anchor.getHeight() - popupHeight);

		try {
			popup.setHeight(popupHeight);
			popup.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], y);
			current = popup;
		} catch (Exception e) {
			current = null;
		}
	}


	private static void fillGrid(@NonNull LinearLayout grid, @NonNull String[] chars, int textColor, @NonNull TraditionalT9 tt9) {
		Context context = grid.getContext();
		grid.removeAllViews();
		LinearLayout row = null;
		for (int i = 0; i < chars.length; i++) {
			if (i % COLUMNS == 0) {
				row = new LinearLayout(context);
				row.setOrientation(LinearLayout.HORIZONTAL);
				grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, CELL_HEIGHT_DP)));
			}
			final String ch = chars[i];
			TextView cell = new TextView(context);
			cell.setText(ch);
			cell.setGravity(Gravity.CENTER);
			cell.setTextColor(textColor);
			cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
			cell.setContentDescription(ch);
			cell.setOnClickListener(v -> {
				dismiss();
				tt9.onText(ch, false);
			});
			LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
			int m = dp(context, 2);
			lp.setMargins(m, m, m, m);
			GradientDrawable cellBg = new GradientDrawable();
			cellBg.setColor((textColor & 0x00FFFFFF) | 0x14000000);
			cellBg.setCornerRadius(dp(context, 6));
			cell.setBackground(cellBg);
			row.addView(cell, lp);
		}
	}


	@NonNull
	private static TextView makeTab(@NonNull Context context, @NonNull String text, int textColor) {
		TextView tab = new TextView(context);
		tab.setText(text);
		tab.setGravity(Gravity.CENTER);
		tab.setTextColor(textColor);
		tab.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
		tab.setSingleLine(true);
		return tab;
	}


	private static void markTab(@NonNull TextView tab, boolean active, int textColor) {
		tab.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
		tab.setAlpha(active ? 1f : 0.55f);
		if (active) {
			GradientDrawable d = new GradientDrawable();
			d.setColor((textColor & 0x00FFFFFF) | 0x22000000);
			d.setCornerRadius(dp(tab.getContext(), 6));
			tab.setBackground(d);
		} else {
			tab.setBackground(null);
		}
	}


	private static int dp(@NonNull Context context, int value) {
		return Math.round(value * context.getResources().getDisplayMetrics().density);
	}
}
