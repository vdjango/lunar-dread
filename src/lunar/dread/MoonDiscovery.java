package lunar.dread;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/** Fixed per-opportunity probability. A failed trial never raises the next trial's chance. */
public final class MoonDiscovery {
    public static final class State extends SavedData {
        CompoundTag players=new CompoundTag();
        static State load(CompoundTag t,HolderLookup.Provider r){var s=new State();s.players=t.getCompound("players");return s;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){t.put("players",players);return t;}
    }
    static State state(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_discovery");}
    static CompoundTag record(ServerPlayer p){
        var s=state(p.serverLevel());String key=p.getUUID().toString();
        if(!s.players.contains(key))s.players.put(key,new CompoundTag());var d=s.players.getCompound(key);
        int day=SurvivalRules.day(p.serverLevel());if(d.getInt("day")!=day){d=new CompoundTag();d.putInt("day",day);s.players.put(key,d);s.setDirty();}return d;
    }
    public static boolean roll(long seed,UUID player,int sequence){return new Random(seed^player.getMostSignificantBits()^Long.rotateLeft(player.getLeastSignificantBits(),17)^0x9e3779b97f4a7c15L*sequence).nextInt(4)==0;}
    public static boolean cooling(ServerPlayer p){var d=record(p);long now=p.serverLevel().getGameTime();return d.getLong("next")>now||d.getLong("markAt")>now;}
    private static Object team(ServerPlayer p){
        try{
            var apiClass=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");Object api=apiClass.getMethod("api").invoke(null);
            var apiInterface=Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            if(!(Boolean)apiInterface.getMethod("isManagerLoaded").invoke(api))return null;
            Object manager=apiInterface.getMethod("getManager").invoke(api);
            var result=(java.util.Optional<?>)Class.forName("dev.ftb.mods.ftbteams.api.TeamManager").getMethod("getTeamForPlayerID",UUID.class).invoke(manager,p.getUUID());
            if(result.isEmpty())return null;Object t=result.get();var c=Class.forName("dev.ftb.mods.ftbteams.api.Team");
            // FTB gives solo players personal teams; these are the ungrouped LAN case.
            if((Boolean)c.getMethod("isPlayerTeam").invoke(t))return null;
            return c.getMethod("getTeamId").invoke(t);
        }catch(ReflectiveOperationException|LinkageError e){return p.getTeam()==null?null:p.getTeam().getName();}
    }
    public static List<ServerPlayer> nearby(ServerPlayer source){
        Object group=team(source);var result=new ArrayList<ServerPlayer>();
        for(var p:source.serverLevel().players()){
            if(!LunarDread.player(p)||p.distanceToSqr(source)>48*48||MoonIncidents.busy(p))continue;
            Object other=team(p);if(p!=source&&other!=null&&!other.equals(group))continue;
            result.add(p);
        }
        if(!result.contains(source))result.addFirst(source);
        result.sort(Comparator.comparingDouble(p->p==source?-1:record(p).getLong("lastFound")));
        return List.copyOf(result);
    }
    public static boolean attempt(ServerPlayer source){
        return attempt(source,false);
    }
    public static boolean attempt(ServerPlayer source,boolean forced){
        if(!LivingMoon.active(source.serverLevel())||!LunarDread.player(source)||!forced&&cooling(source))return false;
        var l=source.serverLevel();var d=record(source);var s=state(l);
        int sequence=d.getInt("sequence")+1;d.putInt("sequence",sequence);s.setDirty();
        if(!forced&&!roll(MoonCycle.state(l).seed,source.getUUID(),sequence))return false;
        long now=l.getGameTime();d.putLong("next",now+1200);
        var discovered=nearby(source);boolean small=!MoonCycle.giant(l);
        if(small)MoonCycle.awaken(l,source);
        HorrorCueDirector.cue(source,MoonContent.WHISPER.get(),.7f,.75f);
        int cap=LunarDread.score(l,"#blood_count")>=5?5:3,index=0;
        for(var p:discovered){
            var entry=record(p);entry.putLong("lastFound",now);MoonDread.witnesses(p,now);
            if(small)MoonIncidents.protect(p,220);
            if(LunarDread.score(l,"#blood_count")<=1)continue;
            if(index<cap){entry.putLong("markAt",now+(small?220:60)+index*40);entry.putInt("markLength",Math.min(600,400+40*Math.max(0,LunarDread.score(l,"#blood_count")-2)));index++;}
        }
        s.setDirty();return true;
    }
    static void update(ServerPlayer p){
        var d=record(p);long due=d.getLong("markAt"),now=p.serverLevel().getGameTime();
        if(due==0||now<due)return;
        if(MoonIncidents.busy(p)&&now-due<200)return;
        d.remove("markAt");state(p.serverLevel()).setDirty();
        if(LunarDread.player(p)&&LivingMoon.active(p.serverLevel())&&!MoonIncidents.busy(p))MoonDread.mark(p,d.getInt("markLength"));
    }
    public static void cancel(ServerPlayer p){var d=record(p);d.remove("markAt");state(p.serverLevel()).setDirty();}
}
