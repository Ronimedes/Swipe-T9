package io.github.sspanak.tt9.ui.main.keys;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;

/**
 * Swipe-T9 v0.2.12: emoji picker with category tabs and a "recently used" tab.
 * It stays open until an emoji is tapped or the user taps outside.
 */
public final class SwipeT9EmojiPopup {
	private static final String PREFS = "swipe_t9_emoji";
	private static final String KEY_RECENT = "recent";
	private static final int MAX_RECENT = 32;
	private static final int COLUMNS = 8;
	private static final int CELL_DP = 44;
	private static final int TAB_DP = 40;
	private static final int ROWS_VISIBLE = 4;

	private static final String[] TAB_TITLES = {"🕘", "😀", "👍", "❤️", "🐶", "🍕", "⚽", "💡"};
	private static final String[][] EMOJIS = {
		{}, // recent, filled at runtime
		{"😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰",
			"😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥳", "😏", "😒",
			"😞", "😔", "😟", "😕", "🙁", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬",
			"🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🤭", "🤫", "🤥", "😶", "😐",
			"😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😵", "🤐", "🥴", "🤢",
			"🤮", "🤧", "😷", "🤒", "🤕", "🤑", "🤠", "😈", "👻", "💀", "🤖", "💩", "😺", "😸", "😹", "😻"},
		{"👍", "👎", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "✋", "🤚", "🖐️",
			"🖖", "👋", "🤝", "🙏", "✍️", "💪", "👏", "🙌", "👐", "🤲", "🤜", "🤛", "✊", "👊", "🫶", "🤌",
			"🙋", "🙆", "🙅", "🤷", "🤦", "🙇", "💁", "🧏", "👀", "👂", "👃", "🧠", "👄", "💋", "🫡", "🫠"},
		{"❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖",
			"💘", "💝", "💟", "♥️", "😍", "🥰", "😘", "💑", "💏", "💐", "🌹", "🌷", "🌸", "💌", "🔥", "✨"},
		{"🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐸", "🐵", "🙈",
			"🙉", "🙊", "🐔", "🐧", "🐦", "🐤", "🦆", "🦅", "🦉", "🐺", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌",
			"🐞", "🐢", "🐍", "🦖", "🐙", "🐠", "🐬", "🐳", "🦈", "🐊", "🐘", "🦒", "🐄", "🐑", "🐈", "🐕"},
		{"🍏", "🍎", "🍐", "🍊", "🍋", "🍌", "🍉", "🍇", "🍓", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅",
			"🥑", "🥦", "🥕", "🌽", "🥔", "🥐", "🥖", "🥨", "🧀", "🥚", "🍳", "🥓", "🍔", "🍟", "🍕", "🌭",
			"🥪", "🌮", "🍝", "🍜", "🍣", "🍦", "🍩", "🍪", "🎂", "🍰", "🍫", "🍿", "☕", "🍺", "🍷", "🥂"},
		{"⚽", "🏀", "🏈", "⚾", "🎾", "🏐", "🏓", "🏸", "🥅", "⛳", "🎣", "🚴", "🏃", "🏊", "⛷️", "🏆",
			"🥇", "🎮", "🎲", "🎯", "🎳", "🎸", "🎹", "🎤", "🎧", "🎬", "🎨", "🚗", "🏎️", "🚲", "✈️", "🚀",
			"🏠", "🏫", "🏖️", "⛰️", "🌍", "☀️", "🌤️", "🌧️", "⛈️", "❄️", "🌈", "🌙", "⭐", "🎉", "🎁", "🎄"},
		{"💡", "📱", "💻", "⌨️", "🖥️", "🖨️", "📷", "📺", "⏰", "📅", "📌", "📎", "✏️", "📝", "📚", "📖",
			"💼", "📁", "🔒", "🔑", "🔧", "🔨", "⚙️", "🧰", "💰", "💶", "💳", "📦", "✉️", "📞", "🔔", "🔕",
			"✅", "❌", "❓", "❗", "⚠️", "🚫", "💯", "🆗", "🆕", "🔝", "➕", "➖", "➗", "✖️", "♻️", "💤"},
	};

	@Nullable private static PopupWindow current = null;

	private SwipeT9EmojiPopup() {}


	public static void dismiss() {
		if (current != null) {
			try {
				current.dismiss();
			} catch (Exception ignored) {}
			current = null;
		}
	}


