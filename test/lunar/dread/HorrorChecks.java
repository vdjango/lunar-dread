package lunar.dread;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native entity/riding, fishing, damage, excavation protection and persistent probability tests. */
public final class HorrorChecks {
    private static int n;
    private static void check(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);n++;}
    private static void unlock()throws Exception{var f=MoonIncidents.class.getDeclaredField("states");f.setAccessible(true);((java.util.Map<?,?>)f.get(null)).clear();}
    @SuppressWarnings("unchecked") public static void run(ServerPlayer fixture)throws Exception{
        n=0;var l=fixture.serverLevel();
        // NeoForge FakePlayer deliberately forbids riding; exercise the actual ServerPlayer instead.
        var p=new ServerPlayer(fixture.server,l,new GameProfile(UUID.randomUUID(),"CarrySubject"),net.minecraft.server.level.ClientInformation.createDefault());p.connection=fixture.connection;
        var inv=ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");inv.setAccessible(true);inv.setInt(p,0);
        var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");field.setAccessible(true);var online=(java.util.Map<UUID,ServerPlayer>)field.get(p.server.getPlayerList());online.put(p.getUUID(),p);
        var mobs=new java.util.ArrayList<HorrorMob>();var now=l.getGameTime();var pos=new BlockPos(260,100,260);p.moveTo(260.5,100,260.5);
        var grief=l.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);boolean originalGrief=grief.get();grief.set(true,l.getServer());
        var rescuer=new FakePlayer(l,new GameProfile(UUID.randomUUID(),"HorrorRescuer"));rescuer.moveTo(254,100,260.5);
        try{
            check(BloodHuntDirector.threshold(1,1)==3000&&BloodHuntDirector.threshold(100,20)==1500,"Hunt countdown must accelerate and retain a seventy-five-second floor");
            check(BloodFogProfile.range(0,0).far()==32&&BloodFogProfile.range(1,0).far()==24&&BloodFogProfile.range(1,1).far()==7,"Blood fog must retain distinct surface and deep ranges");
            check(BloodFogProfile.range(0,.08f).far()==28&&BloodFogProfile.range(1,.5f).far()==14,"Wooden shelter and shallow cave need their own continuous fog ranges");
            check(MoonFogProfile.intensity(13000)==0&&MoonFogProfile.intensity(18000)==1&&MoonFogProfile.intensity(24000)==0,
                "Every lunar fog must grow after dusk, peak near midnight and clear by dawn");
            check(MoonCycle.IDS.length==16&&MoonCycle.NAMES.length==16&&LunarCommands.EVENTS.contains("anomaly.ash"),
                "All sixteen phases must have names and the ash moon must expose its event command");
            check(MoonFogProfile.intensity(15000)<MoonFogProfile.intensity(17000)&&MoonFogProfile.intensity(22000)<MoonFogProfile.intensity(20000),
                "Night fog must rise and fall rather than appearing at full strength instantly");
            check(Math.abs(MoonFogProfile.range(MoonCycle.BLOOD,0,1,0,128,7,1).far()-7)<.01f
                &&MoonFogProfile.range(MoonCycle.BLOOD,1,1,0,128,7,1).far()<4,
                "Peak blood fog must obscure the road at seven blocks and be denser underground");
            for(int phase=0;phase<MoonCycle.IDS.length;phase++)
                check(MoonFogProfile.range(phase,0,1,0,128,7,1).far()<128,"Every moon phase needs its own ground fog");
            MoonGravity.clear(l);p.getPersistentData().putBoolean("lunar_dread.gravity_falling",false);p.stopRiding();
            var m=MoonContent.SNATCHER.get().create(l);m.moveTo(261,100,260.5);m.setNoAi(true);l.addFreshEntity(m);mobs.add(m);unlock();
            check(AbductionController.capture(m,p)&&p.getVehicle()==m&&m.phase()==2,"Grab must use a real native carrier; busy="+MoonIncidents.busy(p)+" los="+m.hasLineOfSight(p)+" available="+AbductionController.available(l)+" passenger="+p.isPassenger());
            var pose=net.minecraft.world.entity.player.Player.class.getDeclaredMethod("updatePlayerPose");pose.setAccessible(true);pose.invoke(p);p.refreshDimensions();
            check(p.getPose()==Pose.SWIMMING&&p.getBbHeight()<.7,"Carried player must have compact native pose and collision");
            var second=MoonContent.SNATCHER.get().create(l);second.moveTo(262,100,260.5);l.addFreshEntity(second);mobs.add(second);
            check(!AbductionController.available(l)&&!AbductionController.capture(second,rescuer),"Only one player may be carried globally");
            check(MoonIncidents.busy(p)&&!MoonIncidents.blink(p,true),"Capture must exclude overlapping blackout and gravity");
            p.setDeltaMovement(.1,.05,0);var hook=new FishingHook(rescuer,l,0,0);hook.setPos(p.position());var pull=FishingHook.class.getDeclaredMethod("pullEntity",Entity.class);pull.setAccessible(true);pull.invoke(hook,p);
            check(!p.isPassenger()&&p.getDeltaMovement().x<-.5,"Native fishing pull must release a captive and preserve its rescue momentum");hook.discard();
            check(MoonIncidents.busy(p),"Released captive needs a strong-control grace window");
            unlock();m.moveTo(p.getX()+1,p.getY(),p.getZ());check(AbductionController.capture(m,p),"Capture can resume after grace");
            MoonIncidents.cure(p);check(!p.isPassenger()&&AbductionController.available(l),"Milk must release a native captive and the global reservation");
            unlock();m.moveTo(p.getX()+1,p.getY(),p.getZ());check(AbductionController.capture(m,p),"Prepare teammate interruption");m.invulnerableTime=0;m.hurt(l.damageSources().playerAttack(rescuer),4);
            check(!p.isPassenger(),"Actual teammate damage must break the carry");
            unlock();m.moveTo(p.getX()+1,p.getY(),p.getZ());check(AbductionController.capture(m,p),"Prepare captive native attack");
            var ticker=net.minecraft.world.entity.LivingEntity.class.getDeclaredField("attackStrengthTicker");ticker.setAccessible(true);ticker.setInt(p,100);
            var attack=p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);double old=attack.getBaseValue();attack.setBaseValue(16);m.invulnerableTime=0;p.attack(m);attack.setBaseValue(old);
            check(!p.isPassenger(),"A genuine fully cooled player attack exceeding twelve actual damage must free the captive");
            unlock();m.moveTo(p.getX()+1,p.getY(),p.getZ());m.setHealth(m.getMaxHealth());check(AbductionController.capture(m,p),"Prepare emergency dismount");p.stopRiding();new AbductionController().tick(new ServerTickEvent.Post(()->true,p.server));
            check(!p.isPassenger()&&AbductionController.available(l)&&MoonIncidents.busy(p),"Vanilla dismount must not remount or retain a stale reservation");
            var wall=pos.offset(6,0,0);l.setBlockAndUpdate(wall,Blocks.STONE.defaultBlockState());check(HuntDigging.breakable(l,wall),"Ordinary stone is excavatable");
            l.setBlockAndUpdate(wall,Blocks.CHEST.defaultBlockState());check(!HuntDigging.breakable(l,wall),"Excavation must preserve containers");
            l.setBlockAndUpdate(wall,Blocks.BEDROCK.defaultBlockState());check(!HuntDigging.breakable(l,wall),"Excavation must preserve unbreakable blocks");l.setBlockAndUpdate(wall,Blocks.AIR.defaultBlockState());
            // Exercise the actual delayed block destruction and normal drop path.
            unlock();var dig=MoonContent.DIGGER.get().create(l);dig.moveTo(270.5,100,260.5);dig.setNoAi(true);dig.tickCount=5;l.addFreshEntity(dig);mobs.add(dig);p.moveTo(266.5,100,260.5);
            var stone=new BlockPos(269,100,260);l.setBlockAndUpdate(stone,Blocks.DIRT.defaultBlockState());HuntDigging.tick(dig,p,false);
            check(dig.getPersistentData().contains("lunar_dread.dig_pos")&&!l.getBlockState(stone).isAir(),"Dig must begin with a real visible windup, not instant removal; permitted="+HuntDigging.permitted(l,dig,stone)+" rule="+l.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)+" grief="+net.neoforged.neoforge.event.EventHooks.canEntityGrief(l,dig)+" entity="+l.getBlockState(stone).canEntityDestroy(l,stone,dig)+" data="+dig.getPersistentData());
            dig.getPersistentData().putLong("lunar_dread.dig_start",l.getGameTime()-31);HuntDigging.tick(dig,p,false);
            check(l.getBlockState(stone).isAir()&&!l.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(stone).inflate(1),item->!item.getItem().isEmpty()).isEmpty(),"Finished excavation must destroy the actual wall and retain normal item drops");
            var record=MoonDiscovery.record(p);record.putInt("sequence",7);var saved=MoonDiscovery.State.load(MoonDiscovery.state(l).save(new net.minecraft.nbt.CompoundTag(),l.registryAccess()),l.registryAccess());
            check(saved.players.getCompound(p.getUUID().toString()).getInt("sequence")==7,"Discovery trial sequence must survive world save/load");
            System.out.println("HORROR_NATIVE_CARRY_FISH_MILK_REAL_ATTACK_DISMOUNT_FOG_DIG_PERSIST_OK checks="+n);
        }finally{grief.set(originalGrief,l.getServer());AbductionController.release(p,false);online.remove(p.getUUID());for(var m:mobs)m.discard();rescuer.discard();p.discard();unlock();}
    }
}
