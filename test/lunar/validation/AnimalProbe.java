package lunar.validation;

import java.util.UUID;
import java.util.List;
import com.mojang.authlib.GameProfile;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Only built into validation-server/mods; never included in the player pack. */
@Mod("lunar_validation")
public final class AnimalProbe {
    private ProbePlayer probe;
    public AnimalProbe() { NeoForge.EVENT_BUS.register(this); }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> players(PlayerList list) {
        try {
            var field = PlayerList.class.getDeclaredField("players");
            field.setAccessible(true);
            return (List<ServerPlayer>) field.get(list);
        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<UUID, ServerPlayer> byUUID(PlayerList list) {
        try { var f = PlayerList.class.getDeclaredField("playersByUUID"); f.setAccessible(true);
            return (java.util.Map<UUID, ServerPlayer>)f.get(list);
        } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }

    private static final class ProbePlayer extends FakePlayer {
        ProbePlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.fromString("e2d16d6d-4ca1-3d91-a99d-023cc370e2f3"), "LunarProbe"));
            // FakePlayer has no player tick to expire vanilla spawn protection.
            try {
                var protection = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                protection.setAccessible(true);
                protection.setInt(this, 0);
            } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        }
        @Override public boolean isInvulnerableTo(DamageSource source) { return false; }
    }

    @SubscribeEvent
    public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunarprobe")
            .requires(source -> source.hasPermission(4))
            .then(Commands.literal("carry").executes(context->{
                var l=context.getSource().getServer().overworld();var viewer=l.players().stream().filter(p->p.getGameProfile().getName().equals("MoonViewer")).findFirst().orElseThrow();
                try{
                    var f=lunar.dread.MoonIncidents.class.getDeclaredField("states");f.setAccessible(true);((java.util.Map<?,?>)f.get(null)).clear();
                    var m=lunar.dread.MoonContent.SNATCHER.get().create(l);m.moveTo(viewer.getX()+1,viewer.getY(),viewer.getZ());m.setNoAi(true);m.addTag("probe.carrier");l.addFreshEntity(m);
                    var capture=lunar.dread.AbductionController.class.getDeclaredMethod("capture",lunar.dread.HorrorMob.class,ServerPlayer.class);capture.setAccessible(true);
                    if(!(Boolean)capture.invoke(null,m,viewer))throw new IllegalStateException("Native capture rejected");return 1;
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            }))
            .then(Commands.literal("gazeseed").executes(context->{
                var l=context.getSource().getServer().overworld();var viewer=l.players().stream().filter(p->p.getGameProfile().getName().equals("MoonViewer")).findFirst().orElseThrow();
                try{var method=lunar.dread.MoonDiscovery.class.getDeclaredMethod("record",ServerPlayer.class);method.setAccessible(true);var data=(net.minecraft.nbt.CompoundTag)method.invoke(null,viewer);data.putInt("sequence",0);data.remove("next");data.remove("markAt");
                    long seed=1;while(!lunar.dread.MoonDiscovery.roll(seed,viewer.getUUID(),1))seed++;var s=lunar.dread.MoonCycle.state(l);s.seed=seed;s.setDirty();return 1;
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
            }))
            .then(Commands.literal("lunarcheck").executes(context->{try{lunar.dread.LunarChecks.run(probe);return 1;}catch(Exception e){e.printStackTrace();throw new IllegalStateException(e);}}))
            .then(Commands.literal("cavecheck").executes(context->{lunar.dread.CaveChecks.run(probe);return 1;}))
            .then(Commands.literal("conversioncheck").executes(context->{try{lunar.dread.HordeConversionChecks.run(probe);return 1;}catch(Exception error){error.printStackTrace();throw new IllegalStateException(error);}}))
            .then(Commands.literal("restorationcheck").executes(context->{try{lunar.dread.BlockRecoveryChecks.run(probe);return 1;}catch(Throwable error){error.printStackTrace();throw error;}}))
            .then(Commands.literal("clearactors").executes(context -> {
                var level=context.getSource().getLevel();
                var entities=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
                level.getAllEntities().forEach(entities::add);
                for (var entity:entities) if (!(entity instanceof ServerPlayer)) entity.discard();
                return 1;
            }))
            .then(Commands.literal("join").executes(context -> {
                try {
                ServerLevel level = context.getSource().getLevel();
                probe = new ProbePlayer(level);
                // Mods inspect NeoForge channel attributes even for fake players.
                new EmbeddedChannel(probe.connection.getConnection());
                probe.setGameMode(GameType.SURVIVAL);
                probe.moveTo(260, 100, 260, 0, 0);
                level.getServer().getPlayerList().broadcastAll(net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(probe)));
                level.addNewPlayer(probe);
                players(level.getServer().getPlayerList()).add(probe);
                byUUID(level.getServer().getPlayerList()).put(probe.getUUID(), probe);
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(probe));
                context.getSource().sendSuccess(() -> Component.literal("PROBE_JOINED"), false);
                return 1;
                } catch (Throwable error) {
                    error.printStackTrace();
                    throw new IllegalStateException("Probe setup failed", error);
                }
            }))
            .then(Commands.literal("guidecheck").executes(context -> {
                var written = net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT;
                var books = probe.getInventory().items.stream().filter(stack -> stack.has(written)).toList();
                if (books.size() != 1 || books.getFirst().get(written).pages().size() != 14)
                    throw new IllegalStateException("First login must deliver exactly one fourteen-page guide");
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(probe));
                if (probe.getInventory().items.stream().filter(stack -> stack.has(written)).count() != 1)
                    throw new IllegalStateException("Repeat login duplicated guide");
                var saved = probe.saveWithoutId(new net.minecraft.nbt.CompoundTag());
                if (!saved.getCompound("NeoForgeData").getBoolean("lunar_dread.guide_received"))
                    throw new IllegalStateException("Guide delivery flag not serialized");
                var clone = new ProbePlayer(probe.serverLevel());
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, probe, true));
                if (!clone.getPersistentData().getBoolean("lunar_dread.guide_received"))
                    throw new IllegalStateException("Death clone lost guide delivery flag");
                probe.getInventory().clearContent();
                int issued = context.getSource().getServer().getCommands().getDispatcher()
                    .execute("lunardread guide", probe.createCommandSourceStack().withPermission(0));
                if (issued != 1 || probe.getInventory().items.stream().filter(stack -> stack.has(written)).count() != 1)
                    throw new IllegalStateException("Non-operator could not recover guide");
                context.getSource().sendSuccess(() -> Component.literal("GUIDE_LOGIN_ONCE_PERSIST_CLONE_RECOVERY_OK"), false);
                return 1;
            }))
            .then(Commands.literal("survivalcheck").executes(context -> {
                SurvivalChecks.run(probe);
                context.getSource().sendSuccess(() -> Component.literal("SURVIVAL_STARTER_RESPAWN_LLAMA_ONCE_CLEANUP_NOISE_OK"), false);
                return 1;
            }))
            .then(Commands.literal("airspawn").executes(context -> {
                try {
                    var method = Class.forName("lunar.dread.AirRaid").getDeclaredMethod("spawn", ServerLevel.class, ServerPlayer.class);
                    method.setAccessible(true);
                    int n = (int)method.invoke(null, probe.serverLevel(), probe);
                    context.getSource().sendSuccess(() -> Component.literal("AIR_SPAWNED=" + n), false);
                    return n;
                } catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            }))
            .then(Commands.literal("milkcheck").executes(context -> {
                var infected = net.minecraft.core.registries.BuiltInRegistries.MOB_EFFECT.getHolder(
                    net.minecraft.resources.ResourceLocation.parse("hordes:infected")).orElseThrow();
                probe.addEffect(new net.minecraft.world.effect.MobEffectInstance(infected, 600));
                probe.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WEAKNESS, 600));
                probe.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST, 600));
                if (!probe.hasEffect(infected)) throw new IllegalStateException("Infection setup failed");
                NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent.Finish(probe,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MILK_BUCKET), 32,
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BUCKET)));
                if (!probe.getActiveEffects().isEmpty()) throw new IllegalStateException("Milk must clear all positive/negative effects, including infection");
                context.getSource().sendSuccess(() -> Component.literal("MILK_ALL_EFFECTS_INFECTION_OK"), false);
                return 1;
            }))
            .then(Commands.literal("mooncheck").executes(context -> {
                try { MoonChecks.run(probe); }
                catch (Throwable e) { e.printStackTrace(); throw new IllegalStateException(e); }
                context.getSource().sendSuccess(() -> Component.literal("MOON_TRACK_LOCK_DODGE_COVER_HIT_RESET_OK"), false);
                return 1;
            }))
            .then(Commands.literal("dreadcheck").executes(context -> {
                try { DreadChecks.run(probe); }
                catch (Throwable e) { e.printStackTrace(); throw new IllegalStateException(e); }
                context.getSource().sendSuccess(() -> Component.literal("MOON_ATTENTION_GLANCE_MARK_HUNT_ROOF_CURE_CAP_RESET_OK"), false);
                return 1;
            }))
            .then(Commands.literal("fell").executes(context -> {
                probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_AXE));
                var pos = new net.minecraft.core.BlockPos(275, 100, 260);
                probe.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, net.minecraft.world.phys.Vec3.atCenterOf(pos));
                if (!probe.gameMode.destroyBlock(pos)) throw new IllegalStateException("Axe felling rejected");
                return 1;
            }))
            .then(Commands.literal("noise").executes(context -> {
                for (int i = 0; i < 8; i++) NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(
                    probe.serverLevel(), probe.blockPosition().below(), probe.serverLevel().getBlockState(probe.blockPosition().below()), probe));
                return 1;
            }))
            .then(Commands.literal("targetcheck").executes(context -> {
                for (var e : probe.serverLevel().getAllEntities()) if (e instanceof Mob mob && e.getTags().contains("probe.sense"))
                    context.getSource().sendSuccess(() -> Component.literal("SENSE_TARGET=" + (mob.getTarget() == probe)), false);
                return 1;
            }))
            .then(Commands.literal("state").then(Commands.argument("tag", StringArgumentType.word()).executes(context -> {
                String tag = StringArgumentType.getString(context, "tag");
                for (var entity : probe.serverLevel().getAllEntities()) if (entity.isAlive() && entity.getTags().contains(tag))
                    context.getSource().sendSuccess(() -> Component.literal("PROBE_STATE " + entity.getPersistentData()), false);
                return 1;
            })))
            .then(Commands.literal("prepare").then(Commands.argument("tag", StringArgumentType.word())
                .then(Commands.argument("action", StringArgumentType.word())
                .then(Commands.argument("ticks", IntegerArgumentType.integer(1, 100)).executes(context -> {
                    String tag = StringArgumentType.getString(context, "tag");
                    for (var entity : probe.serverLevel().getAllEntities()) if (entity.isAlive() && entity.getTags().contains(tag)) {
                        entity.getPersistentData().putInt("lunar_dread.skill_windup", IntegerArgumentType.getInteger(context, "ticks"));
                        entity.getPersistentData().putString("lunar_dread.windup_action", StringArgumentType.getString(context, "action"));
                    }
                    return 1;
                })))))
            .then(Commands.literal("inspect").executes(context -> {
                for (Mob mob : probe.serverLevel().getEntitiesOfClass(Mob.class, probe.getBoundingBox().inflate(16))) {
                    String goals = mob.goalSelector.getAvailableGoals().stream().map(goal ->
                        goal.getPriority() + ":" + goal.isRunning() + ":" + goal.getGoal().getClass().getSimpleName()).toList().toString();
                    StringBuilder state = new StringBuilder();
                    for (var goal : mob.goalSelector.getAvailableGoals()) if (goal.getGoal().getClass().getSimpleName().equals("LungeGoal")) {
                        for (var field : goal.getGoal().getClass().getDeclaredFields()) try {
                            field.setAccessible(true); state.append(field.getName()).append('=').append(field.get(goal.getGoal())).append(' ');
                        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                    }
                    context.getSource().sendSuccess(() -> Component.literal("PROBE_AI " + mob.getType()
                        + " pos=" + mob.position() + " noAI=" + mob.isNoAi() + " target=" + mob.getTarget()
                        + " LOS=" + mob.hasLineOfSight(probe) + " players=" + probe.serverLevel().players().size()
                        + " distance=" + mob.distanceTo(probe) + " persistent=" + mob.getPersistentData() + " state=" + state + " goals=" + goals), false);
                }
                return 1;
            }))
            .then(Commands.literal("move").then(Commands.argument("position", Vec3Argument.vec3()).executes(context -> {
                var position = Vec3Argument.getVec3(context, "position");
                probe.moveTo(position.x, position.y, position.z, 0, 0);
                probe.setDeltaMovement(0, 0, 0);
                probe.baseTick();
                return 1;
            })))
            .then(Commands.literal("heal").executes(context -> {
                probe.setHealth(20);
                probe.invulnerableTime = 0;
                return 1;
            }))
            .then(Commands.literal("leave").executes(context -> {
                if (probe != null) {
                    players(context.getSource().getServer().getPlayerList()).remove(probe);
                    byUUID(context.getSource().getServer().getPlayerList()).remove(probe.getUUID());
                    probe.discard();
                    probe = null;
                }
                return 1;
            })));
    }
}
