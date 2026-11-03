package lunar.dread;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/** Moon events with original actors, telegraphs and shared budgets. */
public final class MoonAnomalies {
    static final class Event {
        UUID id=UUID.randomUUID(),player;int kind,age,triggerAge=-1;Vec3 origin,forward;boolean released,rewarded;final Set<UUID> hits=new HashSet<>();final List<UUID> actors=new ArrayList<>();final List<Vec3> replay=new ArrayList<>();
    }
    public static final class State extends SavedData {
        long next;int day=-1,rewards;boolean overlay;final Set<UUID> claimed=new HashSet<>();
        static State load(CompoundTag t,HolderLookup.Provider r){var s=new State();s.next=t.getLong("next");s.day=t.getInt("day");s.overlay=t.getBoolean("overlay");s.rewards=t.getInt("rewards");for(var v:t.getList("claimed",8))try{s.claimed.add(UUID.fromString(v.getAsString()));}catch(IllegalArgumentException ignored){}return s;}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider r){t.putLong("next",next);t.putInt("day",day);t.putBoolean("overlay",overlay);t.putInt("rewards",rewards);var list=new net.minecraft.nbt.ListTag();for(var id:claimed)list.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));t.put("claimed",list);return t;}
    }
    private static final List<Event> events=new ArrayList<>();
    private static final Map<UUID,ArrayDeque<Vec3>> trails=new HashMap<>();
    static State state(ServerLevel l){return l.getDataStorage().computeIfAbsent(new SavedData.Factory<>(State::new,State::load,null),"lunar_dread_lunar_events");}
    private static Vector3f color(int kind){return switch(kind){case MoonCycle.PURPLE->new Vector3f(.6f,.12f,.9f);case MoonCycle.PALE->new Vector3f(.8f,.83f,.72f);case MoonCycle.MIRROR->new Vector3f(.38f,.47f,.55f);case MoonCycle.AMBER->new Vector3f(.95f,.52f,.05f);case MoonCycle.TIDE->new Vector3f(.05f,.6f,.6f);case MoonCycle.RUST->new Vector3f(.75f,.28f,.08f);case MoonCycle.ASH->new Vector3f(.9f,.3f,.07f);default->new Vector3f(.7f,.43f,.57f);};}
    private static void ring(ServerLevel l,Vec3 center,double radius,int kind){var dust=new DustParticleOptions(color(kind),1.2f);for(int i=0;i<40;i++){double a=i*Math.PI/20;l.sendParticles(dust,center.x+Math.cos(a)*radius,center.y+.12,center.z+Math.sin(a)*radius,1,0,0,0,0);}}
    static Mob spawn(ServerLevel l,Event event,EntityType<? extends Mob> type,Vec3 desired,String role){
        int total=0,lunar=0;for(var e:l.getAllEntities())if(e instanceof Enemy){total++;if(e.getTags().contains("lunar_dread.lunar"))lunar++;}
        if(lunar>=8||total>=Math.max(28,LunarDread.score(l,"#target")))return null;
        BlockPos pos=SurvivalRules.surface(l,(int)Math.floor(desired.x),(int)Math.floor(desired.z));if(pos==null||Math.abs(pos.getY()-event.origin.y)>12)return null;
        var mob=type.create(l);if(mob==null)return null;mob.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);if(!l.noCollision(mob)||l.players().stream().anyMatch(p->LunarDread.player(p)&&p.distanceToSqr(mob)<144))return null;
        mob.addTag("lunar_dread.lunar");mob.addTag("lunar_dread.wave");mob.getPersistentData().putLong("lunar_dread.expires",l.getGameTime()+1800);mob.getPersistentData().putUUID("lunar_dread.lunar_event",event.id);
        mob.getPersistentData().putString("lunar_dread.lunar_role",role);mob.setPersistenceRequired();
        int day=SurvivalRules.day(l);
        if(mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)!=null){mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(1.3*DifficultyRules.zombieHealth(day)*(LunarDread.score(l,"#blood_active")==1?DifficultyRules.bloodHealthScale(day):1));mob.setHealth(mob.getMaxHealth());}
        if(mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)!=null)mob.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).setBaseValue(1.3*DifficultyRules.meleeDamage(day)*(LunarDread.score(l,"#blood_active")==1?DifficultyRules.bloodDamageScale(day):1));
        var player=l.getServer().getPlayerList().getPlayer(event.player);if(player!=null)mob.setTarget(player);
        if(!l.addFreshEntity(mob))return null;event.actors.add(mob.getUUID());return mob;
    }
    static boolean begin(ServerLevel l,ServerPlayer p,int kind){
        if(events.size()>=2||MoonIncidents.busy(p)||LivingMoon.targeted(p))return false;
        var v=new Event();v.player=p.getUUID();v.kind=kind;v.origin=p.position().add(p.getLookAngle().multiply(1,0,1).normalize().scale(kind==MoonCycle.AMBER?0:18));
        var surface=SurvivalRules.surface(l,(int)Math.floor(v.origin.x),(int)Math.floor(v.origin.z));if(surface==null)return false;v.origin=Vec3.atBottomCenterOf(surface);
        v.forward=p.position().subtract(v.origin).multiply(1,0,1).normalize();
        var path=trails.get(p.getUUID());if(path!=null)v.replay.addAll(path);
        if(kind==MoonCycle.AMBER){v.origin=path!=null&&!path.isEmpty()?path.getFirst():p.position();}
        events.add(v);MoonIncidents.protect(p,kind==MoonCycle.TIDE?520:400);
        p.playNotifySound(kind==MoonCycle.PAPER?SoundEvents.BOOK_PAGE_TURN:kind==MoonCycle.PALE?SoundEvents.WARDEN_LISTENING:SoundEvents.AMETHYST_BLOCK_RESONATE,SoundSource.AMBIENT,.7f,.55f);
        return true;
    }
    private static void reward(ServerLevel l,Event event,Vec3 at){
        var s=state(l);if(event.rewarded||s.claimed.contains(event.id)||s.rewards>=8)return;event.rewarded=true;s.claimed.add(event.id);s.rewards+=2;s.setDirty();
        int relic=switch(event.kind){case MoonCycle.PURPLE->0;case MoonCycle.PALE->1;case MoonCycle.MIRROR->2;case MoonCycle.AMBER->3;case MoonCycle.TIDE->4;case MoonCycle.RUST->5;default->6;};
        l.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(l,at.x,at.y+.4,at.z,new ItemStack(MoonContent.relic(relic),2)));
        var p=l.getServer().getPlayerList().getPlayer(event.player);if(p!=null)p.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.NEUTRAL,.6f,1.4f);
    }
    private static boolean exposed(ServerPlayer p,Event event){return LunarDread.player(p)&&!MoonGravity.busy(p)&&!MoonIncidents.black(p)&&(p.getUUID().equals(event.player)||!MoonIncidents.busy(p));}
    private static void advance(ServerLevel l,Event v){
        var p=l.getServer().getPlayerList().getPlayer(v.player);if(p==null||p.level()!=l||!LunarDread.player(p)){v.age=1000;return;}
        int age=v.age++;boolean free=!MoonGravity.busy(p)&&!MoonIncidents.black(p);
        if(age%8==0)ring(l,v.origin,v.kind==MoonCycle.PURPLE?6:v.kind==MoonCycle.AMBER?2.5:1.4,v.kind);
        if(v.kind==MoonCycle.PURPLE){
            if(age==60){var core=spawn(l,v,MoonContent.horror(5).get(),v.origin,"rift_core");if(core!=null){core.setGlowingTag(true);core.setNoAi(true);}spawn(l,v,MoonContent.horror(6).get(),v.origin.add(3,0,0),"stitched");}
            if(age>60&&age<400&&age%40==0)l.playSound(null,BlockPos.containing(v.origin),SoundEvents.WARDEN_LISTENING,SoundSource.HOSTILE,.5f,.5f);
            if(age>80&&age<400&&free&&age%20==0&&p.position().distanceToSqr(v.origin)<36){
                // The pocket has a physical centre and an exit boundary, not a dimension teleport.
                Vec3 away=p.position().subtract(v.origin).multiply(1,0,1).normalize();p.push(away.x*.18,0,away.z*.18);p.hurtMarked=true;
                l.sendParticles(ParticleTypes.PORTAL,v.origin.x,v.origin.y+2,v.origin.z,20,5,2,5,.1);
            }
        }else if(v.kind==MoonCycle.PALE){
            if(age==60){spawn(l,v,MoonContent.horror(7).get(),v.origin,"listener");spawn(l,v,MoonContent.horror(8).get(),v.origin.add(3,0,2),"hollow");}
            if(age>60&&age<400&&age%40==0){boolean noisy=p.getPersistentData().getInt("lunar_dread.noise")>=4||p.isSprinting();
                if(noisy)for(var id:v.actors){var e=l.getEntity(id);if(e instanceof Mob mob){mob.setNoAi(false);mob.setTarget(p);mob.getNavigation().moveTo(p,1.3);}}
                else for(var id:v.actors){var e=l.getEntity(id);if(e instanceof Mob mob){mob.setTarget(null);mob.setNoAi(true);mob.getNavigation().stop();}}
            }
        }else if(v.kind==MoonCycle.MIRROR){
            if(age==60){var mirror=spawn(l,v,MoonContent.horror(9).get(),v.origin,"reflection");if(mirror!=null){mirror.setNoAi(true);((HorrorMob)mirror).mirror(p.getUUID());}}
            if(age>=60&&age<220)for(var id:v.actors)if(l.getEntity(id) instanceof Mob mob){
                mob.getLookControl().setLookAt(p);Vec3 goal=v.replay.isEmpty()?v.origin.subtract(p.position().subtract(v.origin).scale(.2)):v.replay.get(Math.min(v.replay.size()-1,(age-60)/8));
                Vec3 motion=goal.subtract(mob.position());if(motion.lengthSqr()>0.01)mob.setDeltaMovement(motion.normalize().scale(.12));}
            if(age==220)for(var id:v.actors)if(l.getEntity(id) instanceof Mob mob){mob.setNoAi(false);mob.setTarget(p);}
        }else if(v.kind==MoonCycle.AMBER){
            if(age==40)l.playSound(null,BlockPos.containing(v.origin),SoundEvents.NOTE_BLOCK_BELL.value(),SoundSource.HOSTILE,.8f,.65f);
            if(age==60&&free){for(var target:l.players())if(exposed(target,v)&&target.position().distanceToSqr(v.origin)<6.25)target.hurt(l.damageSources().magic(),6);}
            if(age==65){spawn(l,v,MoonContent.horror(10).get(),v.origin.add(6,0,0),"late_shadow");}
            if((age==120||age==180)&&!v.replay.isEmpty()){v.origin=v.replay.get(Math.min(v.replay.size()-1,(age-60)/6));}
            if((age==140||age==200)&&free&&p.position().distanceToSqr(v.origin)<6.25)p.hurt(l.damageSources().magic(),4);
        }else if(v.kind==MoonCycle.TIDE){
            Vec3 center=v.origin.add(v.forward.scale(Math.max(0,age-60)*.065));
            var hit=l.clip(new ClipContext(v.origin.add(0,.5,0),center.add(0,.5,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
            if(hit.getType()!=HitResult.Type.MISS){v.age=700;return;}
            if(age>60&&age<440){if(age%4==0)ring(l,center,3,v.kind);
                if(free)for(var target:l.players())if(exposed(target,v)&&target.position().distanceToSqr(center)<9&&v.hits.add(target.getUUID())){
                    target.push(v.forward.x*.6,.18,v.forward.z*.6);target.hurtMarked=true;target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40,0));
                }
            }
            if(age==60){spawn(l,v,MoonContent.horror(11).get(),v.origin,"tide_walker");spawn(l,v,MoonContent.horror(12).get(),v.origin.add(4,0,0),"chorus");}
        }else if(v.kind==MoonCycle.RUST){
            if(age==60){var magnet=spawn(l,v,MoonContent.horror(13).get(),v.origin,"magnet");if(magnet!=null)magnet.setNoAi(true);spawn(l,v,MoonContent.horror(14).get(),v.origin.add(4,0,0),"porter");}
            if(age==100||age==180){v.origin=p.position();l.playSound(null,p.blockPosition(),SoundEvents.RESPAWN_ANCHOR_CHARGE,SoundSource.HOSTILE,.65f,1.3f);}
            if(age>=100&&age<140||age>=180&&age<220){for(var id:v.actors)if(l.getEntity(id) instanceof HorrorMob core&&core.kind()==13){
                Vec3 from=core.getEyePosition(),to=v.origin.add(0,.4,0);var clip=l.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(clip.getType()!=HitResult.Type.MISS)to=clip.getLocation();Vec3 delta=to.subtract(from);int pieces=Math.min(32,(int)delta.length());
                if(age%4==0)for(int i=0;i<=pieces;i++){Vec3 at=from.add(delta.scale(i/(double)Math.max(1,pieces)));l.sendParticles(ParticleTypes.ELECTRIC_SPARK,at.x,at.y,at.z,1,0,0,0,0);}
            }}
            if((age==140||age==220)&&free&&p.position().distanceToSqr(v.origin)<4){
                for(var id:v.actors)if(l.getEntity(id) instanceof HorrorMob core&&core.kind()==13&&core.hasLineOfSight(p)){
                    p.hurt(l.damageSources().magic(),4);break;
                }
            }
        }else if(v.kind==MoonCycle.PAPER){
            if(age==60){
                var paper=EntityType.BLOCK_DISPLAY.create(l);if(paper!=null){var tag=new CompoundTag();var block=new CompoundTag();block.putString("Name","minecraft:chest");tag.put("block_state",block);paper.load(tag);paper.setPos(v.origin.x-.5,v.origin.y,v.origin.z-.5);paper.addTag("lunar_dread.lunar_paper");paper.getPersistentData().putLong("lunar_dread.expires",l.getGameTime()+600);l.addFreshEntity(paper);v.actors.add(paper.getUUID());}
                var exit=EntityType.BLOCK_DISPLAY.create(l);if(exit!=null){var tag=new CompoundTag();var block=new CompoundTag();block.putString("Name","minecraft:iron_door");tag.put("block_state",block);exit.load(tag);exit.setPos(v.origin.x+3,v.origin.y,v.origin.z);exit.addTag("lunar_dread.lunar_paper");exit.getPersistentData().putLong("lunar_dread.expires",l.getGameTime()+600);l.addFreshEntity(exit);v.actors.add(exit.getUUID());}
                var walker=spawn(l,v,MoonContent.horror(16).get(),v.origin.add(-2,0,0),"backstage");if(walker!=null)walker.setNoAi(true);
            }
            if(age>60&&age%5==0)l.sendParticles(ParticleTypes.ASH,v.origin.x,v.origin.y+1,v.origin.z,6,.55,.4,.55,.02);
            if(age>60&&!v.released&&p.position().distanceToSqr(v.origin)<4){
                v.released=true;v.triggerAge=age;
                for(var id:v.actors){var actor=l.getEntity(id);if(actor instanceof net.minecraft.world.entity.Display)actor.discard();}
                var hand=spawn(l,v,MoonContent.horror(15).get(),v.origin.add(2,0,0),"backstage");if(hand!=null)hand.setNoAi(true);
                p.playNotifySound(SoundEvents.BOOK_PAGE_TURN,SoundSource.HOSTILE,1,.5f);
            }
            if(v.released&&age==v.triggerAge+40)for(var id:v.actors)if(l.getEntity(id) instanceof Mob mob){mob.setNoAi(false);mob.setTarget(p);}
        }else if(v.kind==MoonCycle.ASH){
            // Three marked cinder falls; standing under the ring is the player's choice.
            if(age==60)spawn(l,v,EntityType.BLAZE,v.origin,"cinder_sentry");
            if(age==80||age==160||age==240){
                var spot=SurvivalRules.surface(l,p.getBlockX(),p.getBlockZ());
                if(spot!=null)v.origin=Vec3.atBottomCenterOf(spot);
                l.playSound(null,BlockPos.containing(v.origin),SoundEvents.FIRECHARGE_USE,SoundSource.HOSTILE,.75f,.65f);
            }
            if((age>=80&&age<100)||(age>=160&&age<180)||(age>=240&&age<260)){
                if(age%4==0){ring(l,v.origin,3,v.kind);l.sendParticles(ParticleTypes.ASH,v.origin.x,v.origin.y+4,v.origin.z,8,2,1,2,.03);}
            }
            if(age==100||age==180||age==260){
                l.sendParticles(ParticleTypes.FLAME,v.origin.x,v.origin.y+.5,v.origin.z,34,2,1,2,.08);
                if(free)for(var target:l.players())if(exposed(target,v)&&target.position().distanceToSqr(v.origin)<9&&
                    l.clip(new ClipContext(v.origin.add(0,2,0),target.getEyePosition(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,target)).getType()==HitResult.Type.MISS){
                    target.hurt(l.damageSources().magic(),3);target.setRemainingFireTicks(40);
                }
            }
        }
        if(age==400&&v.kind==MoonCycle.PALE&&p.getPersistentData().getInt("lunar_dread.noise")<4){if(!p.hasEffect(MobEffects.NIGHT_VISION))p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION,220,0,false,false));reward(l,v,p.position());}
    }
    @SubscribeEvent public void death(LivingDeathEvent e){if(e.getEntity().level() instanceof ServerLevel l&&e.getEntity().getPersistentData().hasUUID("lunar_dread.lunar_event")){
        UUID id=e.getEntity().getPersistentData().getUUID("lunar_dread.lunar_event");for(var v:events)if(v.id.equals(id)){
            String role=e.getEntity().getPersistentData().getString("lunar_dread.lunar_role");
            boolean core=role.equals("rift_core")||role.equals("magnet")||role.equals("reflection")||role.equals("late_shadow")||role.equals("chorus")||role.equals("backstage");
            if(core){reward(l,v,e.getEntity().position());v.age=700;for(var actor:v.actors)if(!actor.equals(e.getEntity().getUUID())&&l.getEntity(actor)!=null)l.getEntity(actor).discard();
                if(v.kind==MoonCycle.PAPER)for(var p:l.players())net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new MoonUpdates.Navigation(0));}
        }
    }}
    public static void clear(ServerLevel l){events.clear();trails.clear();for(var e:l.getAllEntities())if(e.getTags().contains("lunar_dread.lunar")||e.getTags().contains("lunar_dread.lunar_paper"))e.discard();}
    @SubscribeEvent public void stopped(ServerStoppedEvent e){events.clear();trails.clear();}
    @SubscribeEvent public void loaded(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
        // In-progress scenes cancel across a process restart. Saved reward IDs/cooldowns remain authoritative.
        if(e.loadedFromDisk()&&!e.getLevel().isClientSide&&(e.getEntity().getPersistentData().hasUUID("lunar_dread.lunar_event")||e.getEntity().getTags().contains("lunar_dread.lunar_paper")))e.getEntity().discard();
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        var l=e.getServer().overworld();if(!MoonCycle.night(l)||MoonCycle.peaceful(l)){if(!events.isEmpty())clear(l);return;}
        for(var p:l.players()){var path=trails.computeIfAbsent(p.getUUID(),id->new ArrayDeque<>());if(l.getGameTime()%4==0){path.addLast(p.position());while(path.size()>20)path.removeFirst();}}
        trails.keySet().removeIf(id->l.getServer().getPlayerList().getPlayer(id)==null);
        for(var v:events)advance(l,v);events.removeIf(v->{if(v.age<650)return false;for(var id:v.actors){var actor=l.getEntity(id);if(actor instanceof net.minecraft.world.entity.Display)actor.discard();}return true;});
        var s=state(l);var moon=MoonCycle.state(l);int day=SurvivalRules.day(l);if(s.day!=day){s.day=day;s.overlay=false;s.rewards=0;s.claimed.clear();s.next=l.getGameTime()+1200;s.setDirty();}
        if(l.getGameTime()<s.next)return;int k=MoonCycle.kind(l);
        if(k==MoonCycle.BLOOD){if(s.overlay)return;k=switch(moon.overlay){case 1->MoonCycle.PURPLE;case 2->MoonCycle.MIRROR;case 3->MoonCycle.AMBER;case 5->MoonCycle.PAPER;default->0;};}
        if(k<7||k==MoonCycle.GRAVITY)return;
        var candidates=l.players().stream().filter(p->LunarDread.player(p)&&!MoonIncidents.busy(p)&&!LivingMoon.targeted(p)).toList();
        if(!candidates.isEmpty()&&begin(l,candidates.get(l.random.nextInt(candidates.size())),k)){s.overlay=MoonCycle.kind(l)==MoonCycle.BLOOD;s.next=l.getGameTime()+1800;s.setDirty();}
    }
}
