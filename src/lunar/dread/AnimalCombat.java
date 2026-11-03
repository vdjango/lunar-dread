package lunar.dread;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/** Native animal AI and abilities. No /damage, command functions or proximity damage. */
public final class AnimalCombat {
    private AnimalCombat() {}
    static final int LLAMA_AGGRO_RANGE = 24;

    static boolean aggressive(Mob mob) {
        if (mob instanceof Llama) return false;
        if (mob instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()) return false;
        if (mob instanceof AbstractHorse horse && horse.isTamed()) return false;
        return mob instanceof Animal;
    }

    static boolean enabled(ServerLevel level) {
        return LunarDreadConfig.ANIMALS_ENABLED.get() && worldActive(level);
    }

    static boolean worldActive(ServerLevel level) {
        return LunarDread.score(level, "#day") > 0
            && !SurvivalRules.recoveryNight(level)
            && (DifficultyRules.safe(SurvivalRules.day(level)) || LunarDread.score(level, "#safe_active") != 1)
            && LunarDread.score(level, "#finished") != 1
            && level.getDifficulty() != net.minecraft.world.Difficulty.PEACEFUL;
    }

    static void retaliate(Mob mob, ServerPlayer attacker) {
        if (!(mob.level() instanceof ServerLevel level) || !enabled(level)
            || !aggressive(mob) && !(mob instanceof Llama)) return;
        var data = mob.getPersistentData();
        data.putUUID("lunar_dread.retaliate_target", attacker.getUUID());
        data.putLong("lunar_dread.retaliate_until", level.getGameTime() + 120);
        data.putLong("lunar_dread.aggro_cooldown", 0);
    }

    private static int searchRange(int day) {
        int week = DifficultyRules.week(day);
        return week <= 4 ? 16 : week <= 8 ? 18 : week <= 12 ? 20 : week <= 16 ? 22 : 24;
    }
    private static int chaseRange(int day) {
        int week = DifficultyRules.week(day);
        return week == 1 ? 16 : week <= 4 ? 28 : week <= 8 ? 30 : week <= 12 ? 32 : week <= 16 ? 34 : 36;
    }
    private static int chaseSeconds(int day) {
        int week = DifficultyRules.week(day);
        int base = week == 1 ? 6 : week <= 4 ? 8 : week <= 8 ? 9 : week <= 12 ? 10 : week <= 16 ? 11 : 12;
        return Math.max(2, (int)Math.round(base * LunarDreadConfig.ANIMAL_CHASE_SCALE.get()));
    }

