package lunar.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Behaviour windows nominate one encounter; they do not each create an independent army. */
public final class HorrorDirector {
    private static final java.util.WeakHashMap<ServerLevel,Long> next=new java.util.WeakHashMap<>();
    private static void count(ServerPlayer p,String key,int window){
        var d=p.getPersistentData();long now=p.level().getGameTime();
        if(now-d.getLong(key+".at")>window)d.putInt(key,0);
        if(d.getInt(key)==0)d.putLong(key+".at",now);d.putInt(key,d.getInt(key)+1);
    }
    @SubscribeEvent public void mine(BlockEvent.BreakEvent e){if(e.getPlayer() instanceof ServerPlayer p&&e.getState().is(BlockTags.BASE_STONE_OVERWORLD))count(p,"lunar_dread.mining",1200);}
    @SubscribeEvent public void build(BlockEvent.EntityPlaceEvent e){if(e.getEntity() instanceof ServerPlayer p)count(p,"lunar_dread.building",1200);}
    @SubscribeEvent public void slaughter(LivingDeathEvent e){if(e.getEntity() instanceof net.minecraft.world.entity.animal.Animal a&&!(a instanceof net.minecraft.world.entity.TamableAnimal t&&t.isTame())&&e.getSource().getEntity() instanceof ServerPlayer p)count(p,"lunar_dread.slaughter",1800);}
    static BlockPos caveSpot(ServerLevel level,int x,int y,int z){
        if(!level.hasChunk(x>>4,z>>4))return null;
        // Search the same distant column for a real cave floor; a one-block Y guess
        // misses most generated caves and makes the early encounter feel absent.
        for(int step=0;step<=8;step++)for(int sign:new int[]{1,-1}){
            if(step==0&&sign<0)continue;
            BlockPos pos=new BlockPos(x,y+step*sign,z);
            if(pos.getY()<=level.getMinBuildHeight()+1||pos.getY()>=level.getMaxBuildHeight()-2)continue;
            if(!level.getBlockState(pos).canBeReplaced()||!level.getBlockState(pos.above()).canBeReplaced()
                ||!level.getFluidState(pos).isEmpty()||level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())continue;
            if(BloodFogProfile.depth(level,pos)>.45f)return pos;
        }
        return null;
    }
    static HorrorMob spawn(ServerLevel l,ServerPlayer p,int kind,BlockPos pos){
        int total=0,lunar=0,ground=0,grabbers=0;
        for(var e:l.getAllEntities())if(e instanceof Enemy){total++;if(e.getTags().contains("lunar_dread.lunar"))lunar++;if(e instanceof HorrorMob h&&h.kind()<5){ground++;if(h.kind()==0||h.kind()==3)grabbers++;}}
        if(lunar>=8||ground>=4||total>=Math.max(28,LunarDread.score(l,"#target"))||((kind==0||kind==3)&&grabbers>=2))return null;
        if(kind==4&&AirRaid.wingCount(l)+MoonGravity.wings(l).size()>=6)return null;
        if(!l.hasChunkAt(pos)||!l.getWorldBorder().isWithinBounds(pos))return null;
        var m=MoonContent.horror(kind).get().create(l);if(m==null)return null;
        m.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);if(!l.noCollision(m)||!l.getFluidState(pos).isEmpty()||l.players().stream().anyMatch(player->LunarDread.player(player)&&player.distanceToSqr(m)<576))return null;
        m.addTag("lunar_dread.lunar");m.addTag("lunar_dread.wave");m.setPersistenceRequired();m.setTarget(p);
        m.getPersistentData().putLong("lunar_dread.expires",l.getGameTime()+1800);
        int day=SurvivalRules.day(l);
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(
            1.5*DifficultyRules.zombieHealth(day)
                *(LunarDread.score(l,"#blood_active")==1?DifficultyRules.bloodHealthScale(day):1));
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(
            1.3*DifficultyRules.meleeDamage(day)
                *(LunarDread.score(l,"#blood_active")==1?DifficultyRules.bloodDamageScale(day):1));
        m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(
            Math.min(.34,1.05*DifficultyRules.mobSpeed(day)));
        m.setHealth(m.getMaxHealth());
        return l.addFreshEntity(m)?m:null;
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        if(e.getServer().getTickCount()%20!=0)return;var l=e.getServer().overworld();long now=l.getGameTime();
        var state=MoonCycle.state(l);if(!LunarDreadConfig.HORROR_ENCOUNTERS_ENABLED.get()||!AnimalCombat.worldActive(l)||LunarDread.score(l,"#safe_active")==1||now<state.nightAt+200&&MoonCycle.night(l))return;
        long global=next.getOrDefault(l,0L);if(now<global)return;
        for(var p:l.players()){
            if(!LunarDread.player(p)||MoonIncidents.busy(p)||LivingMoon.targeted(p))continue;
            var d=p.getPersistentData();if(now<d.getLong("lunar_dread.horror_next")||d.getLong("lunar_dread.hunt_order")>now)continue;
            int day=SurvivalRules.day(l),week=DifficultyRules.week(day),kind=-1;
            boolean underground=BloodFogProfile.depth(l,p.blockPosition())>.45;
            if(week>=2&&underground){
                int mined=d.getInt("lunar_dread.mining");
                boolean activeMining=mined>=(week<=4?16:24)&&now-d.getLong("lunar_dread.mining.at")<=1200;
                int miningOdds=week==2?4:week<=4?3:1;
                int ambientOdds=week==2?480:week<=4?300:120;
                if((activeMining&&l.random.nextInt(miningOdds)==0)||l.random.nextInt(ambientOdds)==0)kind=1;
            }
            if(kind<0&&DifficultyRules.unlocked(day,7)&&d.getInt("lunar_dread.building")>=24&&now-d.getLong("lunar_dread.building.at")<=1200&&p.getY()>l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,p.getBlockX()+6,p.getBlockZ())+6)kind=2;
            if(kind<0&&DifficultyRules.unlocked(day,5)&&d.getInt("lunar_dread.slaughter")>=4&&now-d.getLong("lunar_dread.slaughter.at")<=1800)kind=DifficultyRules.unlocked(day,11)?0:1;
            if(kind<0&&DifficultyRules.unlocked(day,13)&&p.isFallFlying())kind=4;
            if(kind<0&&DifficultyRules.unlocked(day,8)&&MoonCycle.night(l)&&now-d.getLong("lunar_dread.last_encounter")>2400&&l.random.nextInt(8)==0)kind=3;
            if(kind<0&&DifficultyRules.unlocked(day,11)&&LivingMoon.active(l)&&l.random.nextInt(20)==0)kind=0;
            if(kind<0)continue;
            HorrorMob mob=null;
            for(int i=0;i<64&&mob==null;i++){
                double a=l.random.nextDouble()*Math.PI*2;int r=28+l.random.nextInt(21);int x=p.getBlockX()+(int)(Math.cos(a)*r),z=p.getBlockZ()+(int)(Math.sin(a)*r);
                BlockPos pos=underground?caveSpot(l,x,p.getBlockY(),z):SurvivalRules.surface(l,x,z);
                if(pos!=null)mob=spawn(l,p,kind,pos);
            }
            d.putLong("lunar_dread.horror_next",now+(mob==null?200:kind==0?3600:week==2?3600:week<=4?3000:2400));
            if(mob!=null){d.putLong("lunar_dread.last_encounter",now);d.putInt("lunar_dread.mining",0);d.putInt("lunar_dread.building",0);d.putInt("lunar_dread.slaughter",0);next.put(l,now+(week==2?2400:week<=4?1800:600));HorrorCueDirector.cue(p,kind==1||kind==2?MoonContent.SCRAPE.get():MoonContent.WHISPER.get(),.95f,.78f);break;}
        }
    }
}
