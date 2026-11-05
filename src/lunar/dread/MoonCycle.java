package lunar.dread;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** One persisted choice per world night, shared by local hosts and LAN guests. */
public final class MoonCycle {
    public static final int NORMAL=0,HARVEST=1,BLUE=2,WIND=3,GREEN=4,STAR=5,BLOOD=6,PURPLE=7,PALE=8,MIRROR=9,GRAVITY=10,AMBER=11,TIDE=12,RUST=13,PAPER=14,ASH=15;
    public static final String[] IDS={"normal","harvest","blue","wind","green","star","blood","purple","pale","mirror","gravity","amber","tide","rust","paper","ash"};
    public static final String[] NAMES={"普通月","丰收月","蓝月","旅风月","翠息月","星砂月","血月","紫狱月","苍白月","黑镜月","倒悬月","琥珀月","青潮月","锈雨月","纸幕月","灰烬月"};
    private static final int[] UNLOCK={1,1,1,6,1,6,10,11,16,21,76,26,41,46,51,61};
    public static final class State extends SavedData {
        public int day=-1,kind,form,overlay,dry,dangerStreak,countedDay=-1,stars;
        public int beforeDry,beforeStreak,countedKind=-1;
        public boolean superMoon;
        public long awakenedAt=-1,nightAt=-1,seed,deathAt=-1;
        public boolean applied,forced;
        public int[] last=new int[IDS.length],counts=new int[IDS.length];
        public State(){Arrays.fill(last,-1000);}
        static State load(CompoundTag t,HolderLookup.Provider r){
            var s=new State();s.day=t.getInt("day");s.kind=Math.clamp(t.getInt("kind"),0,IDS.length-1);s.form=t.getInt("form");s.overlay=t.getInt("overlay");
            s.dry=t.getInt("dry");s.dangerStreak=t.getInt("dangerStreak");s.countedDay=t.getInt("countedDay");s.stars=t.getInt("stars");
            s.awakenedAt=t.getLong("awakenedAt");s.nightAt=t.getLong("nightAt");s.seed=t.getLong("seed");s.deathAt=t.contains("deathAt")?t.getLong("deathAt"):-1;
            s.applied=t.getBoolean("applied");s.forced=t.getBoolean("forced");
            s.beforeDry=t.getInt("beforeDry");s.beforeStreak=t.getInt("beforeStreak");s.countedKind=t.contains("countedKind")?t.getInt("countedKind"):s.kind;s.superMoon=t.getBoolean("superMoon");
            int[] oldLast=t.getIntArray("last"),oldCounts=t.getIntArray("counts");
            if(oldLast.length>0)System.arraycopy(oldLast,0,s.last,0,Math.min(oldLast.length,s.last.length));
            if(oldCounts.length>0)System.arraycopy(oldCounts,0,s.counts,0,Math.min(oldCounts.length,s.counts.length));
            return s;
        }
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){
            t.putInt("day",day);t.putInt("kind",kind);t.putInt("form",form);t.putInt("overlay",overlay);t.putInt("dry",dry);t.putInt("dangerStreak",dangerStreak);
            t.putInt("countedDay",countedDay);t.putInt("stars",stars);t.putLong("awakenedAt",awakenedAt);t.putLong("nightAt",nightAt);t.putLong("seed",seed);t.putLong("deathAt",deathAt);
            t.putBoolean("applied",applied);t.putBoolean("forced",forced);t.putIntArray("last",last);t.putIntArray("counts",counts);
            t.putInt("beforeDry",beforeDry);t.putInt("beforeStreak",beforeStreak);t.putInt("countedKind",countedKind);t.putBoolean("superMoon",superMoon);return t;
        }
    }
    public static State state(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_moons");}
    static boolean night(ServerLevel l){return l.dimension()==Level.OVERWORLD&&Math.floorMod(l.getDayTime(),24000)>=13000&&LunarDread.score(l,"#finished")!=1;}
    public static int kind(ServerLevel l){return night(l)?state(l).kind:NORMAL;}
    public static boolean peaceful(ServerLevel l){int k=kind(l);return k>=HARVEST&&k<=STAR;}
    public static boolean giant(ServerLevel l){return LivingMoon.active(l)&&state(l).kind==BLOOD&&state(l).form==1;}
    public static boolean ready(ServerLevel l){var s=state(l);return giant(l)&&l.getGameTime()-s.awakenedAt>=220;}
    static void score(ServerLevel l,String name,int value){var b=l.getScoreboard();var o=b.getObjective("lunar_dread.sys");if(o!=null)b.getOrCreatePlayerScore(ScoreHolder.forNameOnly(name),o).set(value);}
    static void command(ServerLevel l,String text){l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withLevel(l).withSuppressedOutput(),text);}
    private static boolean available(State s,int k,int day){return (EventCalendar.probability()||day>=UNLOCK[k])&&day-s.last[k]>=3;}
    static int weighted(State s,Random random,int day,int from,int[] weights){
        int total=0;for(int i=0;i<weights.length;i++)if(available(s,from+i,day))total+=weights[i];
        if(total==0)return NORMAL;int roll=random.nextInt(total);
        for(int i=0;i<weights.length;i++)if(available(s,from+i,day)){roll-=weights[i];if(roll<0)return from+i;}return NORMAL;
    }
    static int choose(State s,int day,Random random){
        if(EventCalendar.probability()) {
            int[] weights=new int[IDS.length];for(int i=1;i<weights.length;i++)weights[i]=LunarDreadConfig.MOON_WEIGHTS[i].get();
            return EventSchedule.probability(day,LunarDreadConfig.GRACE_DAYS.get(),LunarDreadConfig.EVENT_CHANCE.get(),
                LunarDreadConfig.REPEAT_GAP.get(),weights,s.last,random);
        }
        int week=DifficultyRules.week(day);
        int peace=week==1?30:week==2?25:week<=4?20:week<=8?15:week<=12?12:week<=16?10:8;
        int danger=week<=2?0:week<=4?5:week<=8?10:week<=12?15:week<=16?18:20;
        if(s.dry>=10)return weighted(s,random,day,1,new int[]{30,25,20,15,10});
        int r=random.nextInt(100);if(r<peace)return weighted(s,random,day,1,new int[]{30,25,20,15,10});
        if(r<peace+danger&&s.dangerStreak<2)return weighted(s,random,day,7,new int[]{25,17,15,10,10,9,8,6,6});return NORMAL;
    }
    private static void bloodForm(ServerLevel l,State s,Random random){
        int n=Math.max(1,LunarDread.score(l,"#blood_count")+(LivingMoon.active(l)?0:1));
        s.form=(EventCalendar.probability()?random.nextDouble()<LunarDreadConfig.GIANT_CHANCE.get():s.day>=66&&(s.day==100||random.nextInt(100)<(n<=4?25:40)))?1:0;
        s.awakenedAt=s.form==1?l.getGameTime():-1;
        int r=random.nextInt(100);s.overlay=r<65?0:r<73?1:r<80?2:r<86?3:r<91?4:r<95?5:6;
        int[] required={1,11,21,26,101,51,1};
        if(!EventCalendar.probability()&&(s.day<required[s.overlay]||(n==1&&s.overlay!=6)))s.overlay=0;
        int theme=switch(s.overlay){case 1->PURPLE;case 2->MIRROR;case 3->AMBER;case 4->GRAVITY;case 5->PAPER;default->0;};
        if(theme!=0){if(!available(s,theme,s.day)||(EventCalendar.probability()&&LunarDreadConfig.MOON_WEIGHTS[theme].get()==0))s.overlay=0;else{s.last[theme]=s.day;s.counts[theme]++;}}
    }
    public static void ensure(ServerLevel l){
        EventCalendar.ensure(l);
        if(l.dimension()!=Level.OVERWORLD||LunarDread.score(l,"#day")==0)return;
        int clock=(int)Math.floorMod(l.getDayTime(),24000),day=SurvivalRules.day(l);var s=state(l);
        if(clock<12000){if(LunarDread.score(l,"#safe_active")==1)score(l,"#safe_active",0);return;}
        boolean blood=!DifficultyRules.safe(day)
            &&(LunarDread.score(l,"#is_blood")==1||LunarDread.score(l,"#blood_active")==1);
        if(s.day==day&&s.deathAt>l.getGameTime()&&s.kind!=BLOOD)blood=false;
        if(s.day!=day){
            s.day=day;s.seed=l.random.nextLong();s.form=0;s.overlay=0;s.stars=0;s.nightAt=-1;s.awakenedAt=-1;s.applied=false;s.forced=false;s.deathAt=-1;
            s.beforeDry=s.dry;s.beforeStreak=s.dangerStreak;s.superMoon=false;
            var random=new Random(s.seed);s.kind=blood?BLOOD:LunarDread.score(l,"#recovering")==1?NORMAL:choose(s,day,random);
            if(s.kind==BLOOD)bloodForm(l,s,random);s.setDirty();
        }else if(DifficultyRules.safe(day)&&s.kind==BLOOD&&!s.forced){
            MoonIncidents.clearWorld(l);MoonAnomalies.clear(l);s.kind=NORMAL;s.form=0;s.overlay=0;
            s.applied=false;s.nightAt=-1;s.awakenedAt=-1;s.forced=false;s.setDirty();
        }else if(blood&&s.kind!=BLOOD&&!s.forced){
            if(s.kind>=HARVEST&&s.kind<=STAR){s.dry=s.beforeDry;s.dangerStreak=s.beforeStreak;}
            MoonIncidents.clearWorld(l);MoonAnomalies.clear(l);s.kind=BLOOD;s.applied=false;s.nightAt=-1;
            bloodForm(l,s,new Random(s.seed^0x51E6E));s.setDirty();
        }
        if(clock>=13000){
            score(l,"#safe_active",peaceful(l)||LunarDread.score(l,"#recovering")==1?1:0);score(l,"#moon_kind",s.kind);
            if(!s.applied){
                s.applied=true;s.nightAt=l.getGameTime();
                String event=s.kind==BLOOD?"blood_moon":s.kind==HARVEST?"harvest_moon":s.kind==BLUE?"blue_moon":"default";
                s.superMoon=(s.kind==HARVEST||s.kind==BLUE)&&new Random(s.seed^0x511E).nextInt(100)<3;if(s.superMoon)event="super_"+event;
                if(net.neoforged.fml.ModList.get().isLoaded("enhancedcelestials"))command(l,"enhancedcelestials setLunarEvent enhancedcelestials:"+event);
                if(s.kind==BLOOD)for(var p:l.players())if(LunarDread.player(p))
                    HorrorCueDirector.cue(p,MoonContent.RIFT.get(),1.15f,.82f);
                if(s.countedDay!=day||s.countedKind!=s.kind){
                    s.countedDay=day;s.countedKind=s.kind;s.counts[s.kind]++;s.last[s.kind]=day;
                    if(peaceful(l)){s.dry=0;s.dangerStreak=0;}
                    else {if(s.kind!=BLOOD)s.dry++;s.dangerStreak=s.kind>=BLOOD?s.dangerStreak+1:0;}
                }
                if(s.kind!=NORMAL&&s.kind!=BLOOD)for(var p:l.players())p.sendSystemMessage(Component.literal("【"+NAMES[s.kind]+"】"+(peaceful(l)?"本夜地面暂歇；地下仍需警惕。":"今晚的月相改变了。")));
                s.setDirty();
            }
        }
        EventCalendar.applyMoon(l);
    }
    public static boolean awaken(ServerLevel l,ServerPlayer source){
        var s=state(l);if(!(s.forced||DifficultyRules.unlocked(SurvivalRules.day(l),14))
            ||!LivingMoon.active(l)||s.form==1)return false;
        s.kind=BLOOD;s.form=1;s.awakenedAt=l.getGameTime();s.setDirty();
        for(var p:l.players())p.playNotifySound(SoundEvents.ELDER_GUARDIAN_CURSE,SoundSource.HOSTILE,.55f,.5f);
        if(source!=null)MoonIncidents.protect(source,220);sync(l);return true;
    }
    public static MoonUpdates.World snapshot(ServerLevel l){
        var s=state(l);return new MoonUpdates.World(kind(l),night(l)?s.form:0,night(l)?s.overlay:0,l.getGameTime(),s.awakenedAt,s.nightAt,
            SupplyDrops.beacons(l),MoonGravity.wings(l),MoonGravity.tumbles(l),NearMoon.center(l),NearMoon.age(l));
    }
    public static void sync(ServerLevel l){var value=snapshot(l);for(var p:l.getServer().getPlayerList().getPlayers())PacketDistributor.sendToPlayer(p,value);}
    /** Operator selection bypasses natural week gates without changing the saved difficulty curve. */
    public static boolean force(ServerLevel l,int kind){
        EventCalendar.ensure(l);
        if(kind<0||kind>=IDS.length||LunarDread.score(l,"#day")==0)return false;
        var s=state(l);MoonIncidents.clearWorld(l);MoonAnomalies.clear(l);
        s.day=SurvivalRules.day(l);s.kind=kind;s.form=0;s.overlay=0;s.stars=0;s.superMoon=false;
        s.forced=true;s.applied=false;s.nightAt=-1;s.awakenedAt=-1;s.deathAt=-1;
        score(l,"#blood_active",kind==BLOOD?1:0);score(l,"#is_blood",kind==BLOOD?1:0);
        s.setDirty();ensure(l);sync(l);return true;
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){ensure(p.getServer().overworld());PacketDistributor.sendToPlayer(p,snapshot(p.getServer().overworld()));}}
    @SubscribeEvent public void tick(ServerTickEvent.Pre e){ensure(e.getServer().overworld());}
    @SubscribeEvent public void after(ServerTickEvent.Post e){
        var l=e.getServer().overworld();if(l.getGameTime()%10==0)sync(l);
        if(!night(l))return;var s=state(l);long t=l.getGameTime()-s.nightAt;
        if(l.getGameTime()%40!=0)return;
        for(var p:l.players()){
            if(!LunarDread.player(p))continue;
            long cured=p.getPersistentData().getLong("lunar_dread.moon_cured");if(l.getGameTime()<cured)continue;
            if(s.kind==WIND)grant(p,MobEffects.MOVEMENT_SPEED);
            if(s.kind==BLUE&&!net.neoforged.fml.ModList.get().isLoaded("enhancedcelestials"))grant(p,MobEffects.LUCK);
            if(s.kind==HARVEST&&!net.neoforged.fml.ModList.get().isLoaded("enhancedcelestials")&&t>0&&t%2400<40)
                p.addEffect(new MobEffectInstance(MobEffects.SATURATION,1,0,false,false));
            if(s.kind==GREEN){grant(p,MobEffects.DIG_SPEED);if(t>0&&t%2400<40)p.addEffect(new MobEffectInstance(MobEffects.SATURATION,1,0,false,false));}
            if(s.kind==BLOOD&&s.overlay==6&&t>0&&t%3600<40&&t<=10800){
                int boon=new Random(s.seed^(t/3600)).nextInt(3);
                var effect=boon==0?MobEffects.MOVEMENT_SPEED:boon==1?MobEffects.DIG_SPEED:MobEffects.LUCK;
                var old=p.getEffect(effect);if(old==null||old.getDuration()<400&&old.getAmplifier()==0)p.addEffect(new MobEffectInstance(effect,400,0,false,false));
            }
        }
        if(s.kind==STAR&&s.stars<2&&t>=(s.stars+1)*1800){
            var players=l.players().stream().filter(LunarDread::player).toList();
            if(!players.isEmpty()&&SupplyDrops.spawn(l,players.getFirst().position(),0,true)){s.stars++;s.setDirty();}
        }
    }
    private static void grant(ServerPlayer p,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> effect){
        var existing=p.getEffect(effect);if(existing!=null&&(existing.getAmplifier()>0||existing.getDuration()>80))return;
        p.addEffect(new MobEffectInstance(effect,70,0,false,false));p.getPersistentData().putString("lunar_dread.moon_boon",effect.getRegisteredName());
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("moon").requires(s->s.hasPermission(2))
            .then(Commands.literal("death").executes(c->{var l=c.getSource().getServer().overworld();var s=state(l);if(night(l)&&s.kind!=BLOOD){s.deathAt=l.getGameTime()+200;s.forced=false;s.setDirty();}return 1;}))
            .then(Commands.literal("night").executes(c->{ensure(c.getSource().getServer().overworld());return 1;}))
            .then(Commands.literal("awaken").executes(c->awaken(c.getSource().getServer().overworld(),c.getSource().getPlayer())?1:0))
            .then(Commands.literal("set").then(Commands.argument("kind",StringArgumentType.word()).suggests((c,b)->{for(String id:IDS)b.suggest(id);return b.buildFuture();}).executes(c->{
                int k=List.of(IDS).indexOf(StringArgumentType.getString(c,"kind"));
                if(k<0){c.getSource().sendFailure(Component.literal("未知月相；用 /lunardread moon set 查看补全列表"));return 0;}
                var l=c.getSource().getServer().overworld();if(!force(l,k)){
                    c.getSource().sendFailure(Component.literal("世界周数尚未初始化"));return 0;
                }
                c.getSource().sendSuccess(()->Component.literal("月相已设为 "+IDS[k]+(night(l)?"":"；将在今晚显示")),false);return 1;
            })))
            .then(Commands.literal("status").executes(c->{var l=c.getSource().getServer().overworld();var s=state(l);c.getSource().sendSuccess(()->Component.literal("moon="+IDS[kind(l)]+" form="+s.form+" overlay="+s.overlay+" day="+s.day+" seed="+s.seed),false);return 1;}))));
    }
}
