package lunar.dread;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Vector3f;

/** Client reports visual attention only; the host owns exposure, marks and bounded hunts. */
public final class MoonDread {
    private static final Map<UUID, Attention> attention = new HashMap<>();
    private static final DustParticleOptions RED = new DustParticleOptions(new Vector3f(0.65f, 0.015f, 0.04f), 0.9f);
    private static class Attention {
        long report = -100, cooldown;
        boolean looking;
        int gaze, mark, grace;
        int away=20;
        boolean armed=true;
        long trial=-1000;
        boolean challenged;
    }
    private static boolean enabled(ServerPlayer player) {
        return LunarDread.player(player)
            && DifficultyRules.unlocked(SurvivalRules.day(player.serverLevel()),14)
            && LivingMoon.active(player.serverLevel())
            && LunarDread.score(player.serverLevel(), "#moon_attacks") == 1;
    }
    public static void report(ServerPlayer player, boolean looking) {
        if (!enabled(player)) return;
        observe(player, looking, player.serverLevel().getGameTime());
    }
    private static void observe(ServerPlayer player, boolean looking, long now) {
        Attention a = attention.computeIfAbsent(player.getUUID(), id -> new Attention());
        if (now - a.report < 3) return;
        a.report = now; a.looking = looking;
    }
    public static boolean marked(ServerPlayer player) {
        Attention a = attention.get(player.getUUID());
        return enabled(player) && a != null && a.mark > 0 && a.grace == 0 && player.hasEffect(MobEffects.GLOWING);
    }
    static ServerPlayer challenger(net.minecraft.server.level.ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Attention a = attention.get(player.getUUID());
            if (a != null && marked(player) && !a.challenged && a.looking
                && level.getGameTime() - a.report <= 12 && !LivingMoon.sheltered(level, player.position())) {
                a.challenged = true;
                return player;
            }
        }
        return null;
    }
    public static boolean staring(Mob mob) {
        var d = mob.getPersistentData();
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel level)
            || !LivingMoon.active(level) || LunarDread.score(level, "#moon_attacks") != 1
            || !AnimalCombat.enabled(level) || !wild(mob) || !d.hasUUID("lunar_dread.moon_witness")) return false;
        var p = level.getServer().getPlayerList().getPlayer(d.getUUID("lunar_dread.moon_witness"));
        return p != null && p.level() == level && LunarDread.player(p)
            && d.getLong("lunar_dread.moon_stare_until") > level.getGameTime() && mob.distanceToSqr(p) <= 24 * 24;
    }
    private static boolean wild(Mob mob) {
        return mob instanceof Animal && !(mob instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isTame())
            && !(mob instanceof AbstractHorse horse && horse.isTamed());
    }
    public static void addWitnessGoal(Mob mob) {
        if (mob instanceof Animal) mob.goalSelector.addGoal(-1, new Goal() {
            { setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP)); }
            @Override public boolean canUse() { return staring(mob); }
            @Override public boolean canContinueToUse() { return staring(mob); }
            @Override public boolean requiresUpdateEveryTick() { return true; }
            @Override public void tick() { hold(mob); }
        });
    }
    public static void hold(Mob mob) {
        if (!staring(mob)) return;
        var p = mob.level().getServer().getPlayerList().getPlayer(mob.getPersistentData().getUUID("lunar_dread.moon_witness"));
        mob.getNavigation().stop();
        mob.setDeltaMovement(0, mob.getDeltaMovement().y, 0);
        mob.getLookControl().setLookAt(p, 90, 90);
    }
    static void witnesses(ServerPlayer player, long now) {
        int count = 0;
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(20),
                m -> m.isAlive() && !m.isNoAi() && wild(m) && m.hasLineOfSight(player))) {
            if (++count > 12) break;
            var d = mob.getPersistentData();
            d.putUUID("lunar_dread.moon_witness", player.getUUID());
            d.putLong("lunar_dread.moon_stare_until", now + 60);
        }
    }
    private static void personalSound(ServerPlayer player, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        player.playNotifySound(sound, SoundSource.HOSTILE, volume, pitch);
    }
    private static void advance(ServerPlayer player, Attention a, long now) {
        var level = player.serverLevel();
        if (!enabled(player)) { clear(player, a); return; }
        boolean roof = LivingMoon.sheltered(level, player.position());
        boolean looking = a.looking && now - a.report <= 12 && !roof;
        if (a.mark > 0) {
            // Milk (or another actual cure) removes the effect and the host's mark together.
            if (!player.hasEffect(MobEffects.GLOWING)) a.mark = a.grace = 0;
            else {
                a.mark = Math.max(0, a.mark - (roof ? 4 : 1));
                a.grace = Math.max(0, a.grace - 1);
                if (a.mark == 0) player.removeEffect(MobEffects.GLOWING);
                else if (now % 5 == 0) level.sendParticles(RED, player.getX(), player.getY() + .08, player.getZ(), 2, .18, .02, .18, .005);
            }
        }
        if (a.mark == 0 && !MoonDiscovery.cooling(player)&&!MoonIncidents.busy(player)) {
            if(!looking){a.away++;a.gaze=0;if(a.away>=20)a.armed=true;}
            else {
                a.away=0;
                a.gaze=Math.min(40,a.gaze+1);
                if(a.gaze>=40&&(a.armed||now-a.trial>=240)){
                    a.armed=false;a.trial=now;MoonDiscovery.attempt(player);
                }
            }
        } else a.gaze = 0;
        if (marked(player) && now % 20 == 0) {
            int count = 0;
            for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64),
                    m -> m.isAlive() && !m.isNoAi() && (m instanceof Enemy || wild(m)))) {
                if (++count > 24) break;
                if (ThreatSenses.baited(mob)) continue;
                mob.setTarget(player); mob.getNavigation().moveTo(player, 1.3);
            }
        }
        if (now % 5 == 0) PacketDistributor.sendToPlayer(player, new MoonNetwork.Dread(a.gaze, a.mark));
    }
    static void mark(ServerPlayer p,int duration){var a=attention.computeIfAbsent(p.getUUID(),id->new Attention());a.mark=duration;a.grace=0;a.challenged=false;p.addEffect(new MobEffectInstance(MobEffects.GLOWING,duration,0,false,false));}
    private static void clear(ServerPlayer player, Attention a) {
        MoonDiscovery.cancel(player);
        if (a.mark > 0) player.removeEffect(MobEffects.GLOWING);
        if (a.mark > 0 || a.gaze > 0) PacketDistributor.sendToPlayer(player, new MoonNetwork.Dread(0, 0));
        a.mark = a.gaze = a.grace = 0; a.looking = false; a.report = -100; a.cooldown = 0;
    }
    @SubscribeEvent public void logout(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            Attention a = attention.remove(player.getUUID());
            if (a != null && a.mark > 0) player.removeEffect(MobEffects.GLOWING);
        }
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var online = new HashSet<UUID>();
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            online.add(player.getUUID());
            if(LivingMoon.active(player.serverLevel())){MoonDiscovery.update(player);attention.computeIfAbsent(player.getUUID(),id->new Attention());}
            Attention a = attention.get(player.getUUID());
            if (a != null) advance(player, a, player.serverLevel().getGameTime());
        }
        attention.keySet().retainAll(online);
    }
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { attention.clear(); }
}
