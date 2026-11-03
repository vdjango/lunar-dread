package lunar.dread;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Regional gravity replaces travel with weak acceleration and conserved inertia, never player flight. */
public final class MoonGravity {
    static final class Tide {
        UUID id=UUID.randomUUID();Vec3 center;long start;int duration=420,height=24;boolean items,near,mobs;
        CompoundTag save(){var t=new CompoundTag();t.putUUID("id",id);t.putDouble("x",center.x);t.putDouble("y",center.y);t.putDouble("z",center.z);t.putLong("start",start);t.putInt("duration",duration);t.putInt("height",height);t.putBoolean("items",items);t.putBoolean("near",near);t.putBoolean("mobs",mobs);return t;}
        static Tide load(CompoundTag t){var v=new Tide();v.id=t.getUUID("id");v.center=new Vec3(t.getDouble("x"),t.getDouble("y"),t.getDouble("z"));v.start=t.getLong("start");v.duration=t.getInt("duration");v.height=t.getInt("height");v.items=t.getBoolean("items");v.near=t.getBoolean("near");v.mobs=t.getBoolean("mobs");return v;}
    }
    public static final class State extends SavedData {
        final List<Tide> tides=new ArrayList<>();long next;
        static State load(CompoundTag t,HolderLookup.Provider r){var s=new State();s.next=t.getLong("next");for(var v:t.getList("tides",10)){if(s.tides.size()==2)break;s.tides.add(Tide.load((CompoundTag)v));}return s;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){var list=new ListTag();tides.forEach(v->list.add(v.save()));t.put("tides",list);t.putLong("next",next);return t;}
    }
    static State state(ServerLevel l){return l.getServer().overworld().getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_gravity");}
    private static boolean wild(Entity e){return !(e instanceof net.minecraft.world.entity.TamableAnimal pet&&pet.isTame())&&!(e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse&&horse.isTamed())&&!(e instanceof Mob mob&&mob.isLeashed());}
    public static boolean attached(Entity e){return !e.level().isClientSide&&e.getPersistentData().getLong("lunar_dread.gravity_until")>e.level().getGameTime();}
    public static boolean pending(ServerPlayer p){if(!(p.level() instanceof ServerLevel l)||l.dimension()!=Level.OVERWORLD)return false;for(var t:state(l).tides)if(l.getGameTime()<t.start+t.duration&&p.position().multiply(1,0,1).distanceToSqr(t.center.multiply(1,0,1))<=144)return true;return false;}
    public static boolean busy(ServerPlayer p){var d=p.getPersistentData();return attached(p)||pending(p)||d.getBoolean("lunar_dread.gravity_falling")&&!p.onGround()&&!p.isInWaterOrBubble()&&d.getLong("lunar_dread.gravity_recovery")>p.level().getGameTime();}
    public static Vec3 force(Entity e){
        if(!attached(e))return Vec3.ZERO;var d=e.getPersistentData();
        return new Vec3(d.getDouble("lunar_dread.gravity_x"),d.getDouble("lunar_dread.gravity_y"),d.getDouble("lunar_dread.gravity_z"));
    }
    static void attach(Entity e,Tide t,ServerLevel l){
        var d=e.getPersistentData();if(attached(e)||d.getLong("lunar_dread.gravity_cured")>l.getGameTime()||!e.isAlive()||e.isPassenger()||e.isVehicle())return;
        if(e instanceof ServerPlayer p&&(!LunarDread.player(p)||MoonIncidents.sensoryBusy(p)||d.getBoolean("lunar_dread.gravity_falling")||LivingMoon.targeted(p)))return;
        if(e instanceof LivingEntity&&(!(e instanceof ServerPlayer)&&!wild(e)||e.isInWaterOrBubble()||e.isInLava()))return;
        d.putUUID("lunar_dread.gravity_id",t.id);d.putLong("lunar_dread.gravity_start",t.start);d.putLong("lunar_dread.gravity_until",t.start+t.duration);
        d.putInt("lunar_dread.gravity_duration",t.duration);d.putDouble("lunar_dread.gravity_ceiling",Math.min(l.getMaxBuildHeight()-6,e.getY()+t.height));
        d.putDouble("lunar_dread.gravity_center_x",t.center.x);d.putDouble("lunar_dread.gravity_center_z",t.center.z);d.putDouble("lunar_dread.gravity_last_y",e.getY());
        d.putDouble("lunar_dread.gravity_speed",t.height/(double)Math.max(80,t.duration-140)*1.2);d.putBoolean("lunar_dread.gravity_original",e.isNoGravity());d.putBoolean("lunar_dread.gravity_owned",true);
        e.setNoGravity(true);d.putBoolean("lunar_dread.gravity_falling",false);
        if(e instanceof LivingEntity living)living.addEffect(new MobEffectInstance(MoonContent.WEIGHTLESS,Math.max(1,(int)(t.start+t.duration-l.getGameTime())),0,false,false));
    }
    public static void release(Entity e){
        var d=e.getPersistentData();boolean was=d.getBoolean("lunar_dread.gravity_owned");
        if(was)e.setNoGravity(d.getBoolean("lunar_dread.gravity_original"));
        d.remove("lunar_dread.gravity_until");d.remove("lunar_dread.gravity_owned");d.remove("lunar_dread.gravity_x");d.remove("lunar_dread.gravity_y");d.remove("lunar_dread.gravity_z");
        if(e instanceof LivingEntity living)living.removeEffect(MoonContent.WEIGHTLESS);
        if(was&&e instanceof ServerPlayer p){d.putBoolean("lunar_dread.gravity_falling",true);d.putLong("lunar_dread.gravity_recovery",e.level().getGameTime()+1600);MoonIncidents.protect(p,120);}
    }
    public static void cure(ServerPlayer p){release(p);p.getPersistentData().putLong("lunar_dread.gravity_cured",p.level().getGameTime()+120);}
    public static void hooked(Entity target){
        if(target.level().isClientSide)return;
        if(attached(target)||target.getPersistentData().getBoolean("lunar_dread.gravity_falling")){
            release(target);target.getPersistentData().putLong("lunar_dread.gravity_cured",target.level().getGameTime()+100);target.hurtMarked=true;
            if(target instanceof ServerPlayer p)MoonIncidents.send(p);
        }
    }
    public static boolean begin(ServerLevel l,ServerPlayer p,boolean strong,boolean near){
        var s=state(l);if(s.tides.size()>=2||!LunarDread.player(p)||MoonIncidents.busy(p)||LivingMoon.targeted(p))return false;
        var t=new Tide();t.center=p.position();t.start=l.getGameTime();t.height=near?60:strong?48:18+l.random.nextInt(7);t.near=near;t.duration=strong?500:420;
        if(near&&!NearMoon.create(l,p,t.height)){t.near=false;t.height=strong?48:24;}
        s.tides.add(t);s.setDirty();
        if(MoonCycle.kind(l)==MoonCycle.GRAVITY&&MoonCycle.state(l).counts[MoonCycle.GRAVITY]>=2){
            var pos=SurvivalRules.surface(l,p.getBlockX()+32,p.getBlockZ());if(pos!=null){var mob=HorrorDirector.spawn(l,p,l.random.nextBoolean()?17:18,pos);if(mob!=null)mob.getPersistentData().putUUID("lunar_dread.gravity_anchor",t.id);}
        }
        for(var listener:l.players())if(listener.distanceToSqr(p)<48*48)listener.playNotifySound(SoundEvents.BEACON_AMBIENT,SoundSource.AMBIENT,.7f,.45f);
        return true;
    }
    private static void deploy(ServerLevel l,Mob mob){
        if(mob.getY()<=l.getMinBuildHeight()+4||mob.onGround()||wings(l).size()+AirRaid.wingCount(l)>=6||l.random.nextFloat()>.7)return;
        int style=mob instanceof Sheep?1:mob instanceof Goat?2:mob instanceof Chicken?3:mob instanceof net.minecraft.world.entity.monster.Zombie||mob instanceof net.minecraft.world.entity.monster.AbstractSkeleton?0:-1;
        if(style<0)return;var d=mob.getPersistentData();d.putInt("lunar_dread.wing_style",style);d.putFloat("lunar_dread.wing_hp",6);d.putLong("lunar_dread.wing_until",l.getGameTime()+800);
        var canopy=MoonContent.CANOPY.get().create(l);if(canopy!=null){canopy.owner(mob);canopy.setPos(mob.getX(),mob.getY()+mob.getBbHeight()+.5,mob.getZ());l.addFreshEntity(canopy);}
        l.playSound(null,mob.blockPosition(),SoundEvents.ENDER_DRAGON_FLAP,SoundSource.HOSTILE,.6f,1.4f);
    }
    public static List<MoonUpdates.Wing> wings(ServerLevel l){
        var result=new ArrayList<MoonUpdates.Wing>();
        for(var entity:l.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()&&mob.getPersistentData().getLong("lunar_dread.wing_until")>l.getGameTime()){
            result.add(new MoonUpdates.Wing(mob.getId(),mob.getPersistentData().getInt("lunar_dread.wing_style")));if(result.size()==6)break;
        }return List.copyOf(result);
    }
    public static List<MoonUpdates.Tumble> tumbles(ServerLevel l){
        var result=new ArrayList<MoonUpdates.Tumble>();
        // Players first: crowded animal pens cannot consume all remote-player pose slots.
        for(var p:l.players())if(attached(p))result.add(new MoonUpdates.Tumble(p.getId(),p.getPersistentData().getLong("lunar_dread.gravity_start"),p.getPersistentData().getInt("lunar_dread.gravity_duration")));
        for(var e:l.getAllEntities())if(!(e instanceof ServerPlayer)&&e instanceof LivingEntity&&attached(e)&&result.size()<32)result.add(new MoonUpdates.Tumble(e.getId(),e.getPersistentData().getLong("lunar_dread.gravity_start"),e.getPersistentData().getInt("lunar_dread.gravity_duration")));
        return List.copyOf(result.subList(0,Math.min(32,result.size())));
    }
    @SubscribeEvent public void motion(EntityTickEvent.Pre e){
        if(!(e.getEntity().level() instanceof ServerLevel l))return;Entity entity=e.getEntity();var d=entity.getPersistentData();long now=l.getGameTime();
        if(d.getBoolean("lunar_dread.gravity_owned")){
            boolean stopped=!MoonCycle.night(l)||d.getLong("lunar_dread.gravity_until")<=now||entity instanceof LivingEntity living&&!living.hasEffect(MoonContent.WEIGHTLESS)
                ||entity.isInWaterOrBubble()||entity.isInLava()||entity.isPassenger()
                ||Math.pow(entity.getX()-d.getDouble("lunar_dread.gravity_center_x"),2)+Math.pow(entity.getZ()-d.getDouble("lunar_dread.gravity_center_z"),2)>169;
            if(stopped){boolean natural=d.getLong("lunar_dread.gravity_until")<=now;release(entity);if(natural&&entity instanceof Mob mob)deploy(l,mob);}
            else{
                double age=now-d.getLong("lunar_dread.gravity_start"),duration=d.getInt("lunar_dread.gravity_duration");
                double strength=Math.min(1,Math.max(.12,(age-60)/80));
                double speed=d.getDouble("lunar_dread.gravity_speed")*strength;
                double remaining=d.getDouble("lunar_dread.gravity_ceiling")-entity.getY();
                if(remaining<6)speed=Math.min(speed,Math.max(0,remaining)*.025);
                // ServerPlayer velocity is not updated by position packets. Measure actual travel;
                // resending its stale velocity every five ticks would cancel client inertia.
                double velocity=entity instanceof ServerPlayer?entity.getY()-d.getDouble("lunar_dread.gravity_last_y"):entity.getDeltaMovement().y;
                d.putDouble("lunar_dread.gravity_last_y",entity.getY());
                double vertical=Math.clamp((speed-velocity)*.065,-.025,.008);
                if(age>duration-40)vertical=-.0015;
                double angle=(now+entity.getId()*31)*.018;
                d.putDouble("lunar_dread.gravity_x",Math.sin(angle)*.0006);d.putDouble("lunar_dread.gravity_y",vertical);d.putDouble("lunar_dread.gravity_z",Math.cos(angle)*.0006);
                var shell=NearMoon.center(l);
                if(shell!=null&&entity.getY()>shell.getY()+3){var side=Vec3.atCenterOf(shell).subtract(entity.position()).multiply(1,0,1).normalize().scale(.0012);d.putDouble("lunar_dread.gravity_x",side.x);d.putDouble("lunar_dread.gravity_z",side.z);}
                if(entity instanceof ItemEntity){Vec3 v=entity.getDeltaMovement().scale(.997/.98).add(force(entity));entity.setDeltaMovement(v);entity.hurtMarked=true;}
                if(entity instanceof ServerPlayer p&&now%5==0)MoonIncidents.send(p);
                if(now%8==0)l.sendParticles(ParticleTypes.REVERSE_PORTAL,entity.getX(),entity.getY()+.3,entity.getZ(),1,.2,.4,.2,.015);
            }
        }
        if(entity instanceof ServerPlayer p&&d.getBoolean("lunar_dread.gravity_falling")&&(p.onGround()||p.isInWaterOrBubble()||!p.isAlive())){d.putBoolean("lunar_dread.gravity_falling",false);MoonIncidents.protect(p,120);}
        if(entity instanceof Mob mob&&d.getLong("lunar_dread.wing_until")>now){
            if(mob.onGround()||d.getFloat("lunar_dread.wing_hp")<=0){d.remove("lunar_dread.wing_until");return;}
            Vec3 v=mob.getDeltaMovement();if(v.y<-.065){mob.setDeltaMovement(v.x,-.065,v.z);mob.fallDistance=0;mob.hurtMarked=true;}
            if(d.getInt("lunar_dread.wing_style")==2&&mob.getTarget()!=null){Vec3 toward=mob.getTarget().position().subtract(mob.position()).multiply(1,0,1).normalize().scale(.08);mob.setDeltaMovement(mob.getDeltaMovement().multiply(.95,1,.95).add(toward.scale(.05)));}
        }
    }
    @SubscribeEvent public void loaded(EntityJoinLevelEvent e){
        if(e.getLevel() instanceof ServerLevel l&&e.getEntity().getPersistentData().getBoolean("lunar_dread.gravity_owned")&&e.getEntity().getPersistentData().getLong("lunar_dread.gravity_until")<=l.getGameTime())release(e.getEntity());
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        var l=e.getServer().overworld();var s=state(l);long now=l.getGameTime();
        if(!MoonCycle.night(l)){if(!s.tides.isEmpty())clear(l);return;}
        var it=s.tides.iterator();while(it.hasNext()){
            var t=it.next();long age=now-t.start;
            if(age>=t.duration){it.remove();s.setDirty();continue;}
            if(age==t.duration-40)for(var p:l.players())if(p.distanceToSqr(t.center)<80*80)p.playNotifySound(SoundEvents.LEASH_KNOT_BREAK,SoundSource.AMBIENT,1,.5f);
            if(now%10!=0)continue;
            l.sendParticles(ParticleTypes.END_ROD,t.center.x,t.center.y+.3,t.center.z,12,9,.3,9,.035);
            if(!t.items){int n=0;for(var item:l.getEntitiesOfClass(ItemEntity.class,new AABB(t.center.add(-12,-4,-12),t.center.add(12,12,12)))){attach(item,t,l);if(++n==24)break;}t.items=true;s.setDirty();}
            if(age<60)continue;
            for(var p:l.players())if(p.position().multiply(1,0,1).distanceToSqr(t.center.multiply(1,0,1))<=144&&p.getY()>t.center.y-5&&p.getY()<t.center.y+t.height+4)attach(p,t,l);
            if(!t.mobs){int count=0;for(var mob:l.getEntitiesOfClass(Mob.class,new AABB(t.center.add(-12,-4,-12),t.center.add(12,16,12)),m->(m instanceof Enemy||m instanceof Animal)&&wild(m))){if(++count>8)break;attach(mob,t,l);}t.mobs=true;s.setDirty();}
        }
        var moon=MoonCycle.state(l);boolean full=MoonCycle.kind(l)==MoonCycle.GRAVITY,overlay=MoonCycle.kind(l)==MoonCycle.BLOOD&&moon.overlay==4;
        if((full||overlay)&&now>=s.next&&now-moon.nightAt>=1200){
            s.next=now+(overlay?24000:2600);s.setDirty();
            var players=l.players().stream().filter(p->LunarDread.player(p)&&!MoonIncidents.busy(p)).toList();
            if(!players.isEmpty()){
                var p=players.get(l.random.nextInt(players.size()));int seen=moon.counts[MoonCycle.GRAVITY];
                begin(l,p,seen>=2&&l.random.nextBoolean(),seen>=2&&l.random.nextInt(100)<18);
            }
        }
    }
    public static void clear(ServerLevel l){for(var e:l.getAllEntities())if(e.getPersistentData().getBoolean("lunar_dread.gravity_owned"))release(e);var s=state(l);s.tides.clear();s.next=0;s.setDirty();}
    @SubscribeEvent public void commands(RegisterCommandsEvent e){e.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("moon").requires(s->s.hasPermission(2))
        .then(Commands.literal("lift").executes(c->{int n=0;for(var p:c.getSource().getServer().overworld().players())if(begin(p.serverLevel(),p,true,false))n++;return n;}))
        .then(Commands.literal("contact").executes(c->{int n=0;for(var p:c.getSource().getServer().overworld().players())if(begin(p.serverLevel(),p,true,true))n++;return n;}))));}
}
