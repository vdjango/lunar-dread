package lunar.validation;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import lunar.dread.*;

/** Native gaze, deterministic independent rolls, delayed marks and terrain/cure regression. */
public final class DreadChecks {
    private static Method observe,advance,record,update,mark;
    private static Map<UUID,Object> states;
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
    private static int value(Object a,String key)throws Exception{var f=a.getClass().getDeclaredField(key);f.setAccessible(true);return f.getInt(a);}
    private static net.minecraft.nbt.CompoundTag data(ServerPlayer p)throws Exception{return(net.minecraft.nbt.CompoundTag)record.invoke(null,p);}
    private static void reset(ServerPlayer p)throws Exception{
        states.clear();p.removeAllEffects();var d=data(p);for(var key:java.util.Set.copyOf(d.getAllKeys()))if(!key.equals("day"))d.remove(key);
        var f=MoonIncidents.class.getDeclaredField("states");f.setAccessible(true);((Map<?,?>)f.get(null)).clear();
        long seed=1;while(!MoonDiscovery.roll(seed,p.getUUID(),1))seed++;MoonCycle.state(p.serverLevel()).seed=seed;
    }
    private static Object stare(ServerPlayer p,long start,int ticks)throws Exception{
        Object a=null;for(int i=0;i<ticks;i++){if(i%4==0)observe.invoke(null,p,true,start+i);a=states.get(p.getUUID());advance.invoke(null,p,a,start+i);}return a;
    }
    @SuppressWarnings("unchecked") public static void run(ServerPlayer p){
        var l=p.serverLevel();long time=l.getDayTime(),start=l.getGameTime()+100;var roof=new BlockPos(260,303,260);var old=l.getBlockState(roof);var actors=new java.util.ArrayList<Mob>();
        try{
            var f=MoonDread.class.getDeclaredField("attention");f.setAccessible(true);states=(Map<UUID,Object>)f.get(null);
            var c=Class.forName("lunar.dread.MoonDread$Attention");observe=MoonDread.class.getDeclaredMethod("observe",ServerPlayer.class,boolean.class,long.class);observe.setAccessible(true);
            advance=MoonDread.class.getDeclaredMethod("advance",ServerPlayer.class,c,long.class);advance.setAccessible(true);
            record=MoonDiscovery.class.getDeclaredMethod("record",ServerPlayer.class);record.setAccessible(true);update=MoonDiscovery.class.getDeclaredMethod("update",ServerPlayer.class);update.setAccessible(true);
            mark=MoonDread.class.getDeclaredMethod("mark",ServerPlayer.class,int.class);mark.setAccessible(true);
            l.setDayTime(234000);p.setGameMode(GameType.SURVIVAL);p.moveTo(260,300,260);p.setHealth(20);l.setBlockAndUpdate(roof,Blocks.AIR.defaultBlockState());
            MoonChecks.score(p,"#blood_active",1);MoonChecks.score(p,"#blood_count",1);MoonChecks.score(p,"#moon_attacks",1);MoonChecks.score(p,"#day",10);MoonChecks.score(p,"#finished",0);MoonChecks.score(p,"#safe_active",0);
            var cycle=MoonCycle.state(l);cycle.day=10;cycle.kind=6;cycle.form=1;cycle.forced=true;cycle.awakenedAt=l.getGameTime()-1000;
            reset(p);Object a=stare(p,start,39);check(value(a,"gaze")==39&&data(p).getInt("sequence")==0,"Glance must not roll before two valid seconds");
            observe.invoke(null,p,false,start+40);advance.invoke(null,p,a,start+40);check(value(a,"gaze")==0,"Looking away cancels accumulation");
            reset(p);a=stare(p,start,80);check(!p.hasEffect(MobEffects.GLOWING)&&data(p).getInt("sequence")==1,"First blood warns without a mark; continuous samples do not reroll");
            reset(p);MoonChecks.score(p,"#blood_count",2);
            for(int i=0;i<16;i++){var cow=EntityType.COW.create(l);cow.moveTo(265+i%4,300,260+i/4);l.addFreshEntity(cow);actors.add(cow);}
            var pet=EntityType.WOLF.create(l);pet.moveTo(264,300,260);pet.setTame(true,false);l.addFreshEntity(pet);actors.add(pet);
            a=stare(p,start,40);check(actors.stream().filter(m->m.getPersistentData().hasUUID("lunar_dread.moon_witness")).count()==12&&!pet.getPersistentData().hasUUID("lunar_dread.moon_witness"),"Witness cap and tamed exclusion");
            check(!MoonDread.marked(p)&&data(p).getLong("markAt")==l.getGameTime()+60,"Discovered giant-moon player receives a three-second physical warning");
            data(p).putLong("markAt",l.getGameTime());update.invoke(null,p);check(MoonDread.marked(p)&&value(a,"mark")==400,"Due mark must begin a bounded real hunt");
            advance.invoke(null,p,a,((start+79)/20)*20);check(actors.stream().anyMatch(m->m.getTarget()==p),"Marked player must attract nearby mobs");
            int before=value(a,"mark");l.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());advance.invoke(null,p,a,start+61);check(value(a,"mark")==before-4,"Real roof speeds mark decay");
            p.removeAllEffects();advance.invoke(null,p,a,start+62);check(!MoonDread.marked(p),"Milk effect removal clears the mark");
            reset(p);a=stare(p,start,90);check(value(a,"gaze")==0&&data(p).getInt("sequence")==0,"Solid roof blocks gaze trials");
            l.setBlockAndUpdate(roof,Blocks.AIR.defaultBlockState());reset(p);observe.invoke(null,p,true,start);a=states.get(p.getUUID());for(int i=0;i<80;i++)advance.invoke(null,p,a,start+i);
            check(data(p).getInt("sequence")==0,"One stale client sample cannot produce a trial");
            reset(p);long fail=1;while(MoonDiscovery.roll(fail,p.getUUID(),1))fail++;cycle.seed=fail;a=stare(p,start,80);check(data(p).getInt("sequence")==1&&data(p).getLong("next")==0&&!MoonDread.marked(p),"A failed trial neither guarantees awakening nor raises probability");
            int successes=0;for(int i=1;i<=20000;i++)if(MoonDiscovery.roll(4242,p.getUUID(),i))successes++;check(successes>4700&&successes<5300,"Independent per-opportunity rolls must remain approximately 25 percent");
            reset(p);mark.invoke(null,p,400);a=states.get(p.getUUID());MoonChecks.score(p,"#moon_attacks",0);advance.invoke(null,p,a,start);check(value(a,"mark")==0,"Attack switch clears marks");
            MoonChecks.score(p,"#moon_attacks",1);reset(p);mark.invoke(null,p,400);a=states.get(p.getUUID());l.setDayTime(240000);advance.invoke(null,p,a,start);check(value(a,"mark")==0,"Dawn resets attention");
            l.setDayTime(234000);reset(p);p.setGameMode(GameType.CREATIVE);a=stare(p,start,80);check(data(p).getInt("sequence")==0,"Creative players must be excluded");
        }catch(Exception e){throw new IllegalStateException("Moon attention regression",e);}
        finally{for(var actor:actors)actor.discard();l.setBlockAndUpdate(roof,old);l.setDayTime(time);if(states!=null)states.clear();p.removeAllEffects();p.setGameMode(GameType.SURVIVAL);p.setHealth(20);MoonChecks.score(p,"#blood_active",0);MoonChecks.score(p,"#moon_attacks",0);}
    }
}
