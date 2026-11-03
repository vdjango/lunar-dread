package lunar.dread;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Personal sensory attacks share a scheduler with gravity and moon strikes. */
public final class MoonIncidents {
    private static final Map<UUID,Personal> states=new HashMap<>();
    static final class Personal {long blackout=-1,next,protectedUntil;int length;boolean menu;}
    private static Personal personal(ServerPlayer p){return states.computeIfAbsent(p.getUUID(),id->new Personal());}
    public static void protect(ServerPlayer p,int ticks){personal(p).protectedUntil=Math.max(personal(p).protectedUntil,p.serverLevel().getGameTime()+ticks);}
    public static boolean sensoryBusy(ServerPlayer p){var a=states.get(p.getUUID());return AbductionController.busy(p)||p.getPersistentData().getLong("lunar_dread.rope_lock")>p.level().getGameTime()||a!=null&&(a.blackout>=0||a.protectedUntil>p.serverLevel().getGameTime());}
    public static boolean busy(ServerPlayer p){return sensoryBusy(p)||MoonGravity.busy(p);}
    public static boolean black(ServerPlayer p){var a=states.get(p.getUUID());return a!=null&&a.blackout>=0;}
    public static void menu(ServerPlayer p,boolean open){var a=personal(p);a.menu=open;if(open&&a.blackout>=0)endBlack(p,a);}
    private static void endBlack(ServerPlayer p,Personal a){a.blackout=-1;p.removeEffect(MoonContent.BLACKOUT);protect(p,120);send(p);}
    public static boolean blink(ServerPlayer p,boolean forced){
        var l=p.serverLevel();var s=MoonCycle.state(l);var a=personal(p);
        if(!LivingMoon.active(l)||!LunarDread.player(p)||busy(p)||a.menu||LivingMoon.targeted(p)||LivingMoon.sheltered(l,p.position()))return false;
        int day=SurvivalRules.day(l);var d=p.getPersistentData();
        if(d.getInt("lunar_dread.blink_day")!=day){d.putInt("lunar_dread.blink_day",day);d.putInt("lunar_dread.blink_count",0);}
        int count=d.getInt("lunar_dread.blink_count"),cap=LunarDread.score(l,"#blood_count")<=1||s.form==0?1:2;
        if(!forced&&count>=cap)return false;
        d.putInt("lunar_dread.blink_count",count+1);a.length=s.form==0||LunarDread.score(l,"#blood_count")<=1?20:36+l.random.nextInt(9);
        a.blackout=l.getGameTime();a.next=l.getGameTime()+2400+l.random.nextInt(1201);
        p.addEffect(new MobEffectInstance(MoonContent.BLACKOUT,40+5+a.length+8,0,false,false));
        HorrorCueDirector.cue(p,MoonContent.SILENCE.get(),.65f,1);send(p);return true;
    }
    public static void cure(ServerPlayer p){
        p.getPersistentData().remove("lunar_dread.rope_lock");
        var a=personal(p);if(a.blackout>=0)endBlack(p,a);MoonGravity.cure(p);AbductionController.release(p,false);MoonDiscovery.cancel(p);protect(p,120);
        p.getPersistentData().putLong("lunar_dread.moon_cured",p.serverLevel().getGameTime()+1200);send(p);
    }
    public static void send(ServerPlayer p){
        var a=personal(p);long now=p.serverLevel().getGameTime();var d=p.getPersistentData();
        int gAge=MoonGravity.attached(p)?Math.clamp((int)(now-d.getLong("lunar_dread.gravity_start")),0,1999):-1;
        int duration=Math.clamp(d.getInt("lunar_dread.gravity_duration"),0,2000);
        PacketDistributor.sendToPlayer(p,new MoonUpdates.Personal(now,a.blackout<0?-1:Math.clamp((int)(now-a.blackout),0,1999),a.length,gAge,duration,MoonGravity.force(p),MoonGravity.busy(p)&&gAge<0,HorrorCueDirector.threat(p)));
    }
    public static void clearWorld(ServerLevel l){
        for(var p:l.players()){var a=states.get(p.getUUID());if(a!=null&&a.blackout>=0)endBlack(p,a);MoonGravity.release(p);AbductionController.release(p,false);MoonDiscovery.cancel(p);}
        MoonGravity.clear(l);NearMoon.finish(l);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        for(var p:e.getServer().getPlayerList().getPlayers()){
            var l=p.serverLevel();var a=personal(p);long now=l.getGameTime();
            if(!LunarDread.player(p)||!MoonCycle.night(l)){
                if(a.blackout>=0)endBlack(p,a);a.next=0;if(now%5==0)send(p);continue;
            }
            if(a.blackout>=0){
                int age=(int)(now-a.blackout);
                if(!p.hasEffect(MoonContent.BLACKOUT)||!LivingMoon.active(l)||age>=53+a.length||a.menu||(age<40&&LivingMoon.sheltered(l,p.position())))endBlack(p,a);
                else if(age==40)HorrorCueDirector.cue(p,MoonContent.WHISPER.get(),.35f,.7f);
            }
            if(LivingMoon.active(l)&&now%20==0&&a.blackout<0&&!a.menu){
                var s=MoonCycle.state(l);long earliest=s.form==1?Math.max(s.nightAt,s.awakenedAt)+1200:s.nightAt+1800;
                if(now>=earliest&&now>=a.next){
                    a.next=now+2400+l.random.nextInt(1201);
                    boolean roll=s.form==1?l.random.nextInt(100)<55:new java.util.Random(s.seed^p.getUUID().getLeastSignificantBits()).nextInt(100)<45;
                    if(roll)blink(p,false);
                }
            }
            if(now%5==0)send(p);
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){states.remove(p.getUUID());p.removeEffect(MoonContent.BLACKOUT);MoonGravity.release(p);MoonDiscovery.cancel(p);}}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){var a=personal(p);if(a.blackout>=0)endBlack(p,a);MoonGravity.release(p);MoonDiscovery.cancel(p);}}
    @SubscribeEvent public void stopped(ServerStoppedEvent e){states.clear();}
    @SubscribeEvent public void commands(RegisterCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("lunardread").then(net.minecraft.commands.Commands.literal("moon").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.literal("blink").executes(c->{int n=0;for(var p:c.getSource().getServer().overworld().players())if(blink(p,true))n++;return n;}))));}
}
