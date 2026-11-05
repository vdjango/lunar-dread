package lunar.dread;

import java.util.Random;

/** Pure scheduling rules shared by the runtime and deterministic checks. */
public final class EventSchedule {
    private EventSchedule() {}

    public static boolean scheduledBlood(int day, int lastBloodDay) {
        return day >= 10 && day <= 100 && day % 5 == 0 && day-lastBloodDay >= 3;
    }

    public static boolean recovering(int day, int lastBloodDay) {
        return lastBloodDay > 0 && day > lastBloodDay && day-lastBloodDay <= 2;
    }

    public static int probability(int day, int graceDays, double chance, int gap,
                                  int[] weights, int[] last, Random random) {
        if (day <= graceDays || random.nextDouble() >= chance) return 0;
        int total=0;
        for(int i=1;i<weights.length;i++)
            if(last[i]<0 || day-last[i]>gap) total+=Math.max(0,weights[i]);
        if(total==0)return 0;
        int roll=random.nextInt(total);
        for(int i=1;i<weights.length;i++) if(last[i]<0 || day-last[i]>gap) {
            roll-=Math.max(0,weights[i]);
            if(roll<0)return i;
        }
        return 0;
    }
}