    private static final class Pursuit {
        final Mob mob;
        ServerPlayer target;
        Vec3 origin = Vec3.ZERO;
        long started, lastSeen;
        Pursuit(Mob mob) { this.mob = mob; }
        boolean acquire() {
            ServerLevel level = (ServerLevel)mob.level();
            if (!enabled(level) || level.getGameTime() < mob.getPersistentData().getLong("lunar_dread.aggro_cooldown")) return false;
            int day = SurvivalRules.day(level);
            if (DifficultyRules.safe(day)) {
                if (!mob.getPersistentData().hasUUID("lunar_dread.retaliate_target")
                    || level.getGameTime() >= mob.getPersistentData().getLong("lunar_dread.retaliate_until")) return false;
                UUID id = mob.getPersistentData().getUUID("lunar_dread.retaliate_target");
                target = level.players().stream().filter(p -> p.getUUID().equals(id) && LunarDread.player(p)).findFirst().orElse(null);
            } else target = LunarDread.nearest(mob, searchRange(day));
            return target != null && mob.hasLineOfSight(target);
        }
        void start() {
            ServerLevel level = (ServerLevel)mob.level();
            started = lastSeen = level.getGameTime(); origin = mob.position();
            mob.getPersistentData().putBoolean("lunar_dread.pursuing", true);
            mob.setTarget(target);
        }
        boolean ongoing() {
            ServerLevel level = (ServerLevel)mob.level();
            int day = SurvivalRules.day(level), distance = chaseRange(day);
            long now = level.getGameTime();
            if (target == null || !LunarDread.player(target) || target.level() != level
                || !enabled(level) || now-started >= chaseSeconds(day)*20L
                || target.position().distanceToSqr(origin) > distance*distance
                || mob.distanceToSqr(target) > distance*distance) return false;
            if (DifficultyRules.safe(day) && now >= mob.getPersistentData().getLong("lunar_dread.retaliate_until")) return false;
            if (mob.hasLineOfSight(target)) lastSeen = now;
            return now-lastSeen < (DifficultyRules.safe(day) ? 3 : DifficultyRules.week(day) <= 8 ? 4 : DifficultyRules.week(day) <= 16 ? 5 : 6)*20L;
        }
        void stop() {
            mob.getPersistentData().putBoolean("lunar_dread.pursuing", false);
            mob.getPersistentData().putLong("lunar_dread.aggro_cooldown", ((ServerLevel)mob.level()).getGameTime()+200);
            mob.getNavigation().stop(); mob.setTarget(null); target = null;
        }
    }

    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || !(event.getEntity() instanceof Mob mob)) return;
        MoonDread.addWitnessGoal(mob);
        if (mob instanceof Llama llama && !llama.isTraderLlama()) {
            mob.goalSelector.addGoal(1, new SpitGoal(llama));
            return;
        }
        if (!aggressive(mob)) return;
        // Registered for both fresh spawns and saved animals, independently of data packs.
        mob.goalSelector.addGoal(1, new LungeGoal(mob));
    }

    private static final class SpitGoal extends Goal {
        final Llama llama;
        final Pursuit pursuit;
        SpitGoal(Llama llama) { this.llama = llama; pursuit = new Pursuit(llama); setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK)); }
        @Override public boolean canUse() {
            return !llama.isNoAi() && !llama.isTamed() && pursuit.acquire();
        }
        @Override public boolean canContinueToUse() {
            return !llama.isTamed() && pursuit.ongoing();
        }
        @Override public void start() { pursuit.start(); }
        @Override public void stop() { pursuit.stop(); }
        @Override public void tick() {
            ServerPlayer target = pursuit.target;
            llama.getLookControl().setLookAt(target, 30, 30);
            if (llama.getPersistentData().getInt("lunar_dread.skill_windup") > 0) { llama.getNavigation().stop(); return; }
            if (llama.distanceToSqr(target) > 144 || !llama.hasLineOfSight(target)) llama.getNavigation().moveTo(target, 1.15);
            else llama.getNavigation().stop();
        }
    }

    private static final class LungeGoal extends Goal {
        private final Mob mob;
        private final Pursuit pursuit;
        private int windup, lunge;
        private long ready;
        private Vec3 direction = Vec3.ZERO;
        LungeGoal(Mob mob) {
            this.mob = mob;
            this.pursuit = new Pursuit(mob);
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }
        @Override public boolean canUse() {
            return !mob.isNoAi() && aggressive(mob) && pursuit.acquire();
        }
        @Override public boolean canContinueToUse() {
            return aggressive(mob) && pursuit.ongoing();
        }
        @Override public boolean requiresUpdateEveryTick() { return true; }
        @Override public void start() { pursuit.start(); }
        @Override public void stop() {
            windup = lunge = 0;
            mob.getPersistentData().remove("lunar_dread.melee_phase");
            pursuit.stop();
        }
        @Override public void tick() {
            ServerLevel level = (ServerLevel) mob.level();
            ServerPlayer target = pursuit.target;
            var data = mob.getPersistentData();
            // Never overlap a head-down ram, fire breath or another skill.
            if (data.getInt("lunar_dread.skill_windup") > 0 || data.getInt("lunar_dread.windup") > 0
                || data.getInt("lunar_dread.charge_ticks") > 0) {
                windup = lunge = 0;
                data.remove("lunar_dread.melee_phase");
                mob.getNavigation().stop();
                return;
            }
            mob.getLookControl().setLookAt(target, 35, 35);
            if (windup > 0) {
                mob.getNavigation().stop();
                mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
                if (windup % 4 == 0) level.sendParticles(ParticleTypes.POOF,
                    mob.getX(), mob.getY() + 0.1, mob.getZ(), 2, 0.12, 0.05, 0.12, 0.01);
                if (--windup == 0) {
                    direction = target.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
                    lunge = 7;
                    data.putString("lunar_dread.melee_phase", "lunge");
                    mob.swing(InteractionHand.MAIN_HAND);
                    mob.setDeltaMovement(direction.scale(mob instanceof Chicken ? 0.65 : 0.5)
                        .add(0, mob instanceof Chicken ? 0.36 : 0.22, 0));
                    sound(mob, voice(mob), 0.8f, 1.4f);
                }
                return;
            }
            if (lunge > 0) {
                // A real collision window: moving away or an opaque wall makes it miss.
                if (mob.hasLineOfSight(target) && mob.getBoundingBox().inflate(0.3, 0.2, 0.3)
                    .intersects(target.getBoundingBox())) {
                    int day = SurvivalRules.day(level);
                    float damage = DifficultyRules.safe(day) ? 2 : (float)((mob instanceof Chicken ? 2
                        : mob instanceof Cow ? 4 : mob instanceof Goat ? 4 : mob instanceof Pig ? 3 : 3)
                        * DifficultyRules.animalDamageScale(day));
                    if (target.hurt(level.damageSources().mobAttack(mob), damage)) {
                        target.knockback(0.35, -direction.x, -direction.z);
                        target.hurtMarked = true;
                        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + 0.6,
                            target.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
                    }
                    lunge = 0;
                } else --lunge;
                if (lunge == 0) data.remove("lunar_dread.melee_phase");
                return;
            }
            if (mob.tickCount % 10 == 0) mob.getNavigation().moveTo(target, mob instanceof Chicken ? 1.45 : 1.2);
            double reach = mob.getBbWidth() * 0.5 + target.getBbWidth() * 0.5 + 1.4;
            if (level.getGameTime() >= ready && mob.distanceToSqr(target) <= reach * reach && mob.hasLineOfSight(target)) {
                windup = mob instanceof Chicken ? 12 : 16;
                ready = level.getGameTime() + 120;
                data.putString("lunar_dread.melee_phase", "windup");
                mob.getNavigation().stop();
                sound(mob, voice(mob), 0.55f, 0.75f);
            }
        }
    }

    private static SoundEvent voice(Mob mob) {
        if (mob instanceof Chicken) return SoundEvents.CHICKEN_AMBIENT;
        if (mob instanceof Cow) return SoundEvents.COW_AMBIENT;
        if (mob instanceof Pig) return SoundEvents.PIG_AMBIENT;
        if (mob instanceof Goat) return SoundEvents.GOAT_PREPARE_RAM;
        if (mob instanceof Llama) return SoundEvents.LLAMA_ANGRY;
        if (mob instanceof Wolf) return SoundEvents.WOLF_GROWL;
        if (mob instanceof Cat) return SoundEvents.CAT_HISS;
        if (mob instanceof Fox) return SoundEvents.FOX_SCREECH;
        if (mob instanceof Bee) return SoundEvents.BEE_LOOP_AGGRESSIVE;
        if (mob instanceof Panda) return SoundEvents.PANDA_PRE_SNEEZE;
        if (mob instanceof PolarBear) return SoundEvents.POLAR_BEAR_WARNING;
        if (mob instanceof AbstractHorse) return SoundEvents.HORSE_ANGRY;
        if (mob instanceof Frog) return SoundEvents.FROG_TONGUE;
        return SoundEvents.SHEEP_AMBIENT;
    }

    static void tick(Mob mob, ServerLevel level) {
        if (mob instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame()
            || mob instanceof AbstractHorse horse && horse.isTamed()) return;
        if (!enabled(level) || mob.isNoAi() || level.dimension() != Level.OVERWORLD
            || !mob.getPersistentData().getBoolean("lunar_dread.pursuing")
            || mob.tickCount % 10 != 0 || mob.getPersistentData().getInt("lunar_dread.skill_windup") > 0
            || mob.getPersistentData().contains("lunar_dread.melee_phase")) return;
        if (mob instanceof Goat && (mob.getPersistentData().getInt("lunar_dread.windup") > 0
            || mob.getPersistentData().getInt("lunar_dread.charge_ticks") > 0)) return;
        ServerPlayer target = mob.getTarget() instanceof ServerPlayer p && LunarDread.player(p) ? p : null;
        if (target == null || !mob.hasLineOfSight(target)) return;
        var data = mob.getPersistentData();
        long now = level.getGameTime();
        if (now < data.getLong("lunar_dread.ability_until")) return;
        int day = SurvivalRules.day(level);
        int week = DifficultyRules.week(day);
        if (week == 1) {
            if (mob instanceof Llama && mob.distanceToSqr(target) <= 144) begin(mob, "llama", 20);
            return;
        }
        int clock = (int) (level.getDayTime() % 24000);
        boolean night = clock >= 13000;
        boolean blood = LunarDread.score(level, "#blood_active") == 1;
        if (mob instanceof Llama llama && !llama.isTraderLlama()) {
            if (week < 6 || tntCount(level) <= 3) begin(mob, "llama", week < 6 ? 20 : 36);
            else data.putLong("lunar_dread.ability_until", now + 40);
            return;
        }
        if (week >= 4 && mob instanceof Goat && mob.distanceToSqr(target) <= 36) {
            begin(mob, "goat_fire", 30);
            return;
        }
        if (week >= 3 && mob instanceof Sheep && mob.distanceToSqr(target) >= 9) {
            if (tntCount(level) < 6) begin(mob, "sheep", 24);
            else data.putLong("lunar_dread.ability_until", now + 40);
            return;
        }
        if (week >= 11 && mob instanceof Wolf && night) { begin(mob, "wolf", 40); return; }
        if (week >= 5 && mob instanceof Fox && (clock >= 10000 || clock <= 2000)) { begin(mob, "fox", 20); return; }
        if (week >= 10 && mob instanceof Cat && night) { begin(mob, "cat", 30); return; }
        if (week >= 8 && mob instanceof Bee && !night && mob.distanceToSqr(target) <= 16) { begin(mob, "bee", 24); return; }
        if (week >= 6 && mob instanceof AbstractHorse && !mob.isVehicle() && mob.distanceToSqr(target) <= 25) { begin(mob, "hoof", 26); return; }
        if (week >= 11 && mob instanceof Panda && !night && mob.distanceToSqr(target) <= 25) { begin(mob, "panda", 26); return; }
        if (week >= 11 && mob instanceof PolarBear && mob.distanceToSqr(target) <= 36) { begin(mob, "bear", 26); return; }
        if (week >= 12 && mob instanceof Turtle && target.isInWater() && mob.distanceToSqr(target) <= 36) { begin(mob, "turtle", 24); return; }
        if (week >= 12 && mob instanceof Frog && mob.distanceToSqr(target) >= 9) { begin(mob, "frog", 24); return; }
        if (week >= 9 && mob instanceof Axolotl && target.isInWater() && mob.distanceToSqr(target) <= 25) { begin(mob, "axolotl", 24); return; }
        if (week < 3) return;
        data.putLong("lunar_dread.ability_until", now + 80);
        if (mob.getRandom().nextInt(100) >= (blood ? 60 : night ? 40 : 20)) return;
        if (mob instanceof Chicken && (clock <= 6000 || night)) begin(mob, "chicken", 24);
        else if (mob instanceof Cow && week >= 4 && (blood || clock > 6000 && clock < 13000)
            && mob.distanceToSqr(target) <= 25) begin(mob, "cow", 20);
        else if (mob instanceof Pig && week >= 7 && clock >= 11000 && !mob.isVehicle()) begin(mob, "pig", 30);
        else if (mob instanceof Rabbit && week >= 5 && night) begin(mob, "rabbit", 20);
        else if (mob instanceof Bat && week >= 8 && mob.getY() <= 40
            && level.getMaxLocalRawBrightness(mob.blockPosition()) <= 7) begin(mob, "bat", 15);
        else if (mob instanceof Squid && week >= 9 && target.isInWater()
            && mob.distanceToSqr(target) <= 36) begin(mob, "squid", 20);
    }

    public static void begin(Mob mob, String action, int ticks) {
        mob.getPersistentData().putInt("lunar_dread.skill_windup", ticks);
        mob.getPersistentData().putString("lunar_dread.windup_action", action);
        mob.addTag("lunar_dread.preparing");
        SoundEvent cue = action.equals("goat_fire") ? SoundEvents.GOAT_SCREAMING_AMBIENT
            : mob instanceof Bat ? SoundEvents.BAT_AMBIENT : mob instanceof Squid ? SoundEvents.SQUID_AMBIENT : voice(mob);
        sound(mob, cue, 0.8f, 0.7f);
        if (action.equals("sheep") || action.equals("llama") || action.equals("chicken"))
            sound(mob, SoundEvents.TNT_PRIMED, 0.7f, 0.8f);
    }

    static int tntCount(ServerLevel level) { return level.getEntities(EntityType.TNT, e -> true).size(); }
    private static int count(ServerLevel level, String tag) {
        int count = 0;
        for (Entity e : level.getAllEntities()) if (!e.isRemoved() && e.getTags().contains(tag)) ++count;
        return count;
    }

    static void release(Mob mob, ServerLevel level, ServerPlayer target, String action) {
        if (!enabled(level) || !mob.hasLineOfSight(target)) return;
        if(MoonGravity.busy(target)&&java.util.Set.of("frog","turtle","axolotl","squid","cow","panda").contains(action))return;
        long now = level.getGameTime();
        int cooldown = 4;
        switch (action) {
            case "wolf" -> {
                cooldown = 26; sound(mob, SoundEvents.WOLF_HOWL, 1.2f, 0.7f);
                target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 140));
                for (Mob friend : level.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(14),
                    e -> aggressive(e) || e instanceof net.minecraft.world.entity.monster.Enemy))
                    friend.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1));
            }
            case "fox", "cat" -> {
                cooldown = action.equals("cat") ? 30 : 22;
                Vec3 behind = target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-2.5));
                BlockPos p = BlockPos.containing(behind);
                if (!level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()
                    && level.noCollision(mob, mob.getBoundingBox().move(behind.subtract(mob.position())))) {
                    mob.teleportTo(behind.x, behind.y, behind.z);
                    target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 45));
                    mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 80, 1));
                    sound(mob, voice(mob), 1, 0.65f);
                }
            }
            case "bee" -> {
                cooldown = 18;
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, mob.getX(), mob.getY(), mob.getZ(), 24, 1, 0.7, 1, 0.1);
                if (inRange(mob, target, 4)) {
                    target.addEffect(new MobEffectInstance(MobEffects.POISON, 60));
                    target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
                }
            }
            case "hoof" -> {
                cooldown = 12;
                sound(mob, SoundEvents.HORSE_ANGRY, 1, 0.7f);
                for (ServerPlayer p : level.players()) if (inRange(mob, p, 5)) {
                    Vec3 push = p.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
                    p.setDeltaMovement(push.scale(0.7).add(0, 0.3, 0)); p.hurtMarked = true;
                    p.hurt(level.damageSources().mobAttack(mob),
                        (float)(4*DifficultyRules.animalDamageScale(SurvivalRules.day(level))));
                    if (mob instanceof Camel) p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60));
                }
            }
            case "panda" -> {
                cooldown = 24; sound(mob, SoundEvents.PANDA_SNEEZE, 1.2f, 0.65f);
                level.sendParticles(ParticleTypes.SNEEZE, mob.getX(), mob.getEyeY(), mob.getZ(), 30, 1.5, 0.8, 1.5, 0.1);
                for (ServerPlayer p : level.players()) if (inRange(mob, p, 5)) {
                    p.addEffect(new MobEffectInstance(MobEffects.POISON, 60));
                    p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 30, 1));
                }
            }
            case "bear" -> {
                cooldown = 20; sound(mob, SoundEvents.POLAR_BEAR_WARNING, 1, 0.65f);
                level.sendParticles(ParticleTypes.SNOWFLAKE, mob.getX(), mob.getEyeY(), mob.getZ(), 35, 2, 1, 2, 0.1);
                for (ServerPlayer p : level.players()) if (inRange(mob, p, 6)) {
                    p.setTicksFrozen(180); p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 2));
                }
            }
            case "frog", "turtle", "axolotl" -> {
                cooldown = action.equals("frog") ? 14 : 20;
                if (inRange(mob, target, action.equals("frog") ? 10 : 6)) {
                    Vec3 pull = mob.position().subtract(target.position()).normalize().scale(0.7);
                    target.setDeltaMovement(pull.add(0, 0.25, 0)); target.hurtMarked = true;
                    target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    if (action.equals("axolotl")) target.addEffect(new MobEffectInstance(MobEffects.POISON, 60));
                    sound(mob, action.equals("frog") ? SoundEvents.FROG_TONGUE : SoundEvents.GENERIC_SPLASH, 1, 0.7f);
                }
            }
            case "llama" -> {
                if (!(mob instanceof Llama llama)) break;
                cooldown = DifficultyRules.week(SurvivalRules.day(level)) >= 6 ? 24 : 7;
                llama.performRangedAttack(target, 1);
                if (DifficultyRules.week(SurvivalRules.day(level)) < 6 || tntCount(level) > 3
                    || mob.distanceToSqr(target) < 9 || mob.distanceToSqr(target) > 196) break;
                Vec3 mouth = mob.position().add(0, 1.45, 0);
                Vec3 aim = target.position().add(0, 0.6, 0).subtract(mouth).multiply(1, 0, 1).normalize();
                double speed = Math.min(0.85, 0.35 + mob.distanceTo(target) * 0.035);
                for (int i = -1; i <= 1; ++i) {
                    Vec3 velocity = aim.yRot((float)Math.toRadians(i * 12)).scale(speed).add(0, 0.32, 0);
                    PrimedTnt tnt = new PrimedTnt(level, mouth.x, mouth.y, mouth.z, llama);
                    tnt.setDeltaMovement(velocity); tnt.setFuse(60 + (i + 1) * 2);
                    tnt.addTag("lunar_dread.hostile_tnt"); tnt.addTag("lunar_dread.llama_tnt");
                    level.addFreshEntity(tnt);
                }
                sound(mob, SoundEvents.TNT_PRIMED, 0.8f, 1.2f);
            }
            case "goat_fire" -> { breath(mob, level, target); cooldown = 18; }
            case "sheep" -> {
                cooldown = mob.getTags().contains("lunar_dread.hidden") ? 16 : 20;
                if (tntCount(level) >= 6 || mob.distanceToSqr(target) < 9 || mob.distanceToSqr(target) > 144) { cooldown = 2; break; }
                Vec3 mouth = mob.position().add(0, 0.9, 0);
                Vec3 velocity = target.getEyePosition().subtract(mouth).normalize().scale(0.55).add(0, 0.25, 0);
                PrimedTnt tnt = new PrimedTnt(level, mouth.x, mouth.y, mouth.z, mob);
                tnt.setFuse(60);
                tnt.setDeltaMovement(velocity);
                tnt.addTag("lunar_dread.sheep_tnt");
                level.addFreshEntity(tnt);
                sound(mob, SoundEvents.TNT_PRIMED, 1, 1.3f);
            }
            case "chicken" -> {
                cooldown = 18;
                if (count(level, "lunar_dread.trick_egg") >= 12) break;
                ItemEntity egg = new ItemEntity(level, mob.getX(), mob.getY() + 0.3, mob.getZ(), new ItemStack(Items.EGG));
                // Individually armed eggs must not merge into one ItemEntity and silently erase fuses/cap accounting.
                var fuseId=new net.minecraft.nbt.CompoundTag();fuseId.putUUID("lunar_dread_armed_egg",java.util.UUID.randomUUID());
                egg.getItem().set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(fuseId));
                egg.setNeverPickUp();
                egg.addTag("lunar_dread.trick_egg");
                egg.getPersistentData().putUUID("lunar_dread.egg_owner", mob.getUUID());
                egg.getPersistentData().putLong("lunar_dread.egg_boom_at", now + 40);
                egg.getPersistentData().putLong("lunar_dread.expires", now + 100);
                level.addFreshEntity(egg);
                sound(mob, SoundEvents.CHICKEN_EGG, 1, 0.6f);
            }
            case "cow" -> {
                cooldown = 24;
                sound(mob, SoundEvents.RAVAGER_ROAR, 0.8f, 1.5f);
                for (ServerPlayer p : level.players()) if (inRange(mob, p, 5)) {
                    p.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 20, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                }
            }
            case "pig" -> {
                cooldown = 35;
                if (mob.isVehicle() || count(level, "lunar_dread.animal_minion") >= 16) break;
                Zombie rider = new Zombie(EntityType.ZOMBIE, level);
                rider.setBaby(true);
                rider.moveTo(mob.getX(), mob.getY() + 1, mob.getZ());
                if (!minion(rider, level, 2400)) break;
                rider.startRiding(mob, true);
                mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 160, 2));
                sound(mob, SoundEvents.PIG_SADDLE, 1, 0.6f);
            }
            case "rabbit" -> {
                cooldown = 22;
                Vec3 behind = target.position().add(Vec3.directionFromRotation(0, target.getYRot()).scale(-2));
                BlockPos floor = BlockPos.containing(behind).below();
                if (!level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                    && level.noCollision(mob, mob.getBoundingBox().move(behind.subtract(mob.position())))) {
                    mob.teleportTo(behind.x, behind.y, behind.z);
                    if (inRange(mob, target, 3)) {
                        target.hurt(level.damageSources().mobAttack(mob),
                            (float)(2*DifficultyRules.animalDamageScale(SurvivalRules.day(level))));
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80));
                    }
                    sound(mob, SoundEvents.ENDERMAN_TELEPORT, 0.8f, 1.8f);
                }
            }
            case "bat" -> {
                cooldown = 30;
                sound(mob, SoundEvents.BAT_TAKEOFF, 1, 0.5f);
                for (ServerPlayer p : level.players()) if (inRange(mob, p, 8)) {
                    p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40));
                    p.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100));
                }
                if (count(level, "lunar_dread.animal_minion") <= 14) for (int i = 0; i < 2; ++i) {
                    Silverfish fish = new Silverfish(EntityType.SILVERFISH, level);
                    Vec3 away=mob.position().subtract(target.position()).multiply(1,0,1).normalize();
                    fish.moveTo(mob.position().add(away.scale(18)));
                    minion(fish, level, 1800);
                }
            }
            case "squid" -> {
                cooldown = 25;
                for (ServerPlayer p : level.players()) if (inRange(mob, p, 6) && p.isInWater()) {
                    p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40));
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
                    p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 120, 1));
                    Vec3 pull = mob.position().subtract(p.position()).normalize();
                    BlockPos next = BlockPos.containing(p.position().add(pull));
                    if (level.getFluidState(next).getType() == Fluids.WATER
                        && level.getFluidState(next.above()).getType() == Fluids.WATER) {
                        p.setDeltaMovement(pull.scale(0.55));
                        p.hurtMarked = true;
                    }
                }
                sound(mob, SoundEvents.SQUID_SQUIRT, 1, 0.7f);
            }
            default -> { return; }
        }
        mob.getPersistentData().putLong("lunar_dread.ability_until", now + cooldown * 20L);
    }

    private static boolean minion(Mob mob, ServerLevel level, int lifetime) {
        if (!level.hasChunkAt(mob.blockPosition()) || !level.noCollision(mob)
            || level.players().stream().anyMatch(p -> LunarDread.player(p) && p.distanceToSqr(mob) < 144)) return false;
        mob.addTag("lunar_dread.animal_minion");
        mob.getPersistentData().putLong("lunar_dread.expires", level.getGameTime() + lifetime);
        mob.setTarget(LunarDread.nearest(mob, 32));
        return level.addFreshEntity(mob);
    }
    private static boolean inRange(Mob mob, ServerPlayer player, double range) {
        return LunarDread.player(player) && mob.distanceToSqr(player) <= range * range && mob.hasLineOfSight(player);
    }
    private static void sound(Mob mob, SoundEvent event, float volume, float pitch) {
        mob.level().playSound(null, mob.blockPosition(), event, SoundSource.HOSTILE, volume, pitch);
    }
    static void entityTick(Entity entity, ServerLevel level) {
        if (entity instanceof PrimedTnt tnt && tnt.getFuse() == 1
            && (tnt.getTags().contains("lunar_dread.sheep_tnt") || tnt.getTags().contains("lunar_dread.hostile_tnt"))) {
            // Resolve a tick before vanilla's fixed power-4 explosion.
            float power = tnt.getTags().contains("lunar_dread.llama_tnt") ? 1f
                : tnt.getTags().contains("lunar_dread.sheep_tnt") ? 1.5f : 2f;
            level.explode(tnt, tnt.getX(), tnt.getY(), tnt.getZ(), power, Level.ExplosionInteraction.MOB);
            tnt.discard();
            return;
        }
        if (entity instanceof Goat goat) {
            // Vanilla brain ramming would bypass the shared retaliation/pursuit rules.
            goat.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.RAM_TARGET);
            goat.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.RAM_COOLDOWN_TICKS, 200);
        }
        if (entity instanceof Animal animal && entity.tickCount % 20 == 0
            && DifficultyRules.unlocked(SurvivalRules.day(level), 2)
            && !(animal instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame())
            && !(animal instanceof AbstractHorse horse && horse.isTamed())) {
            var attribute=animal.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
            if (attribute != null) {
                var data=animal.getPersistentData();
                if (!data.contains("lunar_dread.base_health")) data.putDouble("lunar_dread.base_health",attribute.getBaseValue());
                int day=SurvivalRules.day(level);
                if (data.getInt("lunar_dread.health_day") != day) {
                    boolean newborn=entity.tickCount <= 20 && !data.contains("lunar_dread.health_day");
                    attribute.setBaseValue(data.getDouble("lunar_dread.base_health")*DifficultyRules.animalHealthScale(day));
                    if (newborn) animal.setHealth(animal.getMaxHealth());
                    else if (animal.getHealth() > animal.getMaxHealth()) animal.setHealth(animal.getMaxHealth());
                    data.putInt("lunar_dread.health_day",day);
                }
            }
        }
        if(entity instanceof Llama llama&&entity.getTags().contains("lunar_dread.spawn_llama")&&llama.hasRestriction()){
            if(level.getGameTime()>=entity.getPersistentData().getLong("lunar_dread.arrival_until")||LunarDread.nearest(llama,LLAMA_AGGRO_RANGE)!=null)llama.clearRestriction();
        }
        if (entity.tickCount % 10 != 0) return;
        long expires = entity.getPersistentData().getLong("lunar_dread.expires");
        if (expires > 0 && level.getGameTime() >= expires) { entity.discard(); return; }
        if (!(entity instanceof ItemEntity egg) || !egg.getTags().contains("lunar_dread.trick_egg")) return;
        if (!enabled(level)) { egg.discard(); return; }
        level.sendParticles(ParticleTypes.WITCH, egg.getX(), egg.getY() + 0.2, egg.getZ(), 2, 0.1, 0.1, 0.1, 0.01);
        if (level.getGameTime() < egg.getPersistentData().getLong("lunar_dread.egg_boom_at")) return;
        for (ServerPlayer p : level.players()) if (LunarDread.player(p) && p.distanceToSqr(egg) <= 9 && p.hasLineOfSight(egg)) {
            p.addEffect(new MobEffectInstance(MobEffects.POISON, 60));
            p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100));
        }
        level.playSound(null, egg.blockPosition(), SoundEvents.EGG_THROW, SoundSource.HOSTILE, 1, 0.5f);
        Entity owner = egg.getPersistentData().hasUUID("lunar_dread.egg_owner") ? level.getEntity(egg.getPersistentData().getUUID("lunar_dread.egg_owner")) : null;
        // Small real explosion: the eggs are dangerous, but don't act as silent proximity damage.
        level.explode(owner, egg.getX(), egg.getY() + 0.1, egg.getZ(), 1.25f, Level.ExplosionInteraction.MOB);
        egg.discard();
    }

    static void goatTrap(Goat goat, ServerLevel level) {
        boolean night = level.getDayTime() % 24000 >= 13000;
        boolean blood = level.dimension() == Level.OVERWORLD && LunarDread.score(level, "#blood_active") == 1;
        boolean charged = goat.getRandom().nextInt(100) < (blood ? 30 : night ? 15 : 5);
        ServerPlayer target = LunarDread.nearest(goat, 32);
        if (target == null) return;
        Vec3 away = goat.position().subtract(target.position()).multiply(1, 0, 1).normalize();
        if (away.lengthSqr() < 0.01) away = new Vec3(1, 0, 0);
        Vec3 pos = goat.position().add(away.scale(14));
        if (!level.hasChunkAt(BlockPos.containing(pos))) return;
        Creeper creeper = new Creeper(EntityType.CREEPER, level);
        creeper.moveTo(pos.x, pos.y, pos.z);
        if (!level.noCollision(creeper) || level.players().stream().anyMatch(p -> LunarDread.player(p) && p.distanceToSqr(creeper) < 144)) return;
        creeper.addTag("lunar_dread.goat_trap");
        if (charged) creeper.thunderHit(level, EntityType.LIGHTNING_BOLT.create(level));
        creeper.setTarget(target);
        level.addFreshEntity(creeper);
        sound(goat, SoundEvents.CREEPER_PRIMED, 1, 0.7f);
    }

    private static void breath(Mob goat, ServerLevel level, ServerPlayer target) {
        Vec3 start = goat.position().add(0, 0.9, 0);
        Vec3 direction = target.getEyePosition().subtract(start).normalize();
        Vec3 end = level.clip(new ClipContext(start, start.add(direction.scale(6)),
            ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, goat)).getLocation();
        Set<UUID> hit = new HashSet<>();
        sound(goat, SoundEvents.BLAZE_SHOOT, 0.8f, 0.7f);
        for (double d = 0.5; d <= 6 && d <= start.distanceTo(end) + 0.01; d += 0.5) {
            Vec3 point = start.add(direction.scale(d));
            level.sendParticles(ParticleTypes.FLAME, point.x, point.y, point.z, 5, 0.12, 0.12, 0.12, 0.015);
            for (ServerPlayer p : level.players()) if (LunarDread.player(p) && !hit.contains(p.getUUID())
                && p.getBoundingBox().inflate(0.25).contains(point)) {
                hit.add(p.getUUID());
                p.hurt(level.damageSources().inFire(),
                    (float)(2*DifficultyRules.animalDamageScale(SurvivalRules.day(level))));
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40));
            }
        }
    }
}
