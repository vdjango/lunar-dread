package lunar.dread;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns standalone world progress; defers to the existing pack in hundred-day mode. */
public final class EventCalendar {
    public static final class State extends SavedData {
        int day=-1, lastBloodDay, bloodCount, countedBloodDay=-1, supplyDay=-1;
        String mode="";
        long lastTick=Long.MIN_VALUE;
        static State load(CompoundTag tag,HolderLookup.Provider registry) {
            var s=new State();s.day=tag.getInt("day");s.lastBloodDay=tag.getInt("lastBloodDay");
            s.bloodCount=tag.getInt("bloodCount");s.countedBloodDay=tag.getInt("countedBloodDay");
            s.supplyDay=tag.getInt("supplyDay");s.mode=tag.getString("mode");return s;
        }
        @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider registry) {
            tag.putInt("day",day);tag.putInt("lastBloodDay",lastBloodDay);tag.putInt("bloodCount",bloodCount);
            tag.putInt("countedBloodDay",countedBloodDay);tag.putInt("supplyDay",supplyDay);tag.putString("mode",mode);return tag;
        }
    }
    static State state(ServerLevel l) {
        return l.getServer().overworld().getDataStorage().computeIfAbsent(
            new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_calendar");
    }
    public static boolean probability() { return LunarDreadConfig.EVENT_MODE.get()==LunarDreadConfig.EventMode.PROBABILITY; }
    public static boolean packOwns(ServerLevel l) {
        return !probability() && l.getServer().getFunctions().get(ResourceLocation.fromNamespaceAndPath("lunar_dread","second")).isPresent();
    }
    public static void ensure(ServerLevel input) {
        var l=input.getServer().overworld();
        if(l.getScoreboard().getObjective("lunar_dread.sys")==null)
            MoonCycle.command(l,"scoreboard objectives add lunar_dread.sys dummy");
        boolean pack=packOwns(l);
        MoonCycle.score(l,"#native_calendar",pack?0:1);
        var s=state(l);long now=l.getGameTime();String mode=LunarDreadConfig.EVENT_MODE.get().name();
        if(pack) {
            // Remember transitions through the datapack too, including a round trip on one day.
            if(!s.mode.equals(mode)){s.mode=mode;s.setDirty();}
            return;
        }
        if(s.lastTick==now&&s.mode.equals(mode))return;s.lastTick=now;
        int day=SurvivalRules.day(l),clock=(int)Math.floorMod(l.getDayTime(),24000);
        if(!s.mode.equals(mode)) {
            // Preserve the accumulated world date and existing blood history on mode changes.
            s.bloodCount=Math.max(s.bloodCount,LunarDread.score(l,"#blood_count"));
            int packLast=LunarDread.score(l,"#last_blood_day");
            if(packLast>s.lastBloodDay){s.lastBloodDay=packLast;s.countedBloodDay=packLast;}
            s.mode=mode;s.day=-1;MoonCycle.state(l).day=-1;MoonCycle.state(l).setDirty();
        }
        if(s.day!=day) {
            MoonIncidents.clearWorld(l);MoonAnomalies.clear(l);LivingMoon.cancel(l);
            s.day=day;
            for(String key:new String[]{"#blood_active","#is_blood","#safe_active","#recovering","#preblood","#finished"})MoonCycle.score(l,key,0);
            MoonCycle.score(l,"#is_blood",!probability()&&EventSchedule.scheduledBlood(day,s.lastBloodDay)?1:0);
            MoonCycle.score(l,"#recovering",!probability()&&EventSchedule.recovering(day,s.lastBloodDay)?1:0);
            MoonCycle.score(l,"#preblood",!probability()&&EventSchedule.scheduledBlood(day+1,s.lastBloodDay)?1:0);
            s.setDirty();
        }
        MoonCycle.score(l,"#day",day);MoonCycle.score(l,"#week",DifficultyRules.week(day));
        MoonCycle.score(l,"#tier",DifficultyRules.tier(day));MoonCycle.score(l,"#within",DifficultyRules.withinWeek(day));
        MoonCycle.score(l,"#overall",DifficultyRules.score(day));MoonCycle.score(l,"#clock",clock);
        var board=l.getScoreboard();var objective=board.getObjective("lunar_dread.sys");
        for(String key:new String[]{"#moon_attacks","#dread"})
            if(board.getPlayerScoreInfo(net.minecraft.world.scores.ScoreHolder.forNameOnly(key),objective)==null)MoonCycle.score(l,key,1);
        MoonCycle.score(l,"#finished",!probability()&&day>100?1:0);
        if(clock<12000||LunarDread.score(l,"#finished")==1) {
            MoonCycle.score(l,"#blood_active",0);MoonCycle.score(l,"#safe_active",0);
        }
        int players=(int)l.players().stream().filter(LunarDread::player).count();
        MoonCycle.score(l,"#target",DifficultyRules.ordinaryTarget(day,players));
        MoonCycle.score(l,"#blood_target",DifficultyRules.bloodTarget(day,players));
    }
    static void applyMoon(ServerLevel l) {
        if(packOwns(l))return;
        var s=state(l);var moon=MoonCycle.state(l);
        boolean blood=MoonCycle.night(l)&&moon.day==s.day&&moon.kind==MoonCycle.BLOOD;
        MoonCycle.score(l,"#blood_active",blood?1:0);
        if(blood&&s.countedBloodDay!=s.day) {
            s.countedBloodDay=s.day;s.lastBloodDay=s.day;s.bloodCount++;s.setDirty();
        }
        MoonCycle.score(l,"#blood_count",s.bloodCount);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Pre event) { ensure(event.getServer().overworld()); }
    @SubscribeEvent public void after(ServerTickEvent.Post event) {
        var l=event.getServer().overworld();if(packOwns(l)||probability()||l.getGameTime()%100!=0)return;
        if(LunarDread.score(l,"#finished")==1||LunarDread.score(l,"#recovering")==1)return;
        var s=state(l);int day=SurvivalRules.day(l),clock=(int)Math.floorMod(l.getDayTime(),24000);
        var players=l.players().stream().filter(LunarDread::player).toList();if(players.isEmpty())return;
        if(day%5==4&&clock>=6000&&clock<13000&&s.supplyDay!=day) {
            if(SupplyDrops.spawn(l,players.getFirst().position(),0,day<=5)){s.supplyDay=day;s.setDirty();}
        }
        if(!MoonCycle.night(l)||DifficultyRules.safe(day)||MoonCycle.peaceful(l))return;
        // Native, bounded reinforcement when The Hordes/datapack are absent.
        int target=LivingMoon.active(l)?DifficultyRules.bloodTarget(day,players.size()):DifficultyRules.ordinaryTarget(day,players.size());
        long alive=0;for(var entity:l.getAllEntities())if(entity instanceof net.minecraft.world.entity.monster.Enemy)alive++;
        for(int i=0;i<Math.min(4,Math.max(0,target-alive));i++) {
            var p=players.get(l.random.nextInt(players.size()));double angle=l.random.nextDouble()*Math.PI*2;
            int distance=28+l.random.nextInt(17);
            var pos=SurvivalRules.surface(l,p.getBlockX()+(int)(Math.cos(angle)*distance),p.getBlockZ()+(int)(Math.sin(angle)*distance));
            if(pos==null||players.stream().anyMatch(other->other.distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(pos))<24*24))continue;
            var zombie=net.minecraft.world.entity.EntityType.ZOMBIE.create(l);if(zombie==null)continue;
            zombie.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);zombie.addTag("lunar_dread.wave");zombie.setTarget(p);
            if(l.noCollision(zombie))l.addFreshEntity(zombie);
        }
    }
}
