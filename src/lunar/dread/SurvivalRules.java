package lunar.dread;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.Llama;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Persistent world rules, conservative debris collection and once-only rewards. */
public final class SurvivalRules {
    private static final String STARTER = "lunar_dread.starter_received";
    private static final SavedData.Factory<WorldState> FACTORY = new SavedData.Factory<>(WorldState::new, WorldState::load);

    public static final class WorldState extends SavedData {
        int llamas, cleanupDay, goatDay;
        boolean rareClaimed;
        long cleanupAt;
        static WorldState load(CompoundTag tag, HolderLookup.Provider registry) {
            WorldState s = new WorldState();
            s.llamas = tag.getInt("llamas"); s.rareClaimed = tag.getBoolean("rareClaimed");
            s.cleanupDay = tag.getInt("cleanupDay"); s.goatDay = tag.getInt("goatDay");
            s.cleanupAt = tag.getLong("cleanupAt"); return s;
        }
        @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registry) {
            tag.putInt("llamas", llamas); tag.putBoolean("rareClaimed", rareClaimed);
            tag.putInt("cleanupDay", cleanupDay); tag.putInt("goatDay", goatDay);
            tag.putLong("cleanupAt", cleanupAt); return tag;
        }
    }
    static WorldState state(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, "lunar_dread_survival");
    }
    static int day(ServerLevel level) {
        return Math.max(1, (int)(level.getServer().overworld().getDayTime() / 24000) + 1);
    }
    static boolean grace(ServerLevel level) {
        return day(level) <= 5 && LunarDread.score(level, "#is_blood") != 1
            && LunarDread.score(level, "#blood_active") != 1;
    }
    static boolean recoveryNight(ServerLevel level) {
        return level.dimension() == Level.OVERWORLD && MoonCycle.night(level)
            && LunarDread.score(level, "#recovering") == 1;
    }
    private static void give(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!player.getPersistentData().getBoolean(STARTER)) {
            give(player, new ItemStack(Items.COOKED_BEEF, 16));
            give(player, new ItemStack(Items.GOLDEN_APPLE));
            player.getPersistentData().putBoolean(STARTER, true);
        }
        if(player.level()==player.getServer().overworld())ensureLlamas(player.getServer().overworld(), player.blockPosition());
    }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(STARTER))
            event.getEntity().getPersistentData().putBoolean(STARTER, true);
    }
    @SubscribeEvent public void respawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.isEndConquered() || !(event.getEntity() instanceof ServerPlayer player)) return;
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 500, 0));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 80, 0));
        player.displayClientMessage(Component.literal("你带回了装备，却还没有缓过来。先找个地方喘口气。"), true);
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void milk(LivingEntityUseItemEvent.Finish event) {
        if (event.getEntity().level().isClientSide || !event.getItem().is(Items.MILK_BUCKET)) return;
        // Deliberately ignores individual mod curative-item restrictions in this pack.
        event.getEntity().removeAllEffects();
        event.getEntity().clearFire(); event.getEntity().setTicksFrozen(0);
        if(event.getEntity() instanceof ServerPlayer player)MoonIncidents.cure(player);
    }
    @SubscribeEvent public void join(EntityJoinLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (recoveryNight(level) && RecoveryNight.hostile(event.getEntity())) {
            event.setCanceled(true); return;
        }
        if (!(event.getEntity() instanceof Enemy)) return;
        if (level.dimension() == Level.OVERWORLD && (grace(level)
            ||MoonCycle.peaceful(level)&&!event.loadedFromDisk())
            && !event.getEntity().getTags().contains("lunar_dread.supply_guard")
            && !event.getEntity().getTags().contains("lunar_dread.goat_trap")
            && !event.getEntity().getTags().contains("lunar_dread.air_mount")
            && !event.getEntity().getTags().contains("lunar_dread.air_rider")
            && !event.getEntity().getTags().contains("lunar_dread.animal_minion"))
            event.setCanceled(true);
    }
    static BlockPos surface(ServerLevel level, int x, int z) {
        if (!level.hasChunk(x >> 4, z >> 4)) return null;
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (!level.getWorldBorder().isWithinBounds(pos) || !level.getFluidState(pos.below()).isEmpty()
            || !level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.above()).canBeReplaced()
            || level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()) return null;
        return pos;
    }
    static void ensureLlamas(ServerLevel level) {
        ensureLlamas(level, level.players().stream().filter(LunarDread::player).findFirst()
            .map(ServerPlayer::blockPosition).orElse(level.getSharedSpawnPos()));
    }
    static void ensureLlamas(ServerLevel level, BlockPos arrival) {
        WorldState state = state(level);
        if (state.llamas >= 2) return;
        BlockPos spawn = arrival;
        // Never force-generate remote chunks. Retry when the spawn area is loaded.
        for (int attempt = 0; attempt < 48 && state.llamas < 2; attempt++) {
            int index = attempt + state.llamas * 17;
            double angle = index * 2.39996323;
            int radius = AnimalCombat.LLAMA_AGGRO_RANGE + 4 + index % 5;
            BlockPos pos = surface(level, spawn.getX() + (int)(Math.cos(angle) * radius), spawn.getZ() + (int)(Math.sin(angle) * radius));
            if (pos == null || Math.abs(pos.getY()-spawn.getY())>6
                || level.players().stream().anyMatch(p -> p.position().distanceToSqr(Vec3.atBottomCenterOf(pos))
                    < Math.pow(AnimalCombat.LLAMA_AGGRO_RANGE+3,2))) continue;
            Llama llama = new Llama(EntityType.LLAMA, level);
            llama.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (!level.noCollision(llama)) continue;
            llama.setCustomName(Component.literal("看什么看，就是你"));
            llama.setCustomNameVisible(true); llama.setPersistenceRequired();
            llama.addTag("lunar_dread.spawn_llama");
            llama.restrictTo(pos, 2);
            llama.getPersistentData().putLong("lunar_dread.arrival_until", level.getGameTime()+1200);
            if (level.addFreshEntity(llama)) { state.llamas++; state.setDirty(); }
        }
    }
    @SubscribeEvent public void death(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer victim && victim.level() instanceof ServerLevel deathLevel
            && !victim.isCreative() && !victim.isSpectator()
            && !(event.getSource().getEntity() instanceof ServerPlayer)
            && !event.getSource().is(DamageTypes.GENERIC_KILL)) {
            ServerLevel world = deathLevel.getServer().overworld();
            MoonCycle.score(world, "#deaths_total", LunarDread.score(world, "#deaths_total") + 1);
            MoonCycle.score(world, "#death_pending", LunarDread.score(world, "#death_pending") + 1);
            MoonCycle.score(world, "#deaths_today", LunarDread.score(world, "#deaths_today") + 1);
        }
        if (!(event.getEntity() instanceof Llama llama) || !(llama.level() instanceof ServerLevel level)
            || !llama.getTags().contains("lunar_dread.spawn_llama")
            || !(event.getSource().getEntity() instanceof ServerPlayer player) || !LunarDread.player(player)) return;
        WorldState state = state(level);
        if (state.rareClaimed) return;
        state.rareClaimed = true; state.setDirty();
        // Shared world reward, claimed before spawning drops, never inherited by offspring.
        llama.spawnAtLocation(new ItemStack(Items.HEART_OF_THE_SEA));
        llama.spawnAtLocation(new ItemStack(Items.DIAMOND, 2));
        player.displayClientMessage(Component.literal("它留下了不该出现在这里的东西。这种好运不会再有了。"), false);
    }
    private static boolean cheap(ItemStack stack) {
        return stack.is(Items.COBBLESTONE) || stack.is(Items.COBBLED_DEEPSLATE) || stack.is(Items.DIRT)
            || stack.is(Items.STONE) || stack.is(Items.DEEPSLATE) || stack.is(Items.NETHERRACK)
            || stack.is(Items.GRAVEL) || stack.is(Items.SAND) || stack.is(Items.TUFF)
            || stack.is(Items.GRANITE) || stack.is(Items.DIORITE) || stack.is(Items.ANDESITE);
    }
    @SubscribeEvent public void drops(BlockDropsEvent event) {
        boolean player = event.getBreaker() instanceof ServerPlayer;
        for (ItemEntity item : event.getDrops()) {
            if (player) item.addTag("lunar_dread.player_drop");
            else if (cheap(item.getItem())) item.addTag("lunar_dread.rubble");
        }
    }
    @SubscribeEvent public void toss(ItemTossEvent event) {
        event.getEntity().addTag("lunar_dread.player_drop");
        event.getEntity().removeTag("lunar_dread.rubble");
    }
    static boolean debris(ItemEntity item) {
        return cheap(item.getItem()) && item.getOwner() == null && !item.getTags().contains("lunar_dread.player_drop")
            && !item.getItem().has(DataComponents.CUSTOM_NAME) && !item.getItem().has(DataComponents.CONTAINER)
            && !item.getItem().isEnchanted() && !item.getTags().contains("lunar_dread.trick_egg");
    }
    static int cleanup(ServerLevel level, int minAge, boolean pressure) {
        List<ItemEntity> candidates = new ArrayList<>();
        int total = 0;
        for (Entity entity : level.getAllEntities()) if (entity instanceof ItemEntity item) {
            total++;
            if (debris(item) && item.getAge() >= minAge
                && level.players().stream().noneMatch(p -> p.isAlive() && p.distanceToSqr(item) < 64)) candidates.add(item);
        }
        candidates.sort(Comparator.comparingInt(ItemEntity::getAge).reversed());
        int count = pressure ? Math.min(Math.max(0, total - 450), candidates.size()) : candidates.size();
        for (int i = 0; i < count; i++) candidates.get(i).discard();
        return count;
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 100 != 0) return;
        ServerLevel world = server.overworld();
        if (server.getPlayerList().getPlayerCount() == 0) return;
        ensureLlamas(world);
        WorldState state = state(world);
        int day = day(world);
        if (day <= 100 && LunarDread.score(world, "#preblood") == 1 && state.cleanupDay != day) {
            state.cleanupDay = day; state.cleanupAt = world.getGameTime() + 1200; state.setDirty();
            server.getPlayerList().broadcastSystemMessage(Component.literal("【清场预告】60秒后清理远处陈旧的碎石泥土。请及时收拾战场，明晚红月。"), false);
        }
        if (state.cleanupAt > 0 && world.getGameTime() >= state.cleanupAt) {
            int cleaned = 0;
            for (ServerLevel level : server.getAllLevels()) cleaned += cleanup(level, 600, false);
            state.cleanupAt = 0; state.setDirty();
            server.getPlayerList().broadcastSystemMessage(Component.literal("【清场完成】已清理 " + cleaned + " 堆远处旧碎屑。箱子、装备和贵重物品不在清理范围。"), false);
        }
        for (ServerLevel level : server.getAllLevels()) cleanup(level, 2400, true);
        if (day <= 100 && day % 3 == 2 && state.goatDay != day) {
            state.goatDay = day; state.setDirty();
            int goats = world.getEntities(EntityType.GOAT, e -> !e.getTags().contains("lunar_dread.blood_herd")).size();
            ServerPlayer target = world.players().stream().filter(LunarDread::player).findFirst().orElse(null);
            if (goats < 6 && target != null) for (int i = 0; i < Math.min(2, 6 - goats); i++) {
                BlockPos pos = surface(world, target.getBlockX() + 20 + i * 3, target.getBlockZ() + 8);
                if (pos == null) continue;
                Goat goat = new Goat(EntityType.GOAT, world); goat.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                goat.addTag("lunar_dread.wanderer"); world.addFreshEntity(goat);
            }
        }
    }
}