	@NonNull
	private static List<String> getRecent(@NonNull Context context) {
		String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_RECENT, "");
		ArrayList<String> list = new ArrayList<>();
		if (raw != null && !raw.isEmpty()) list.addAll(Arrays.asList(raw.split("\t")));
		return list;
	}


	private static void addRecent(@NonNull Context context, @NonNull String emoji) {
		List<String> recent = getRecent(context);
		recent.remove(emoji);
		recent.add(0, emoji);
		while (recent.size() > MAX_RECENT) recent.remove(recent.size() - 1);
		SharedPreferences.Editor editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
		editor.putString(KEY_RECENT, android.text.TextUtils.join("\t", recent)).apply();
	}


	public static boolean show(@NonNull View key, @Nullable TraditionalT9 tt9) {
		if (tt9 == null) return false;
		dismiss();

		View root = key.getRootView();
		View container = root != null ? root.findViewById(R.id.keyboard_container) : null;
		View anchor = container != null ? container : key;
		if (anchor.getWindowToken() == null || anchor.getWidth() == 0) return false;

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

		HorizontalScrollView tabScroll = new HorizontalScrollView(context);
		tabScroll.setHorizontalScrollBarEnabled(false);
		LinearLayout tabs = new LinearLayout(context);
		tabs.setOrientation(LinearLayout.HORIZONTAL);
		tabScroll.addView(tabs);
		content.addView(tabScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, TAB_DP)));

		ScrollView gridScroll = new ScrollView(context);
		LinearLayout grid = new LinearLayout(context);
		grid.setOrientation(LinearLayout.VERTICAL);
		gridScroll.addView(grid);
		content.addView(gridScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, CELL_DP * ROWS_VISIBLE)));

		final TextView[] tabViews = new TextView[TAB_TITLES.length];
		for (int t = 0; t < TAB_TITLES.length; t++) {
			final int tab = t;
			TextView tv = new TextView(context);
			tv.setText(TAB_TITLES[t]);
			tv.setGravity(Gravity.CENTER);
			tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
			tv.setTextColor(textColor);
			tv.setOnClickListener(v -> {
				fill(grid, tab == 0 ? getRecent(context) : Arrays.asList(EMOJIS[tab]), textColor, tt9);
				gridScroll.scrollTo(0, 0);
				for (int i = 0; i < tabViews.length; i++) mark(tabViews[i], i == tab, textColor);
			});
			tabViews[t] = tv;
			tabs.addView(tv, new LinearLayout.LayoutParams(dp(context, 48), ViewGroup.LayoutParams.MATCH_PARENT));
		}

		List<String> recent = getRecent(context);
		int startTab = recent.isEmpty() ? 1 : 0;
		fill(grid, startTab == 0 ? recent : Arrays.asList(EMOJIS[startTab]), textColor, tt9);
		for (int i = 0; i < tabViews.length; i++) mark(tabViews[i], i == startTab, textColor);

		int height = dp(context, TAB_DP + CELL_DP * ROWS_VISIBLE) + 2 * pad;
		final PopupWindow popup = new PopupWindow(content, anchor.getWidth(), height, false);
		popup.setOutsideTouchable(true);
		popup.setBackgroundDrawable(new ColorDrawable(0x00000000));

		int[] location = new int[2];
		anchor.getLocationInWindow(location);
		int y = Math.max(0, location[1] + anchor.getHeight() - height);
		try {
			popup.showAtLocation(anchor, Gravity.NO_GRAVITY, location[0], y);
			current = popup;
			return true;
		} catch (Exception e) {
			current = null;
			return false;
		}
	}


	private static void fill(@NonNull LinearLayout grid, @NonNull List<String> emojis, int textColor, @NonNull TraditionalT9 tt9) {
		Context context = grid.getContext();
		grid.removeAllViews();
		if (emojis.isEmpty()) {
			TextView hint = new TextView(context);
			hint.setText("Noch keine – benutzte Smileys erscheinen hier.");
			hint.setTextColor(textColor);
			hint.setGravity(Gravity.CENTER);
			grid.addView(hint, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, CELL_DP * 2)));
			return;
		}
		LinearLayout row = null;
		for (int i = 0; i < emojis.size(); i++) {
			if (i % COLUMNS == 0) {
				row = new LinearLayout(context);
				row.setOrientation(LinearLayout.HORIZONTAL);
				grid.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(context, CELL_DP)));
			}
			final String emoji = emojis.get(i);
			TextView cell = new TextView(context);
			cell.setText(emoji);
			cell.setGravity(Gravity.CENTER);
			cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
			cell.setOnClickListener(v -> {
				dismiss();
				addRecent(context, emoji);
				tt9.onText(emoji, false);
			});
			row.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		}
		// keep the last row's cells the same width
		int rest = (COLUMNS - emojis.size() % COLUMNS) % COLUMNS;
		for (int i = 0; i < rest && row != null; i++) {
			row.addView(new View(context), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
		}
	}


	private static void mark(@NonNull TextView tab, boolean active, int textColor) {
		tab.setTypeface(null, active ? Typeface.BOLD : Typeface.NORMAL);
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
