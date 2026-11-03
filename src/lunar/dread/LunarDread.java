package lunar.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Lunar Dread server rules for integrated and dedicated hosts. */
@Mod("lunar_dread")
public final class LunarDread {
    public LunarDread(net.neoforged.bus.api.IEventBus modBus, net.neoforged.fml.ModContainer container) {
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.SERVER, LunarDreadConfig.SERVER_SPEC);
        container.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT, LunarDreadConfig.CLIENT_SPEC);
        MoonContent.register(modBus);
        modBus.addListener(MoonNetwork::register);
        modBus.addListener(MoonUpdates::register);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new LunarDreadCommands());
        NeoForge.EVENT_BUS.register(new LunarCommands());
        NeoForge.EVENT_BUS.register(new GuideBook());
        NeoForge.EVENT_BUS.register(new SurvivalRules());
        NeoForge.EVENT_BUS.register(new ThreatSenses());
        NeoForge.EVENT_BUS.register(new AirRaid());
        NeoForge.EVENT_BUS.register(new LivingMoon());
        NeoForge.EVENT_BUS.register(new MoonDread());
        NeoForge.EVENT_BUS.register(new MoonCycle());
        NeoForge.EVENT_BUS.register(new MoonIncidents());
        NeoForge.EVENT_BUS.register(new MoonGravity());
        NeoForge.EVENT_BUS.register(new MoonAnomalies());
        NeoForge.EVENT_BUS.register(new NearMoon());
        NeoForge.EVENT_BUS.register(new SupplyDrops());
        NeoForge.EVENT_BUS.register(new HorrorCueDirector());
        NeoForge.EVENT_BUS.register(new AbductionController());
        NeoForge.EVENT_BUS.register(new BloodHuntDirector());
        NeoForge.EVENT_BUS.register(new HorrorDirector());
        NeoForge.EVENT_BUS.register(new MobProgression());
        NeoForge.EVENT_BUS.register(new RecoveryNight());
        NeoForge.EVENT_BUS.register(new BlockRecovery());
        ZbbRecoveryBridge.register();
    }

    private static boolean ally(Entity e) {
        if (e instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()) return false;
        if (e instanceof net.minecraft.world.entity.animal.horse.AbstractHorse horse && horse.isTamed()) return false;
        return e instanceof Enemy || e instanceof Animal || e instanceof Goat || e instanceof Sheep
            || e instanceof Cow || e instanceof Pig || e instanceof Chicken
            || e instanceof Rabbit || e instanceof Bat || e instanceof Squid
            || e instanceof net.minecraft.world.entity.animal.horse.Llama
            || e != null && (e.getTags().contains("lunar_dread.boss")
                || e.getTags().contains("lunar_dread.animal_minion")
                || e.getTags().contains("lunar_dread.wave"));
    }

    static boolean player(LivingEntity e) {
        return e instanceof ServerPlayer p && p.isAlive() && !p.isCreative() && !p.isSpectator();
    }

    static ServerPlayer nearest(Mob mob, double range) {
        ServerPlayer best = null;
        double distance = range * range;
        for (ServerPlayer p : ((ServerLevel) mob.level()).players()) {
            if (!player(p)) continue;
            double candidate = mob.distanceToSqr(p);
            if (candidate < distance) { best = p; distance = candidate; }
        }
        return best;
    }

    static int score(ServerLevel level, String name) {
        Scoreboard board = level.getScoreboard();
        var objective = board.getObjective("lunar_dread.sys");
        if (objective == null) return 0;
        var value = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), objective);
        return value == null ? 0 : value.value();
    }

    @SubscribeEvent
    public void join(EntityJoinLevelEvent event) { AnimalCombat.join(event); }

    @SubscribeEvent
    public void target(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide || !ally(mob)) return;
        LivingEntity target = event.getNewAboutToBeSetTarget();
        if (mob instanceof Animal && mob.level() instanceof ServerLevel level && target != null) {
            if (SurvivalRules.recoveryNight(level)) { event.setNewAboutToBeSetTarget(null); return; }
            var data = mob.getPersistentData();
            long now = level.getGameTime();
            if (now < data.getLong("lunar_dread.aggro_cooldown")) { event.setNewAboutToBeSetTarget(null); return; }
            if (DifficultyRules.safe(SurvivalRules.day(level)) && (!(target instanceof ServerPlayer player)
                || !data.hasUUID("lunar_dread.retaliate_target")
                || !data.getUUID("lunar_dread.retaliate_target").equals(player.getUUID())
                || now >= data.getLong("lunar_dread.retaliate_until"))) {
                event.setNewAboutToBeSetTarget(null); return;
            }
        }
        if (target != null && ThreatSenses.baited(mob)&&!BloodHuntDirector.assigned(mob)) { event.setNewAboutToBeSetTarget(null); return; }
        if (target != null && !player(target)) event.setNewAboutToBeSetTarget(nearest(mob, 128));
    }

    @SubscribeEvent
    public void friendlyFire(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide || !ally(event.getEntity())) return;
        Entity source = event.getSource().getEntity();
        Entity direct = event.getSource().getDirectEntity();
        boolean hostileTnt = direct instanceof PrimedTnt &&
            (direct.getTags().contains("lunar_dread.sheep_tnt") || direct.getTags().contains("lunar_dread.hostile_tnt"));
        if (ally(source) || hostileTnt) event.setCanceled(true);
    }

    @SubscribeEvent
    public void goatHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Goat goat) || !(goat.level() instanceof ServerLevel level)
            || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
            || !player(attacker) || event.getNewDamage() <= 0 || score(level, "#safe_active") == 1
            || SurvivalRules.recoveryNight(level)
            || !DifficultyRules.unlocked(SurvivalRules.day(level), 7)) return;
        long now = level.getGameTime();
        var data = goat.getPersistentData();
        if (now < data.getLong("lunar_dread.trap_until")) return;
        data.putLong("lunar_dread.trap_until", now + 600);
        // This event has the actual victim, including ranged and killing hits.
        // No nearest-HurtTime heuristic and no advancement reward race.
        AnimalCombat.goatTrap(goat, level);
    }

    @SubscribeEvent
    public void animalHurt(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Animal)
            || !(event.getSource().getEntity() instanceof ServerPlayer attacker)
            || event.getNewDamage() <= 0) return;
        AnimalCombat.retaliate(mob, attacker);
    }

    @SubscribeEvent
    public void tick(EntityTickEvent.Post event) {
        if (event.getEntity().level() instanceof ServerLevel serverLevel)
            AnimalCombat.entityTick(event.getEntity(), serverLevel);
        if (!(event.getEntity() instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) return;
        if (!ally(mob)) return;
        if (MoonDread.staring(mob)) { MoonDread.hold(mob); return; }
        if (mob.getPersistentData().getInt("lunar_dread.skill_windup") > 0) {
            animalWindup(mob, level);
            return;
        }
        AnimalCombat.tick(mob, level);
        if (mob.getPersistentData().getInt("lunar_dread.skill_windup") > 0) return;
        if (mob.tickCount % 20 == 0 && mob.getTarget() != null && !player(mob.getTarget())) {
            mob.setTarget(nearest(mob, 128));
            mob.setLastHurtByMob(null);
        }
        if (!(mob instanceof Goat goat) || goat.isNoAi()) return;
        if (goat.getPersistentData().contains("lunar_dread.melee_phase")) return;
        if (score(level, "#safe_active") == 1 || score(level, "#finished") == 1
            || !goat.getPersistentData().getBoolean("lunar_dread.pursuing")
            || DifficultyRules.safe(SurvivalRules.day(level))) {
            goat.getPersistentData().putInt("lunar_dread.charge_ticks", 0);
            goat.getPersistentData().putInt("lunar_dread.windup", 0);
            goat.removeTag("lunar_dread.ramming");
            if (goat.removeTag("lunar_dread.preparing")) level.broadcastEntityEvent(goat, (byte) 59);
            goat.setTarget(null);
            return;
        }
        var data = goat.getPersistentData();
        ServerPlayer target = goat.getTarget() instanceof ServerPlayer p && player(p) ? p : null;
        if (target == null) {
            data.putInt("lunar_dread.windup", 0);
            data.putInt("lunar_dread.charge_ticks", 0);
            boolean preparing = goat.removeTag("lunar_dread.preparing");
            goat.removeTag("lunar_dread.ramming");
            if (preparing) level.broadcastEntityEvent(goat, (byte) 59);
            return;
        }
        boolean blood = score(level, "#blood_active") == 1;
        int day = SurvivalRules.day(level);
        if (DifficultyRules.unlocked(day, 7) && data.getInt("lunar_dread.windup") == 0
            && goat.distanceToSqr(target) < 32 * 32) HuntDigging.tick(goat,target,true);

        int charge = data.getInt("lunar_dread.charge_ticks");
        int windup = data.getInt("lunar_dread.windup");
        if (charge > 0) {
            data.putInt("lunar_dread.charge_ticks", charge - 1);
            Vec3 direction = new Vec3(data.getDouble("lunar_dread.ram_x"), 0, data.getDouble("lunar_dread.ram_z"));
            goat.setDeltaMovement(direction.scale(1.15).add(0, goat.onGround() ? 0.18 : goat.getDeltaMovement().y, 0));
            if (goat.distanceToSqr(target) < 2.5 * 2.5) {
                target.hurt(level.damageSources().mobAttack(goat), (float)(4 * DifficultyRules.animalDamageScale(day)));
                Vec3 away = target.position().subtract(goat.position()).multiply(1, 0, 1).normalize();
                target.setDeltaMovement(away.scale(0.9).add(0, 0.45, 0));
                target.hurtMarked = true;
                data.putInt("lunar_dread.charge_ticks", 0);
                goat.removeTag("lunar_dread.ramming");
                level.playSound(null, goat.blockPosition(), SoundEvents.GOAT_RAM_IMPACT, SoundSource.HOSTILE, 1.2f, 0.7f);
            } else if (charge == 1) goat.removeTag("lunar_dread.ramming");
        } else if (windup > 0) {
            goat.getNavigation().stop();
            goat.setDeltaMovement(0, goat.getDeltaMovement().y, 0);
            goat.getLookControl().setLookAt(target, 45, 45);
            if (windup % 3 == 0) level.sendParticles(ParticleTypes.SMOKE,
                goat.getX(), goat.getEyeY() + 0.3, goat.getZ(), 3, 0.15, 0.1, 0.15, 0.015);
            data.putInt("lunar_dread.windup", windup - 1);
            if (windup == 1) {
                goat.removeTag("lunar_dread.preparing");
                level.broadcastEntityEvent(goat, (byte) 59);
                Vec3 direction = target.position().subtract(goat.position()).multiply(1, 0, 1).normalize();
                data.putDouble("lunar_dread.ram_x", direction.x);
                data.putDouble("lunar_dread.ram_z", direction.z);
                data.putInt("lunar_dread.charge_ticks", 16);
                goat.addTag("lunar_dread.ramming");
            }
        } else if (level.getGameTime() >= data.getLong("lunar_dread.ram_until")
                   && goat.distanceToSqr(target) >= 3 * 3 && goat.distanceToSqr(target) <= 16 * 16
                   && goat.hasLineOfSight(target)) {
            data.putLong("lunar_dread.ram_until", level.getGameTime() + 160);
            data.putInt("lunar_dread.windup", 20);
            goat.addTag("lunar_dread.preparing");
            level.broadcastEntityEvent(goat, (byte) 58);
            level.playSound(null, goat.blockPosition(), SoundEvents.GOAT_PREPARE_RAM, SoundSource.HOSTILE, 1.4f, 0.65f);
        }
    }

    private static void animalWindup(Mob mob, ServerLevel level) {
        var data = mob.getPersistentData();
        int remaining = data.getInt("lunar_dread.skill_windup");
        ServerPlayer target = mob.getTarget() instanceof ServerPlayer p && player(p) ? p : null;
        if (score(level, "#safe_active") == 1 && !DifficultyRules.safe(SurvivalRules.day(level))
            || target == null || score(level, "#finished") == 1
            || !mob.getPersistentData().getBoolean("lunar_dread.pursuing")) {
            data.putInt("lunar_dread.skill_windup", 0);
            data.remove("lunar_dread.windup_action");
            mob.removeTag("lunar_dread.preparing");
            return;
        }
        String action = data.getString("lunar_dread.windup_action");
        mob.addTag("lunar_dread.preparing");
        mob.getNavigation().stop();
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        if (mob instanceof Chicken) mob.getLookControl().setLookAt(mob.getX() + 0.5, mob.getY() - 0.3, mob.getZ());
        else mob.getLookControl().setLookAt(target, 30, 30);
        double particleY = mob instanceof Chicken || mob instanceof Rabbit || mob instanceof Pig
            ? mob.getY() + 0.1 : mob.getEyeY();
        if (remaining % 4 == 0) level.sendParticles(
            mob instanceof Squid ? ParticleTypes.SQUID_INK : mob instanceof Goat ? ParticleTypes.SMOKE : ParticleTypes.POOF,
            mob.getX(), particleY, mob.getZ(), 2, 0.12, 0.1, 0.12, 0.01);
        if (mob instanceof Sheep && remaining == 24) level.broadcastEntityEvent(mob, (byte) 10);
        data.putInt("lunar_dread.skill_windup", remaining - 1);
        if (remaining != 1) return;
        data.remove("lunar_dread.windup_action");
        mob.removeTag("lunar_dread.preparing");
        AnimalCombat.release(mob, level, target, action);
    }

}
