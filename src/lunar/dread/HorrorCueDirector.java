package lunar.dread;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Long stretches of quiet, and one owned audio cue rather than three competing loops. */
public final class HorrorCueDirector {
    private static final class Voice {long next,until,quiet,dangerUntil;int threat;}
    private static final Map<UUID,Voice> voices=new HashMap<>();
    public static int threat(ServerPlayer p){var v=voices.get(p.getUUID());return v==null?0:v.threat;}
    public static void cue(ServerPlayer p,net.minecraft.sounds.SoundEvent sound,float volume,float pitch){
        long now=p.serverLevel().getGameTime();var v=voices.computeIfAbsent(p.getUUID(),id->new Voice());
        if(now<v.quiet)return;v.quiet=now+60;v.until=0;
        p.playNotifySound(sound,SoundSource.HOSTILE,volume,pitch);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        var l=e.getServer().overworld();long now=l.getGameTime();
        for(var p:e.getServer().getPlayerList().getPlayers()){
            var v=voices.computeIfAbsent(p.getUUID(),id->new Voice());
            if(!LunarDread.player(p)||p.level()!=l||!MoonCycle.night(l)
                ||DifficultyRules.safe(SurvivalRules.day(l))||LunarDread.score(l,"#safe_active")==1){
                v.next=0;v.until=0;v.threat=0;v.dangerUntil=0;continue;
            }
            if(now%20==0){
                int count=0,near=0;
                for(Mob mob:l.getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(48),
                    m->m instanceof Enemy&&m.isAlive()&&!m.isNoAi())){
                    double distance=mob.distanceToSqr(p);
                    if(distance>28*28&&mob.getTarget()!=p&&!mob.getTags().contains("lunar_dread.wave"))continue;
                    if(distance<=20*20)near++;
                    if(++count>=8)break;
                }
                int found=count>=5||near>=3?2:count>0?1:0;
                if(found>0){
                    if(v.threat==0)cue(p,MoonContent.WHISPER.get(),.85f,.78f);
                    v.threat=found;v.dangerUntil=now+100;
                }else if(now>=v.dangerUntil)v.threat=0;
            }
            boolean blood=MoonCycle.kind(l)==MoonCycle.BLOOD||LivingMoon.active(l);
            boolean giant=MoonCycle.giant(l);
            if(v.next==0)v.next=Math.max(now+(blood?40:240),v.quiet+1);
            if(now>=v.next&&now%56==9){
                v.until=now+(giant?56:28);
                int interval=giant?120:blood?260:600;
                v.next=now+interval+l.random.nextInt(interval/2+1);
            }
            int beat=(int)(now%56);
            if(LunarDreadConfig.HEARTBEAT_ENABLED.get()&&LunarDreadConfig.HEARTBEAT_VOLUME_SCALE.get()>0
                &&now<v.until&&now>=v.quiet&&!MoonIncidents.busy(p)&&beat==9)
                p.playNotifySound(MoonContent.HEART.get(),SoundSource.HOSTILE,
                    (float)(LunarDreadConfig.HEARTBEAT_VOLUME_SCALE.get()*(giant?1.25f:blood?1.15f:.7f)),
                    giant?.9f:blood?.84f:.9f);
        }
        voices.keySet().removeIf(id->e.getServer().getPlayerList().getPlayer(id)==null);
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("sound")
            .then(Commands.literal("test").then(Commands.argument("id",StringArgumentType.word())
                .suggests((c,b)->{for(String id:new String[]{"heart","tension","whisper","rift","scrape","grab","silence"})b.suggest(id);return b.buildFuture();})
                .executes(c->{
                    var p=c.getSource().getPlayerOrException();
                    var id=StringArgumentType.getString(c,"id");
                    var sound=switch(id){
                        case "heart"->MoonContent.HEART.get();case "tension"->MoonContent.TENSION.get();case "whisper"->MoonContent.WHISPER.get();
                        case "rift"->MoonContent.RIFT.get();case "scrape"->MoonContent.SCRAPE.get();
                        case "grab"->MoonContent.GRAB.get();case "silence"->MoonContent.SILENCE.get();
                        default->null;
                    };
                    if(sound==null)return 0;
                    p.playNotifySound(sound,SoundSource.HOSTILE,id.equals("heart")?1.15f:1,id.equals("heart")?.9f:.9f);
                    p.sendSystemMessage(Component.literal("音效测试："+id+"（调高主音量及敌对生物音量，并开启字幕核对）"));
                    return 1;
                })))));
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent e){voices.clear();}
}
