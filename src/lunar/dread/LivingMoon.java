package lunar.dread;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** A telegraphed moon attack. No entities, permanent attributes, or terrain mutations. */
public final class LivingMoon {
    private static LivingMoon current;
    public LivingMoon(){current=this;}
    public static boolean targeted(ServerPlayer player){return current!=null&&current.age>=0&&current.targets.stream().anyMatch(t->t.player.equals(player.getUUID()));}
    public static int trigger(ServerLevel level,List<ServerPlayer> players){
        if(current==null||!active(level))return 0;
        MoonCycle.awaken(level,null);current.begin(level,players);return current.targets.size();
    }
    private static final int TRACK = 48, LOCK = 32, FIRE = 20;
    private static final double RADIUS = 4.5;
    private record Target(UUID player, Vec3 aim, Vec3 impact) {}
    private final List<Target> targets = new ArrayList<>();
    private boolean wasActive;
    private int age = -1;
    private long nextAttack;

    public static boolean active(ServerLevel level) {
        long clock = Math.floorMod(level.getDayTime(), 24000);
        return level.dimension() == Level.OVERWORLD && clock >= 13000
            && LunarDread.score(level, "#blood_active") == 1 && LunarDread.score(level, "#finished") != 1;
    }
    private int phase() { return age < 0 ? 0 : age < TRACK ? 1 : age < TRACK + LOCK ? 2 : 3; }
    private MoonNetwork.State snapshot(ServerLevel level) {
        return new MoonNetwork.State(active(level), phase(), level.getGameTime(), Math.max(1, LunarDread.score(level, "#blood_count")),
            targets.stream().map(t -> new MoonNetwork.Spot(t.aim, t.impact)).toList());
    }
    private void sync(ServerLevel level) {
        var packet = snapshot(level);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, packet);
    }
    private void reset() { age = -1; targets.clear(); nextAttack = 0; wasActive = false; }
    public static void cancel(ServerLevel level){if(current!=null){current.reset();current.sync(level);}}
    @SubscribeEvent public void stopped(ServerStoppedEvent event) { reset(); }
    static Vec3 skyHit(ServerLevel level, Vec3 aim) {
        Vec3 end = aim.add(0, 1, 0);
        double top = Math.max(end.y + 8, Math.min(level.getMaxBuildHeight() - 1, end.y + 128));
        var hit = level.clip(new ClipContext(new Vec3(aim.x, top, aim.z), end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, net.minecraft.world.phys.shapes.CollisionContext.empty()));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }
    static boolean sheltered(ServerLevel level, Vec3 aim) { return skyHit(level, aim).y > aim.y + 1.1; }
    void begin(ServerLevel level, List<ServerPlayer> candidates) {
        if (!active(level) || !MoonCycle.giant(level) || age >= 0) return;
        var shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, new java.util.Random(level.random.nextLong()));
        int count = Math.min(3, 1 + Math.max(0, LunarDread.score(level, "#blood_count") - 1) / 2);
        for (ServerPlayer player : shuffled) {
            if (!LunarDread.player(player) || player.level() != level || MoonIncidents.busy(player)) continue;
            targets.add(new Target(player.getUUID(), player.position(), player.position().add(0, 1, 0)));
            if (targets.size() >= count) break;
        }
        if (targets.isEmpty()) return;
        age = 0;
        level.players().forEach(p -> level.playSound(null, p.blockPosition(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.HOSTILE, 0.55f, 0.55f));
        sync(level);
    }
    private void release(ServerLevel level) {
        float damage = Math.min(26, 14 + 1.5f * Math.max(0, LunarDread.score(level, "#blood_count") - 1));
        for (int i = 0; i < targets.size(); i++) {
            Target t = targets.get(i);
            Vec3 impact = skyHit(level, t.aim);
            targets.set(i, new Target(t.player, t.aim, impact));
            level.playSound(null, net.minecraft.core.BlockPos.containing(impact), SoundEvents.WARDEN_SONIC_BOOM, SoundSource.HOSTILE, 1.6f, 0.65f);
            level.sendParticles(ParticleTypes.FLASH, impact.x, impact.y + 0.1, impact.z, 1, 0, 0, 0, 0);
            level.sendParticles(new DustParticleOptions(new org.joml.Vector3f(.58f,.015f,.045f),1.6f),
                impact.x, impact.y + 0.15, impact.z, 64, 2.9, 0.18, 2.9, 0.015);
            if (sheltered(level, t.aim)) continue;
            for (ServerPlayer player : level.players()) {
                if (!LunarDread.player(player) || player.position().distanceToSqr(t.aim) > RADIUS * RADIUS || sheltered(level, player.position())) continue;
                if (player.hurt(level.damageSources().magic(), damage)) {
                    player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
                }
            }
        }
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) PacketDistributor.sendToPlayer(player, snapshot(player.getServer().overworld()));
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        current=this;
        ServerLevel level = event.getServer().overworld();
        boolean live = active(level);
        long now = level.getGameTime();
        if (!live) {
            boolean dirty = wasActive || age >= 0;
            reset();
            if (dirty || now % 20 == 0) sync(level);
            return;
        }
        if (!wasActive) { wasActive = true; nextAttack = now + 900; sync(level); }
        if(!MoonCycle.giant(level)||!MoonCycle.ready(level)){
            if(age>=0){age=-1;targets.clear();sync(level);}nextAttack=Math.max(nextAttack,now+100);if(now%20==0)sync(level);return;
        }
        if (LunarDread.score(level, "#moon_attacks") == 0) {
            if (age >= 0) { age = -1; targets.clear(); sync(level); }
            nextAttack = now + 900;
            if (now % 20 == 0) sync(level);
            return;
        }
        ServerPlayer challenger = age < 0 ? MoonDread.challenger(level) : null;
        if (age < 0 && (challenger != null || now >= nextAttack)) {
            begin(level, challenger == null ? level.players() : List.of(challenger));
            if (age < 0) nextAttack = now + 100;
        }
        if (age >= 0) {
            targets.removeIf(t -> {
                var p = level.getServer().getPlayerList().getPlayer(t.player);
                return p == null || p.level() != level || !LunarDread.player(p);
            });
            if (targets.isEmpty()) age = -1;
            else {
                if (age < TRACK) for (int i = 0; i < targets.size(); i++) {
                    var t = targets.get(i); var p = level.getServer().getPlayerList().getPlayer(t.player);
                    targets.set(i, new Target(t.player, p.position(), p.position().add(0, 1, 0)));
                }
                if (age == TRACK) {
                    for (var t : targets) level.playSound(null, net.minecraft.core.BlockPos.containing(t.aim), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 1, 1.6f);
                    sync(level);
                }
                if (age == TRACK + LOCK) { release(level); sync(level); }
                if (++age >= TRACK + LOCK + FIRE) { targets.clear(); age = -1; sync(level); }
            }
            if (age < 0) nextAttack = now + Math.max(360, 1040 - 60 * Math.max(0, LunarDread.score(level, "#blood_count") - 1));
        }
        if (now % (age >= 0 ? 4 : 20) == 0) sync(level);
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("moon").requires(s -> s.hasPermission(2))
            .then(Commands.literal("strike").executes(ctx -> {
                var level = ctx.getSource().getServer().overworld();
                if (!active(level)) { ctx.getSource().sendFailure(Component.literal("仅在主世界血月夜可唤醒月面。")); return 0; }
                MoonCycle.awaken(level,null);begin(level, level.players()); return targets.size();
            }))));
    }
}
