package lunar.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Elytra interception uses real mounted mobs and real arrows/potions. */
public final class AirRaid {
    static int wingCount(ServerLevel level) {
        int count=level.getEntities(EntityType.PHANTOM, e -> e.getTags().contains("lunar_dread.air_mount")).size();
        for(var e:level.getAllEntities())if(e instanceof HorrorMob h&&h.kind()==4)count++;return count;
    }
    static int spawn(ServerLevel level, ServerPlayer target) {
        return spawn(level,target,false);
    }
    static int spawn(ServerLevel level, ServerPlayer target,boolean forced) {
        int available = 6 - wingCount(level) - MoonGravity.wings(level).size();
        int day=SurvivalRules.day(level);
        if (available <= 0 || (!forced&&!DifficultyRules.unlocked(day,6)) || !AnimalCombat.enabled(level)) return 0;
        int size = Math.min(available, LunarDread.score(level, "#blood_active") == 1 ? 3 : DifficultyRules.unlocked(day,13) ? 2 : 1);
        int spawned = 0;
        for (int i = 0; i < size; i++) {
            double angle = target.getRandom().nextDouble() * Math.PI * 2;
            Vec3 pos = target.position().add(Math.cos(angle) * 38, 10 + i * 3, Math.sin(angle) * 38);
            if (pos.y > level.getMaxBuildHeight() - 4) pos = new Vec3(pos.x, level.getMaxBuildHeight() - 4, pos.z);
            if (!level.hasChunkAt(BlockPos.containing(pos)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(pos))) continue;
            Phantom mount = new Phantom(EntityType.PHANTOM, level);
            mount.moveTo(pos.x, pos.y, pos.z); mount.setPhantomSize(1);
            if (!level.noCollision(mount) || level.players().stream().anyMatch(p -> LunarDread.player(p) && p.distanceToSqr(mount) < 1024)) continue;
            mount.addTag("lunar_dread.air_mount"); mount.addTag("lunar_dread.wave");
            mount.getPersistentData().putUUID("lunar_dread.air_target", target.getUUID());
            mount.getPersistentData().putLong("lunar_dread.expires", level.getGameTime() + 1800);
            if (!level.addFreshEntity(mount)) continue;
            Mob rider;
            if (DifficultyRules.unlocked(day,13) && (i + LunarDread.score(level, "#blood_count")) % 2 == 0) rider = new Witch(EntityType.WITCH, level);
            else {
                rider = new Skeleton(EntityType.SKELETON, level);
                rider.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                rider.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                rider.setDropChance(EquipmentSlot.HEAD, 0);
            }
            rider.moveTo(pos.x, pos.y + 1, pos.z); rider.addTag("lunar_dread.air_rider"); rider.addTag("lunar_dread.wave");
            rider.getPersistentData().putLong("lunar_dread.expires", level.getGameTime() + 1800);
            rider.setTarget(target); mount.setTarget(target);
            if (level.addFreshEntity(rider) && rider.startRiding(mount, true)) ++spawned;
            else { rider.discard(); mount.discard(); }
        }
        return spawned;
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 != 0) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (!LunarDread.player(player)) continue;
            var level = player.serverLevel();
            if (!DifficultyRules.unlocked(SurvivalRules.day(level),6) || !AnimalCombat.enabled(level)
                || level.dimension() != Level.OVERWORLD || SurvivalRules.grace(level)) continue;
            CompoundTag d = player.getPersistentData();
            if(MoonGravity.busy(player)||MoonIncidents.black(player)){
                d.putInt("lunar_dread.air_seconds",0);d.remove("lunar_dread.air_at");continue;
            }
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, player.getBlockX(), player.getBlockZ());
            boolean airborne = player.isFallFlying() || !player.onGround() && !player.isInWater()
                && player.getY() > surface + 12;
            int seconds = airborne ? d.getInt("lunar_dread.air_seconds") + 1 : 0;
            d.putInt("lunar_dread.air_seconds", seconds);
            long now = level.getGameTime();
            if (seconds >= 3 && now >= d.getLong("lunar_dread.air_ready") && wingCount(level) < 6
                && d.getLong("lunar_dread.air_at") == 0) {
                d.putLong("lunar_dread.air_at", now + 60);
                player.connection.send(new ClientboundSetTitleTextPacket(Component.literal("上面也有东西")));
                player.sendSystemMessage(Component.literal("三秒后，空中拦截。留意尖啸和侧面的影子。"));
                level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_AMBIENT, SoundSource.HOSTILE, 1, 0.65f);
            }
            if (d.getLong("lunar_dread.air_at") > 0 && now >= d.getLong("lunar_dread.air_at")) {
                d.remove("lunar_dread.air_at");
                if (airborne) spawn(level, player);
                d.putLong("lunar_dread.air_ready", now + (LunarDread.score(level, "#blood_active") == 1 ? 900 : 1500));
            }
        }
    }
    @SubscribeEvent public void fly(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Phantom mount) || !(mount.level() instanceof ServerLevel level)
            || !mount.getTags().contains("lunar_dread.air_mount")) return;
        var d = mount.getPersistentData();
        ServerPlayer target = d.hasUUID("lunar_dread.air_target") ? level.getServer().getPlayerList().getPlayer(d.getUUID("lunar_dread.air_target")) : null;
        if (target == null || target.level() != level || !LunarDread.player(target) || !AnimalCombat.enabled(level)) {
            mount.getPassengers().forEach(e -> e.discard()); mount.discard(); return;
        }
        mount.clearFire(); mount.setTarget(target);
        Vec3 predict = target.getEyePosition().add(target.getDeltaMovement().scale(8)).add(0, 4, 0);
        Vec3 offset = predict.subtract(mount.position());
        double speed = Math.min(1.8, 0.9 + SurvivalRules.day(level) * 0.009);
        Vec3 desired = offset.lengthSqr() > 64 ? offset.normalize().scale(speed)
            : new Vec3(-offset.z, 0.08, offset.x).normalize().scale(0.45);
        mount.setDeltaMovement(mount.getDeltaMovement().scale(0.6).add(desired.scale(0.4)));
        mount.hurtMarked = true;
        if (mount.tickCount % 50 != 0 || mount.distanceToSqr(target) > 48 * 48 || !mount.hasLineOfSight(target)) return;
        for (var passenger : mount.getPassengers()) if (passenger instanceof Mob rider) {
            rider.setTarget(target); rider.getLookControl().setLookAt(target, 45, 45);
            if (rider instanceof Witch witch && rider.distanceToSqr(target) <= 20 * 20) witch.performRangedAttack(target, 1);
            else if (rider instanceof Skeleton skeleton) skeleton.performRangedAttack(target, 1);
        }
    }
}
