package lunar.validation;

import lunar.dread.SurvivalRules;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Real-event assertions in an isolated world; never distributed. */
public final class SurvivalChecks {
    private static Object call(Class<?> type, String name, Class<?>[] signature, Object... args) {
        try { var m = type.getDeclaredMethod(name, signature); m.setAccessible(true); return m.invoke(null, args); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static Object stateField(Object state, String name, Object value) {
        try { var f = state.getClass().getDeclaredField(name); f.setAccessible(true); if (value != null) f.set(state, value); return f.get(state); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void ensure(net.minecraft.server.level.ServerLevel level) {
        call(SurvivalRules.class, "ensureLlamas", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, level);
    }
    private static void check(boolean pass, String why) { if (!pass) throw new IllegalStateException(why); }
    private static ItemEntity item(ServerPlayer p, ItemStack stack, int age, boolean protect, boolean near) {
        var level = p.serverLevel();
        ItemEntity e = new ItemEntity(level, p.getX() + (near ? 1 : 32), p.getY(), p.getZ() + 4, stack);
        CompoundTag tag = e.saveWithoutId(new CompoundTag()); tag.putShort("Age", (short)age); e.load(tag);
        if (protect) e.addTag("lunar_dread.player_drop");
        level.addFreshEntity(e); return e;
    }
    public static void run(ServerPlayer p) {
        var level = p.serverLevel();
        check(p.getInventory().countItem(Items.COOKED_BEEF) == 16, "Starter cooked beef");
        check(p.getInventory().countItem(Items.GOLDEN_APPLE) == 1, "Starter golden apple");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedInEvent(p));
        check(p.getInventory().countItem(Items.COOKED_BEEF) == 16, "Repeat login duplicates starter");
        check(p.saveWithoutId(new CompoundTag()).getCompound("NeoForgeData").getBoolean("lunar_dread.starter_received"), "Starter persistence");
        var clone = net.neoforged.neoforge.common.util.FakePlayerFactory.get(level, new com.mojang.authlib.GameProfile(
            java.util.UUID.fromString("a41dc690-347d-44ce-8be8-230f97baf21a"), "StarterClone"));
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone, p, true));
        check(clone.getPersistentData().getBoolean("lunar_dread.starter_received"), "Starter death clone");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(p, false));
        check(p.getEffect(MobEffects.WEAKNESS).getDuration() == 500, "25 second weakness");
        check(p.getEffect(MobEffects.DARKNESS).getDuration() == 80, "4 second darkness");
        p.removeAllEffects();

        for (var e : level.getEntities(EntityType.LLAMA, e -> e.getTags().contains("lunar_dread.spawn_llama"))) e.discard();
        var state = (SurvivalRules.WorldState)call(SurvivalRules.class, "state", new Class<?>[]{net.minecraft.server.level.ServerLevel.class}, level);
        stateField(state, "llamas", 0); stateField(state, "rareClaimed", false);
        level.setDefaultSpawnPos(p.blockPosition(), 0);
        ensure(level); ensure(level);
        var llamas = level.getEntities(EntityType.LLAMA, e -> e.getTags().contains("lunar_dread.spawn_llama"));
        check(llamas.size() == 2 && stateField(state, "llamas", null).equals(2), "Exactly two initial llamas");
        check(llamas.stream().allMatch(e->e.distanceToSqr(p)>=27*27&&e.distanceToSqr(p)<=34*34&&e.getTarget()==null),"Starter pair must be visible but beyond their twenty-four-block acquisition range");
        check(llamas.stream().allMatch(e -> e.hasCustomName() && e.getCustomName().getString().equals("看什么看，就是你")
            && e.isCustomNameVisible()), "Visible llama names");
        for (var llama : llamas) llama.hurt(level.damageSources().playerAttack(p), 1000);
        var hearts = level.getEntities(EntityType.ITEM, e -> e.getItem().is(Items.HEART_OF_THE_SEA));
        check(hearts.size() == 1 && stateField(state, "rareClaimed", null).equals(true), "Rare reward shared once");
        ensure(level);
        check(level.getEntities(EntityType.LLAMA, e -> e.isAlive() && e.getTags().contains("lunar_dread.spawn_llama")).isEmpty(), "Killed pair must not respawn");
        var restored = call(SurvivalRules.WorldState.class, "load", new Class<?>[]{CompoundTag.class, net.minecraft.core.HolderLookup.Provider.class},
            state.save(new CompoundTag(), level.registryAccess()), level.registryAccess());
        check(stateField(restored, "llamas", null).equals(2) && stateField(restored, "rareClaimed", null).equals(true), "Llama saved-data roundtrip");

        ItemEntity rubble = item(p, new ItemStack(Items.COBBLESTONE), 1200, false, false);
        ItemEntity diamond = item(p, new ItemStack(Items.DIAMOND), 1200, false, false);
        ItemEntity protectedDrop = item(p, new ItemStack(Items.COBBLESTONE), 1200, true, false);
        ItemStack named = new ItemStack(Items.COBBLESTONE); named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep"));
        ItemEntity namedDrop = item(p, named, 1200, false, false);
        ItemEntity nearby = item(p, new ItemStack(Items.DIRT), 1200, false, true);
        ItemEntity young = item(p, new ItemStack(Items.DIRT), 1, false, false);
        check((int)call(SurvivalRules.class, "cleanup", new Class<?>[]{net.minecraft.server.level.ServerLevel.class, int.class, boolean.class}, level, 600, false) >= 1 && rubble.isRemoved(), "Old rubble cleanup");
        check(!diamond.isRemoved() && !protectedDrop.isRemoved() && !namedDrop.isRemoved()
            && !nearby.isRemoved() && !young.isRemoved(), "Cleanup must protect loot and near/recent drops");
        ItemEntity tossed = item(p, new ItemStack(Items.DIRT), 1200, false, false);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.item.ItemTossEvent(tossed, p));
        check(tossed.getTags().contains("lunar_dread.player_drop"), "Actual toss event protection");
        var cow = EntityType.COW.create(level);
        p.getPersistentData().putInt("lunar_dread.noise", 0);
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(p, cow));
        check(p.getPersistentData().getInt("lunar_dread.noise") == 3, "Combat noise event");
        NeoForge.EVENT_BUS.post(new net.neoforged.neoforge.event.level.BlockEvent.BreakEvent(level, p.blockPosition().below(),
            level.getBlockState(p.blockPosition().below()), p));
        check(p.getPersistentData().getInt("lunar_dread.noise") == 7, "Mining noise event");
        p.getPersistentData().putInt("lunar_dread.noise", 0);
    }
}
