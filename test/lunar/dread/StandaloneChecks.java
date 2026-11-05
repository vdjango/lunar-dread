package lunar.dread;

import java.util.Arrays;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;

/** Real-server checks: no pack, no compatibility mods, fresh scoreboard and persisted state. */
public final class StandaloneChecks {
    private static void check(boolean value,String message) { if(!value)throw new IllegalStateException(message); }
    private static void time(ServerLevel l,int day,int clock) {
        l.setDayTime((day-1L)*24000+clock);EventCalendar.state(l).lastTick=Long.MIN_VALUE;
        EventCalendar.ensure(l);MoonCycle.ensure(l);
    }
    public static void run(ServerLevel l) {
        check(!net.neoforged.fml.ModList.get().isLoaded("enhancedcelestials"),"Fixture must not have EC");
        check(!net.neoforged.fml.ModList.get().isLoaded("stellarview"),"Fixture must not have Stellar View");
        check(!net.neoforged.fml.ModList.get().isLoaded("zbb"),"Fixture must not have ZBB");
        check(!net.neoforged.fml.ModList.get().isLoaded("hordes"),"Fixture must not have The Hordes");
        LunarDreadConfig.EVENT_MODE.set(LunarDreadConfig.EventMode.PROBABILITY);
        LunarDreadConfig.EVENT_CHANCE.set(1.0);LunarDreadConfig.REPEAT_GAP.set(0);LunarDreadConfig.GRACE_DAYS.set(0);
        for(int i=1;i<16;i++)LunarDreadConfig.MOON_WEIGHTS[i].set(i==MoonCycle.BLOOD?1:0);
        time(l,1,18000);
        check(LunarDread.score(l,"#day")==1,"Standalone calendar must initialize itself");
        check(MoonCycle.kind(l)==MoonCycle.BLOOD&&LivingMoon.active(l),"First-night probability blood must be active");
        check(LunarDread.score(l,"#blood_count")==1,"Blood count must increment once");
        long seed=MoonCycle.state(l).seed;MoonCycle.ensure(l);MoonCycle.ensure(l);
        check(MoonCycle.state(l).seed==seed&&LunarDread.score(l,"#blood_count")==1,"Same night must not reroll/count twice");
        LunarDreadConfig.EVENT_CHANCE.set(0.0);time(l,2,18000);
        check(MoonCycle.kind(l)==MoonCycle.NORMAL&&!LivingMoon.active(l),"Zero probability must select ordinary moon");
        LunarDreadConfig.EVENT_CHANCE.set(1.0);
        for(int i=1;i<16;i++)LunarDreadConfig.MOON_WEIGHTS[i].set(i==MoonCycle.GRAVITY?1:0);
        time(l,3,18000);check(MoonCycle.kind(l)==MoonCycle.GRAVITY,"Probability mode must not gate moon types behind day 76");
        for(int i=1;i<16;i++)LunarDreadConfig.MOON_WEIGHTS[i].set(0);
        time(l,4,18000);check(MoonCycle.kind(l)==MoonCycle.NORMAL,"All disabled weights must safely fall back");
        LunarDreadConfig.GRACE_DAYS.set(5);time(l,5,18000);
        check(DifficultyRules.safe(5),"Probability grace period must apply");
        time(l,150,18000);check(LunarDread.score(l,"#finished")==0,"Probability mode must not stop at day 100");
        LunarDreadConfig.EVENT_MODE.set(LunarDreadConfig.EventMode.HUNDRED_DAYS);
        time(l,6,18000);check(LunarDread.score(l,"#week")==2&&!DifficultyRules.safe(6),"Native five-day week must advance");
        time(l,10,18000);check(LivingMoon.active(l),"Native hundred-day mode must schedule day-10 blood");
        time(l,11,18000);check(SurvivalRules.recoveryNight(l)&&!LivingMoon.active(l),"Two recovery nights must follow blood");
        time(l,12,18000);check(SurvivalRules.recoveryNight(l),"Second recovery night missing");
        time(l,15,18000);check(LivingMoon.active(l),"Day-15 blood missing");
        time(l,100,18000);check(LivingMoon.active(l)&&MoonCycle.giant(l),"Day 100 must retain final giant blood moon");
        time(l,101,18000);check(LunarDread.score(l,"#finished")==1&&!MoonCycle.night(l),"Hundred-day mode must end after day 100");
        int[] weights=new int[16],last=new int[16];Arrays.fill(last,-1000);weights[6]=1;last[6]=8;
        check(EventSchedule.probability(9,0,1,2,weights,last,new Random(1))==0,"Repeat cooldown must prevent adjacent blood");
        check(EventSchedule.probability(11,0,1,2,weights,last,new Random(1))==6,"Repeat cooldown must eventually release");
        // Leave a real native world in a forced first-night blood moon for restart checks.
        LunarDreadConfig.EVENT_MODE.set(LunarDreadConfig.EventMode.PROBABILITY);LunarDreadConfig.EVENT_MODE.save();
        LunarDreadConfig.GRACE_DAYS.set(0);time(l,201,18000);MoonCycle.force(l,MoonCycle.BLOOD);
        l.getServer().saveEverything(true,true,true);
        System.out.println("LUNAR_STANDALONE_CHECKS_OK seed="+MoonCycle.state(l).seed+" blood="+LunarDread.score(l,"#blood_count"));
    }
    public static void persisted(ServerLevel l) {
        MoonCycle.ensure(l);
        check(SurvivalRules.day(l)==201&&MoonCycle.kind(l)==MoonCycle.BLOOD&&MoonCycle.state(l).forced,"Night must survive restart without a datapack");
        int count=LunarDread.score(l,"#blood_count");long seed=MoonCycle.state(l).seed;
        MoonCycle.ensure(l);check(LunarDread.score(l,"#blood_count")==count&&MoonCycle.state(l).seed==seed,"Restart must not recount or reroll");
        System.out.println("LUNAR_STANDALONE_RESTART_OK seed="+seed+" blood="+count);
    }
}
