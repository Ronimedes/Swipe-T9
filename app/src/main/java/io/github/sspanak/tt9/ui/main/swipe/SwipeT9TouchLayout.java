package io.github.sspanak.tt9.ui.main.swipe;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.preference.PreferenceManager;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.LinearLayout;

import io.github.sspanak.tt9.R;
import io.github.sspanak.tt9.ime.TraditionalT9;

/**
 * Experimental opt-in swipe mode for the large touchscreen numpad.
 * Taps and ordinary long presses continue to go to the original soft keys.
 * Only a deliberate drag takes over the touch stream, cancelling the original key.
 */
public final class SwipeT9TouchLayout extends LinearLayout {
    public static final String ENABLED = "swipe_t9_enabled";
    private final int[] digitIds = {R.id.soft_key_2, R.id.soft_key_3, R.id.soft_key_4, R.id.soft_key_5,
            R.id.soft_key_6, R.id.soft_key_7, R.id.soft_key_8, R.id.soft_key_9};
    private final Rect hitRect = new Rect();
    private final SwipeT9Gesture gesture = new SwipeT9Gesture();
    private TraditionalT9 tt9;
    private boolean tracking, captured, normalKeyStarted;
    // Do not send DOWN to the original digit key until we know this is a tap/hold.
    // Otherwise the original key can emit a long-press digit before the swipe wins.
    private MotionEvent pendingDown;
    private final Handler pendingKeyHandler = new Handler(Looper.getMainLooper());
    private final Runnable startNormalHold = this::forwardPendingDown;
    private float startX, startY, lastX, lastY, pathLength, firstKeyWidth;
    private int startDigit;
    private final Path visibleTrail = new Path();
    private final Paint trailGlow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailInk = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] trace = new float[512];
    private int traceCount;
    private boolean fadingTrail;
    private long fadeStart;

    private void recordPoint(float x, float y) {
        if (traceCount < trace.length / 2) {
            trace[2*traceCount] = x / Math.max(1, getWidth());
            trace[2*traceCount+1] = y / Math.max(1, getHeight());
            traceCount++;
        }
        if (traceCount == 1) visibleTrail.moveTo(x, y);
        else visibleTrail.lineTo(x, y);
        invalidate();
    }

    private float[] finishedTrace() {
        float[] copy = new float[traceCount * 2];
        System.arraycopy(trace, 0, copy, 0, copy.length);
        return copy;
    }

    private boolean isTrailEnabled() {
        return PreferenceManager.getDefaultSharedPreferences(getContext())
                .getBoolean("swipe_t9_show_trail", true);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (!isTrailEnabled() || (traceCount < 2 && !fadingTrail)) return;
        float opacity = fadingTrail ? Math.max(0f, 1f - (SystemClock.uptimeMillis() - fadeStart) / 450f) : 1f;
        if (opacity == 0f) {
            fadingTrail = false;
            visibleTrail.reset();
            return;
        }
        float dp = getResources().getDisplayMetrics().density;
        trailGlow.setColor(Color.rgb(0, 190, 220));
        trailGlow.setStyle(Paint.Style.STROKE);
        trailGlow.setStrokeWidth(12f * dp);
        trailGlow.setStrokeCap(Paint.Cap.ROUND);
        trailGlow.setStrokeJoin(Paint.Join.ROUND);
        trailGlow.setAlpha((int) (55 * opacity));
        trailInk.setColor(Color.rgb(0, 177, 210));
        trailInk.setStyle(Paint.Style.STROKE);
        trailInk.setStrokeWidth(4f * dp);
        trailInk.setStrokeCap(Paint.Cap.ROUND);
        trailInk.setStrokeJoin(Paint.Join.ROUND);
        trailInk.setAlpha((int) (225 * opacity));
        canvas.drawPath(visibleTrail, trailGlow);
        canvas.drawPath(visibleTrail, trailInk);
        if (fadingTrail) postInvalidateDelayed(16);
    }

    public SwipeT9TouchLayout(Context c) { super(c); }
    public SwipeT9TouchLayout(Context c, AttributeSet a) { super(c, a); }
    public SwipeT9TouchLayout(Context c, AttributeSet a, int s) { super(c, a, s); }

    public void setTT9(TraditionalT9 handler) { this.tt9 = handler; }

    public static boolean toggleEnabled(Context context) {
        boolean enabled = !isEnabled(context);
        PreferenceManager.getDefaultSharedPreferences(context).edit().putBoolean(ENABLED, enabled).apply();
        return enabled;
    }

    public static boolean isEnabled(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(ENABLED, false);
    }

    private int digitAt(float x, float y) {
        // MotionEvent.getX/Y() and this rectangle are BOTH local to the numpad.
        // getGlobalVisibleRect() + getRawX/Y() mixes coordinate spaces on IME windows,
        // so real swipes can be misidentified as an ordinary tap on the first key.
        for (int i = 0; i < digitIds.length; i++) {
            View key = findViewById(digitIds[i]);
            if (key == null || !key.isShown() || key.getWidth() <= 0 || key.getHeight() <= 0) continue;
            key.getDrawingRect(hitRect);
            offsetDescendantRectToMyCoords(key, hitRect);
            if (hitRect.contains((int) x, (int) y)) return digitIdsToNumber(i);
        }
        return -1;
    }

    private int digitIdsToNumber(int i) {
        return new int[] {2,3,4,5,6,7,8,9}[i];
    }

    private void sample(float x, float y) {
        int digit = digitAt(x,y);
        if (digit < 2) return;
        View key = findViewById(digitIds[digit - 2]);
        if (key != null) gesture.add(digit, x, y, key.getWidth(), key.getHeight());
    }

    private void traceTo(float x, float y) {
        float dx = x - lastX, dy = y - lastY;
        float distance = (float) Math.hypot(dx, dy);
        pathLength += distance;
        // Fast swipes may skip MOVE events for intermediate keys. Sample the
        // connecting line so 4 -> 2 -> 5 -> 6 is not reduced to just 4 and 6.
        int steps = Math.min(96, Math.max(1,
                (int) Math.ceil(distance / Math.max(8f, firstKeyWidth * .18f))));
        for (int i = 1; i <= steps; i++) {
            float fraction = (float) i / steps;
            sample(lastX + dx * fraction, lastY + dy * fraction);
        }
        lastX = x;
        lastY = y;
        recordPoint(x, y);
    }

    private void forwardPendingDown() {
        if (!tracking || captured || pendingDown == null) return;
        normalKeyStarted = true;
        super.dispatchTouchEvent(pendingDown);
        pendingDown.recycle();
        pendingDown = null;
    }

    private void clear() {
        pendingKeyHandler.removeCallbacks(startNormalHold);
        if (pendingDown != null) {
            pendingDown.recycle();
            pendingDown = null;
        }
        tracking = captured = normalKeyStarted = false;
        pathLength = 0;
        gesture.reset();
    }

    @Override
    protected void onDetachedFromWindow() {
        clear();
        fadingTrail = false;
        visibleTrail.reset();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        // One finger only. No interception with TalkBack, special input fields or ABC/numeric mode.
        if (action == MotionEvent.ACTION_DOWN) {
            clear();
            visibleTrail.reset();
            traceCount = 0;
            fadingTrail = false;
            if (tt9 == null || !isEnabled(getContext()) || !tt9.isSwipeT9Allowed()) {
                return super.dispatchTouchEvent(ev);
            }
            startDigit = digitAt(ev.getX(), ev.getY());
            if (startDigit < 2) return super.dispatchTouchEvent(ev);
            tracking = true;
            startX = lastX = ev.getX();
            startY = lastY = ev.getY();
            View first = findViewById(digitIds[startDigit - 2]);
            firstKeyWidth = first == null ? 70 : Math.max(20, first.getWidth());
            sample(startX, startY);
            recordPoint(startX, startY);
            // Wait for the gesture decision. Ordinary taps are replayed on UP;
            // a stationary hold starts the original key after a short grace period.
            // This preserves the long-press digit without inserting it into swipes.
            pendingDown = MotionEvent.obtain(ev);
            pendingKeyHandler.postDelayed(startNormalHold,
                    Math.max(300, ViewConfiguration.getLongPressTimeout() - 150));
            return true;
        }
        if (!tracking) return super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_POINTER_DOWN || action == MotionEvent.ACTION_CANCEL) {
            if (normalKeyStarted) {
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            clear();
            traceCount = 0;
            fadingTrail = false;
            visibleTrail.reset();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            for (int i = 0; i < ev.getHistorySize(); i++) {
                traceTo(ev.getHistoricalX(i), ev.getHistoricalY(i));
            }
            traceTo(ev.getX(), ev.getY());
            if (!captured && gesture.getDigits().length() >= 2 && pathLength >=
                    Math.max(ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2, firstKeyWidth * .35f)) {
                // Cancel ONLY if the normal key stream actually started. Until then,
                // no digit has been dispatched, so there is nothing to undo.
                pendingKeyHandler.removeCallbacks(startNormalHold);
                if (normalKeyStarted) {
                    MotionEvent cancel = MotionEvent.obtain(ev);
                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancel);
                    cancel.recycle();
                }
                captured = true;
            }
            if (captured) {
                if (action == MotionEvent.ACTION_UP) {
                    String digits = gesture.getDigits();
                    String loops = gesture.getLoopDetails();
                    java.util.ArrayList<String> candidates = SwipeT9Decoder.decode(gesture);
                    int endingAt = digitAt(ev.getX(), ev.getY());
                    float[] drawing = finishedTrace();
                    long logId = tt9 != null && tt9.isSwipeT9DiagnosticAllowed()
                            ? SwipeT9History.add(getContext(), digits, drawing, loops, startDigit, endingAt, true,
                                    tt9.getLanguage() == null ? -1 : tt9.getLanguage().getId()) : -1;
                    fadingTrail = true;
                    fadeStart = SystemClock.uptimeMillis();
                    clear();
                    invalidate();
                    if (digits.length() >= 2 && tt9 != null && tt9.isSwipeT9Allowed()) {
                        tt9.onSwipeT9Candidates(digits, candidates, drawing, logId);
                    }
                }
                return true;
            }
            // A real tap must still reach the original T9 key. Replaying the
            // stored DOWN now makes it impossible for an unfinished swipe to
            // produce a leading number through a key long-press callback.
            if (action == MotionEvent.ACTION_UP) {
                // Record failed drags as well (e.g. "Liebe" becoming a single "d").
                float minDistance = Math.max(ViewConfiguration.get(getContext()).getScaledTouchSlop() * 2,
                        firstKeyWidth * .35f);
                if (pathLength >= minDistance && tt9 != null && tt9.isSwipeT9DiagnosticAllowed()) {
                    SwipeT9History.add(getContext(), gesture.getDigits(), finishedTrace(),
                            gesture.getLoopDetails(), startDigit, digitAt(ev.getX(), ev.getY()), false,
                            tt9.getLanguage() == null ? -1 : tt9.getLanguage().getId());
                }
            }
            if (action == MotionEvent.ACTION_UP && !normalKeyStarted) forwardPendingDown();
            boolean result = normalKeyStarted ? super.dispatchTouchEvent(ev) : true;
            if (action == MotionEvent.ACTION_UP) {
                clear();
                traceCount = 0;
                visibleTrail.reset();
                invalidate();
            }
            return result;
        }
        return super.dispatchTouchEvent(ev);
    }
}
