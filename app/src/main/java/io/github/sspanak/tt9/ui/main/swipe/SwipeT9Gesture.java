package io.github.sspanak.tt9.ui.main.swipe;

/**
 * Small, allocation-light gesture decoder. Receives positions in screen pixels.
 * A complete loop within one key counts as ONE deliberate repeated key.
 * No dictionary logic lives here: the resulting digits use the normal T9 engine.
 */
public final class SwipeT9Gesture {
    private final StringBuilder sequence = new StringBuilder();
    private final float[] sampleX = new float[200];
    private final float[] sampleY = new float[200];
    private int sampleCount;
    private int digit = -1;
    private boolean loopRecognized;
    private float traveled;
    private float keySize;
    private float lowX, highX, lowY, highY;
    private int horizontalDirection, horizontalTurns;
    private float horizontalLeg, zigzagTravel;

    public void reset() {
        sequence.setLength(0);
        digit = -1;
        sampleCount = 0;
        loopRecognized = false;
        traveled = 0;
        horizontalDirection = horizontalTurns = 0;
        horizontalLeg = zigzagTravel = 0;
    }

    public String getDigits() { return sequence.toString(); }

    public void add(int nextDigit, float x, float y, float keyWidth, float keyHeight) {
        if (nextDigit < 2 || nextDigit > 9 || sequence.length() >= 32) return;
        if (digit != nextDigit) {
            digit = nextDigit;
            sequence.append((char) ('0' + digit));
            sampleCount = 0;
            traveled = 0;
            loopRecognized = false;
            horizontalDirection = horizontalTurns = 0;
            horizontalLeg = zigzagTravel = 0;
            keySize = Math.max(1f, Math.min(keyWidth, keyHeight));
            lowX = highX = x;
            lowY = highY = y;
        }
        if (sampleCount > 0) {
            float dx = x - sampleX[sampleCount - 1];
            float dy = y - sampleY[sampleCount - 1];
            traveled += (float) Math.hypot(dx, dy);
            if (!loopRecognized && Math.abs(dx) > .03f * keySize) {
                int direction = dx > 0 ? 1 : -1;
                if (horizontalDirection != 0 && direction != horizontalDirection) {
                    if (horizontalLeg >= .25f * keySize) {
                        horizontalTurns++;
                        zigzagTravel += horizontalLeg;
                        horizontalDirection = direction;
                        horizontalLeg = Math.abs(dx);
                    }
                } else {
                    horizontalDirection = direction;
                    horizontalLeg += Math.abs(dx);
                }
            }
        }
        lowX = Math.min(lowX, x); highX = Math.max(highX, x);
        lowY = Math.min(lowY, y); highY = Math.max(highY, y);
        if (sampleCount < sampleX.length) {
            sampleX[sampleCount] = x;
            sampleY[sampleCount] = y;
            sampleCount++;
        }
        // Deliberate left-right-left is a second way to repeat a key. Tiny finger jitter
        // does not pass the minimum travel/turn thresholds.
        if (!loopRecognized && horizontalTurns >= 2 && zigzagTravel + horizontalLeg >= 1.1f * keySize
                && highY - lowY < .25f * keySize && highX - lowX >= .32f * keySize) {
            sequence.append((char) ('0' + digit));
            loopRecognized = true;
        }
        if (!loopRecognized && sampleCount >= 7 && traveled >= 1.10f * keySize
            && highX - lowX >= .25f * keySize && highY - lowY >= .25f * keySize) {
            // Look for a real return to an earlier point, with enough perimeter between them.
            for (int i = 0; i < sampleCount - 5; i++) {
                float dx = x - sampleX[i], dy = y - sampleY[i];
                if (Math.hypot(dx, dy) <= .20f * keySize) {
                    float path = 0;
                    for (int j = i + 1; j < sampleCount; j++) {
                        path += Math.hypot(sampleX[j] - sampleX[j-1], sampleY[j] - sampleY[j-1]);
                    }
                    if (path >= .85f * keySize) {
                        sequence.append((char) ('0' + digit));
                        loopRecognized = true;
                        break;
                    }
                }
            }
        }
    }
}
