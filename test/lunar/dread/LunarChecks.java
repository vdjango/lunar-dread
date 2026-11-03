package lunar.dread;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Runs against real patched Minecraft classes, collisions, mixins, and native fishing pull. */
public final class LunarChecks {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new IllegalStateException(message);checks++;}
    private static void reset(ServerPlayer p){
        MoonIncidents.clearWorld(p.serverLevel());MoonAnomalies.clear(p.serverLevel());MoonIncidents.menu(p,false);
        p.getPersistentData().putLong("lunar_dread.gravity_cured",0);p.getPersistentData().putBoolean("lunar_dread.gravity_falling",false);
        try{var f=MoonIncidents.class.getDeclaredField("states");f.setAccessible(true);((java.util.Map<?,?>)f.get(null)).clear();}catch(Exception e){throw new IllegalStateException(e);}
        p.removeAllEffects();p.setDeltaMovement(Vec3.ZERO);p.setGameMode(GameType.SURVIVAL);p.setHealth(20);
    }
    public static void run(ServerPlayer p)throws Exception{
        checks=0;var l=p.serverLevel();long oldTime=l.getDayTime();var at=new BlockPos(260,100,260);var roof=at.above(3);var originalRoof=l.getBlockState(roof);
        var cycle=MoonCycle.state(l);var oldCycle=cycle.save(new CompoundTag(),l.registryAccess());
        var savedDrops=new java.util.ArrayList<>(SupplyDrops.state(l).drops);SupplyDrops.state(l).drops.clear();
        var items=new java.util.ArrayList<Entity>();
        try{
            var bookFactory=GuideBook.class.getDeclaredMethod("book",boolean.class);bookFactory.setAccessible(true);
            var survivalBook=(net.minecraft.world.item.ItemStack)bookFactory.invoke(null,false);
            var moonBook=(net.minecraft.world.item.ItemStack)bookFactory.invoke(null,true);
            check(survivalBook.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages().size()==14,
                "The original survival handbook must remain fourteen pages");
            check(moonBook.get(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT).pages().size()==10
                &&moonBook.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getBoolean("lunar_dread.moon_codex"),
                "The independent colored moon codex must be a real marked written book");
            reset(p);l.setDayTime(498000);MoonCycle.score(l,"#day",21);MoonCycle.score(l,"#safe_active",0);MoonCycle.score(l,"#finished",0);
            cycle.day=21;cycle.kind=MoonCycle.GRAVITY;cycle.forced=true;cycle.applied=true;cycle.nightAt=l.getGameTime();
            p.moveTo(Vec3.atBottomCenterOf(at));p.setOnGround(false);l.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());
            var tide=new MoonGravity.Tide();tide.center=p.position();tide.start=l.getGameTime()-100;tide.height=24;
            MoonGravity.attach(p,tide,l);
            check(MoonGravity.attached(p)&&p.isNoGravity(),"Indoors must lose gravity rather than receive roof immunity");
            new MoonGravity().motion(new EntityTickEvent.Pre(p));
            var update=net.minecraft.world.entity.player.Player.class.getDeclaredMethod("updatePlayerPose");update.setAccessible(true);update.invoke(p);p.refreshDimensions();
            check(p.getPose()==Pose.SWIMMING&&p.getBbHeight()<.7,"Native player pose and hitbox must lie down");
            check(!p.getAbilities().flying&&!p.getAbilities().mayfly&&!p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION),"Gravity must not enable flight or levitation");
            double start=p.getY();p.travel(Vec3.ZERO);check(p.getY()>start,"Real travel mixin must accelerate indoors");
            p.setPos(260.5,102.35,260.5);p.setDeltaMovement(0,.3,0);p.travel(Vec3.ZERO);
            check(p.getY()+p.getBbHeight()<=103.001,"Tumbling hitbox must collide with solid roof");
            l.setBlockAndUpdate(roof,Blocks.AIR.defaultBlockState());p.setPos(260.5,105,260.5);p.setDeltaMovement(.12,.16,.03);
            Vec3 before=p.getDeltaMovement();MoonGravity.cure(p);
            check(!MoonGravity.attached(p)&&!p.isNoGravity()&&p.getDeltaMovement().equals(before),"Milk/release must preserve inertia and restore gravity");
            check(!p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING),"Released players must retain native fall risk");
            reset(p);p.setPos(260.5,106,260.5);tide.center=p.position();MoonGravity.attach(p,tide,l);p.setDeltaMovement(0,.12,0);
            var rescuer=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"LunarRescuer"));rescuer.setPos(252,100,260.5);
            var hook=new FishingHook(rescuer,l,0,0);hook.setPos(p.position());var pull=FishingHook.class.getDeclaredMethod("pullEntity",Entity.class);pull.setAccessible(true);pull.invoke(hook,p);
            check(!MoonGravity.attached(p)&&!p.isNoGravity(),"Actual native fishing pull must release the target");
            check(p.getDeltaMovement().x<-.7&&p.getDeltaMovement().y<0,"Fishing rescue must keep native pull toward the teammate, including downwards");
            check(p.getPersistentData().getLong("lunar_dread.gravity_cured")>l.getGameTime(),"Reeled player needs a short window to reach cover");
            hook.discard();rescuer.discard();
            var item=new ItemEntity(l,260,105,260,new ItemStack(Items.IRON_INGOT,7));l.addFreshEntity(item);items.add(item);item.tick();int age=item.getAge();
            tide.center=item.position();MoonGravity.attach(item,tide,l);new MoonGravity().motion(new EntityTickEvent.Pre(item));
            check(item.isNoGravity()&&item.getItem().getCount()==7&&item.getAge()==age,"Lifting real drops must preserve count and lifetime");MoonGravity.release(item);
            check(!item.isNoGravity(),"Item gravity must restore on release");
            var zombie=EntityType.ZOMBIE.create(l);zombie.setPos(265,110,260);l.addFreshEntity(zombie);items.add(zombie);
            zombie.getPersistentData().putFloat("lunar_dread.wing_hp",6);zombie.getPersistentData().putLong("lunar_dread.wing_until",l.getGameTime()+100);
            var canopy=MoonContent.CANOPY.get().create(l);canopy.owner(zombie);canopy.setPos(265,112,260);l.addFreshEntity(canopy);items.add(canopy);
            check(canopy.isPickable()&&canopy.hurt(l.damageSources().playerAttack(p),6)&&canopy.isRemoved()&&zombie.getPersistentData().getLong("lunar_dread.wing_until")==0,"Physical wing hitbox must be shootable and break its fall protection");
            reset(p);p.moveTo(Vec3.atBottomCenterOf(at));cycle.kind=MoonCycle.BLOOD;cycle.form=0;MoonCycle.score(l,"#blood_active",1);MoonCycle.score(l,"#blood_count",2);MoonCycle.score(l,"#moon_attacks",1);
            check(!MoonCycle.giant(l)&&!MoonCycle.ready(l),"Small blood moon must not fire giant-moon rays");
            check(MoonCycle.awaken(l,p)&&cycle.form==1&&!MoonCycle.ready(l),"Awakening must be world-wide with an actual attack grace window");
            reset(p);check(MoonIncidents.blink(p,true)&&p.hasEffect(MoonContent.BLACKOUT),"Blackout must have a real curable state");
            MoonIncidents.menu(p,true);check(!MoonIncidents.black(p)&&!p.hasEffect(MoonContent.BLACKOUT),"Opening menus must cancel blackout without hiding menus");
            reset(p);check(MoonIncidents.blink(p,true),"Blackout can start again in a clean fixture");p.removeAllEffects();new MoonIncidents().tick(new ServerTickEvent.Post(()->true,p.server));
            check(!MoonIncidents.black(p),"Removing effects with milk must cancel host blackout");
            reset(p);cycle.kind=MoonCycle.NORMAL;MoonCycle.score(l,"#blood_active",0);var dropPos=at.offset(10,0,0);l.setBlockAndUpdate(dropPos,Blocks.AIR.defaultBlockState());
            check(SupplyDrops.create(l,dropPos,1,false,null),"Supply guard mission must spawn on actual valid terrain");var drop=SupplyDrops.state(l).drops.getFirst();
            check(!(l.getBlockEntity(dropPos) instanceof ChestBlockEntity)&&!SupplyDrops.unlock(l,drop),"Guarded supply must not be lootable before the guard dies");
            var guard=l.getEntity(drop.guard);guard.hurt(l.damageSources().genericKill(),10000);check(drop.killed&&SupplyDrops.unlock(l,drop),"Own guard death must unlock exactly its own chest");
            var chest=(ChestBlockEntity)l.getBlockEntity(drop.pos);check(!chest.isEmpty()&&SupplyDrops.beacons(l).getFirst().status()==1,"Unlocked supply must retain guidance while loot remains");
            check(chest.getItem(0).is(Items.IRON_INGOT)&&chest.getItem(0).getCount()==8,"Blood supplies give materials rather than ready-made turrets");
            var pistol=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("musketmod","pistol"));
            var cartridges=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("musketmod","cartridge"));
            check(chest.getItem(6).is(pistol)&&chest.getItem(7).is(cartridges)&&chest.getItem(7).getCount()==12,
                "Early blood supply must contain a pistol and twelve matching cartridges");
            check(chest.getItem(5).is(Items.ARROW)&&chest.getItem(5).getCount()==24,"Blood supply must retain backup arrows");
            var persisted=SupplyDrops.State.load(SupplyDrops.state(l).save(new CompoundTag(),l.registryAccess()),l.registryAccess());
            check(persisted.drops.getFirst().guard.equals(drop.guard)&&persisted.drops.getFirst().status==1,"Supply ownership and unlocked guidance must survive save/load");
            chest.clearContent();SupplyDrops.fill(l,drop.pos,0,false);
            check(chest.getItem(6).is(Items.BOW)&&chest.getItem(5).is(Items.ARROW)&&chest.getItem(5).getCount()==16,
                "Early normal supply must contain a bow and arrows");
            long supplyDayTime=l.getDayTime();l.setDayTime(29*24000L+18000L);chest.clearContent();SupplyDrops.fill(l,drop.pos,0,false);
            check(chest.getItem(6).is(Items.CROSSBOW),"Later normal supply must upgrade to a crossbow");
            chest.clearContent();SupplyDrops.fill(l,drop.pos,1,false);
            var musket=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("musketmod","musket"));
            check(chest.getItem(6).is(musket)&&chest.getItem(7).is(cartridges)&&chest.getItem(7).getCount()==20,
                "Later blood supply must upgrade to a musket and twenty cartridges");
            l.setDayTime(supplyDayTime);chest.clearContent();new SupplyDrops().tick(new ServerTickEvent.Post(()->true,p.server));l.setBlockAndUpdate(drop.pos,Blocks.AIR.defaultBlockState());
            var starPos=at.offset(13,0,0);l.setBlockAndUpdate(starPos,Blocks.AIR.defaultBlockState());
            check(SupplyDrops.create(l,starPos,0,true,null),"Star cargo fixture needs a real mission");
            var star=SupplyDrops.state(l).drops.getLast();star.inAir=true;
            var cargo=MoonContent.CRATE.get().create(l);cargo.mission(star.id);star.crate=cargo.getUUID();cargo.moveTo(Vec3.atBottomCenterOf(starPos));l.addFreshEntity(cargo);items.add(cargo);
            l.setBlockAndUpdate(starPos,Blocks.GOLD_BLOCK.defaultBlockState());cargo.tick();
            check(!star.inAir&&cargo.isRemoved()&&l.getBlockState(starPos).is(Blocks.GOLD_BLOCK)&&l.getBlockEntity(star.pos) instanceof ChestBlockEntity starChest&&!starChest.isEmpty(),"Obstructed star drop must move its real loot safely without overwriting player blocks");
            var starChest=(ChestBlockEntity)l.getBlockEntity(star.pos);
            check(starChest.getItem(5).is(Items.BOW)&&starChest.getItem(6).is(Items.ARROW)&&starChest.getItem(6).getCount()==12,
                "Small unguarded star drop must contain only an entry-level bow and twelve arrows");
            l.setBlockAndUpdate(star.pos,Blocks.AIR.defaultBlockState());l.setBlockAndUpdate(starPos,Blocks.AIR.defaultBlockState());SupplyDrops.state(l).drops.remove(star);
            var copy=MoonCycle.State.load(cycle.save(new CompoundTag(),l.registryAccess()),l.registryAccess());check(copy.kind==cycle.kind&&copy.form==cycle.form&&copy.seed==cycle.seed,"Night choice/form must survive save/load");
            var drought=new MoonCycle.State();drought.dry=10;check(MoonCycle.choose(drought,87,new java.util.Random(1))>=1&&MoonCycle.choose(drought,87,new java.util.Random(1))<=5,"Long dry streak must produce a peaceful moon even late in the run");
            var snapshot=MoonCycle.snapshot(l);var buf=new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(),l.registryAccess());
            try{MoonUpdates.World.CODEC.encode(buf,snapshot);check(MoonUpdates.World.CODEC.decode(buf).equals(snapshot),"New world snapshot must roundtrip with bounded collections");}finally{buf.release();}
            reset(p);p.setPos(260.5,100,260.5);MoonCycle.score(l,"#is_blood",0);cycle.kind=MoonCycle.NORMAL;cycle.forced=false;cycle.applied=true;
            cycle.deathAt=l.getGameTime()+200;MoonCycle.score(l,"#is_blood",1);MoonCycle.ensure(l);
            check(cycle.kind==MoonCycle.NORMAL,"Death-triggered transition must retain the promised ten-second preparation window");
            cycle.deathAt=l.getGameTime();MoonCycle.ensure(l);check(cycle.kind==MoonCycle.BLOOD,"Death-triggered moon must take over after its window");
            reset(p);cycle.kind=MoonCycle.AMBER;MoonCycle.score(l,"#blood_active",0);
            check(MoonAnomalies.begin(l,p,MoonCycle.AMBER),"Amber event must reserve its target");
            var eventsField=MoonAnomalies.class.getDeclaredField("events");eventsField.setAccessible(true);
            @SuppressWarnings("unchecked")var events=(java.util.List<MoonAnomalies.Event>)eventsField.get(null);
            var amber=events.getFirst();amber.age=60;p.invulnerableTime=0;
            var advance=MoonAnomalies.class.getDeclaredMethod("advance",net.minecraft.server.level.ServerLevel.class,MoonAnomalies.Event.class);advance.setAccessible(true);advance.invoke(null,l,amber);
            check(p.getHealth()<20,"An event's own reservation must not accidentally cancel its telegraphed damage");
            reset(p);cycle.kind=MoonCycle.GRAVITY;var shellState=NearMoon.state(l);shellState.day=-1;
            check(NearMoon.create(l,p,60),"Near-moon landing surface must consist of actual collidable blocks");
            var shellCopy=NearMoon.State.load(shellState.save(new CompoundTag(),l.registryAccess()),l.registryAccess());
            check(shellCopy.center.equals(shellState.center)&&shellCopy.owned.size()==shellState.owned.size(),"Moon surface ownership must survive save/load");
            var replacement=BlockPos.of(shellState.owned.getFirst());l.setBlockAndUpdate(replacement,Blocks.GOLD_BLOCK.defaultBlockState());
            shellState.end=l.getGameTime();var cleanup=new NearMoon();for(int i=0;i<400&&shellState.center!=null;i++)cleanup.tick(new ServerTickEvent.Post(()->true,p.server));
            check(shellState.center==null&&shellState.owned.isEmpty()&&l.getBlockState(replacement).is(Blocks.GOLD_BLOCK),"Moon cleanup must remove its blocks but preserve a player's replacement");
            l.setBlockAndUpdate(replacement,Blocks.AIR.defaultBlockState());
            HorrorChecks.run(p);
            System.out.println("LUNAR_INDOOR_POSE_COLLISION_INERTIA_FISH_RESCUE_BLACKOUT_SUPPLY_PERSISTENCE_OK checks="+checks);
        }finally{
            reset(p);for(var e:items)e.discard();l.setBlockAndUpdate(roof,originalRoof);l.setDayTime(oldTime);
            SupplyDrops.state(l).drops.clear();SupplyDrops.state(l).drops.addAll(savedDrops);SupplyDrops.state(l).setDirty();
            var restored=MoonCycle.State.load(oldCycle,l.registryAccess());cycle.day=restored.day;cycle.kind=restored.kind;cycle.form=restored.form;cycle.overlay=restored.overlay;cycle.forced=restored.forced;cycle.applied=restored.applied;cycle.nightAt=restored.nightAt;cycle.awakenedAt=restored.awakenedAt;cycle.setDirty();
            MoonCycle.score(l,"#blood_active",0);MoonCycle.score(l,"#moon_attacks",0);
        }
    }
}
