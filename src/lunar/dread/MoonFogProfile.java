package lunar.dread;

/** Pure fog curves shared by every moon phase; the actual renderer only fogs terrain. */
public final class MoonFogProfile {
    public record Range(float near, float far) {}

    // Peak surface visibility, in blocks. Peaceful nights retain longer sight lines.
    private static final float[] PEAK_FAR = {
        80, 104, 72, 56, 54, 64, 7, 18, 28, 20, 30, 24, 32, 22, 20, 24
    };
    private static final float[] COLOR_WEIGHT = {
        .25f, .24f, .28f, .34f, .34f, .32f, .86f, .58f, .48f, .58f, .48f, .46f, .42f, .52f, .5f, .55f
    };

    private MoonFogProfile() {}

    private static float ease(float value) {
        float x = Math.clamp(value, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Starts clear at dusk, peaks around midnight, then clears completely at dawn. */
    public static float intensity(long dayTime) {
        long clock = Math.floorMod(dayTime, 24000);
        if (clock < 13000) return 0;
        if (clock < 17500) return ease((clock - 13000) / 4500f);
        if (clock <= 19500) return 1;
        return ease((24000 - clock) / 4500f);
    }

    public static float colorWeight(int kind, float intensity) {
        return COLOR_WEIGHT[Math.clamp(kind, 0, COLOR_WEIGHT.length - 1)] * Math.clamp(intensity, 0, 1);
    }

    public static Range range(int kind, float depth, float intensity, float originalNear, float originalFar,
                              float bloodPeakFar, float otherScale) {
        int index = Math.clamp(kind, 0, PEAK_FAR.length - 1);
        float d = Math.clamp(depth, 0, 1), strength = Math.clamp(intensity, 0, 1);
        float surfaceFar = index == MoonCycle.BLOOD ? bloodPeakFar : PEAK_FAR[index] * otherScale;
        float peakFar = Math.max(2.5f, surfaceFar * (1 - d * (index == MoonCycle.BLOOD ? .52f : .38f)));
        float peakNear = Math.min(peakFar - .8f, index == MoonCycle.BLOOD ? .5f : Math.max(1.2f, peakFar * .12f));
        float far = originalFar + (Math.min(originalFar, peakFar) - originalFar) * strength;
        float near = originalNear + (Math.min(originalNear, peakNear) - originalNear) * strength;
        return new Range(Math.min(near, far - .25f), far);
    }
}
