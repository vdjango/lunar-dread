package lunar.dread;

/** Five-day weeks are the single source of progression for the next pack version. */
public final class DifficultyRules {
    private DifficultyRules() {}

    private static int curveDay(int day) { return EventCalendar.probability()?LunarDreadConfig.RANDOM_DIFFICULTY_DAY.get():Math.max(1,day); }
    public static int week(int day) { return Math.clamp((curveDay(day) + 4) / 5, 1, 20); }
    public static int withinWeek(int day) { return (curveDay(day) - 1) % 5 + 1; }
    public static int score(int day) { return 5 * week(day); }
    public static int intensity(int day) { return score(day) - 5 + withinWeek(day); }
    public static int tier(int day) { return (week(day) + 1) / 2; }
    public static boolean unlocked(int day, int firstWeek) { return EventCalendar.probability() || week(day) >= firstWeek; }
    public static boolean safe(int day) { return EventCalendar.probability()?day<=LunarDreadConfig.GRACE_DAYS.get():week(day)==1; }
    public static boolean deathBloodAllowed(int day) { return week(day) >= 3; }

    public static double playerScale(int activePlayers) {
        return 1.0 + .03 * (Math.clamp(activePlayers, 5, 10) - 5);
    }
    public static int ordinaryTarget(int day, int activePlayers) {
        if (safe(day)) return 0;
        return Math.min(72, (int)Math.ceil((12 + intensity(day) / 2) * playerScale(activePlayers)));
    }
    public static int bloodTarget(int day, int activePlayers) {
        if (safe(day)) return 0;
        return Math.min(120, (int)Math.ceil((32 + 4 * (week(day) - 2)) * playerScale(activePlayers)));
    }
    private static int endgame(int day) { return Math.max(0, intensity(day) - 50); }
    public static double zombieHealth(int day) {
        int x=endgame(day);
        return 20 + .25 * (intensity(day) - 6) + .015 * x * x;
    }
    public static double meleeDamage(int day) {
        int x=endgame(day);
        return 3 + .04 * (intensity(day) - 6) + .0015 * x * x;
    }
    public static double mobSpeed(int day) {
        return Math.min(.34, .23 + .0009 * (intensity(day) - 6)
            + .0003 * Math.max(0, intensity(day) - 70));
    }
    public static double animalDamageScale(int day) {
        int x=endgame(day);
        return 1 + .006 * (intensity(day) - 6) + .00008 * x * x;
    }
    public static double animalHealthScale(int day) {
        int x=endgame(day);
        return 1 + .005 * (intensity(day) - 6) + .00006 * x * x;
    }

    public static double bloodHealthScale(int day) {
        return new double[]{1.05, 1.08, 1.10, 1.12, 1.14, 1.17, 1.20, 1.25, 1.32, 1.40}[tier(day)-1];
    }
    public static double bloodDamageScale(int day) {
        return new double[]{1.03, 1.05, 1.06, 1.08, 1.08, 1.10, 1.13, 1.18, 1.24, 1.30}[tier(day)-1];
    }
}
