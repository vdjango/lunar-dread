package lunar.dread;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual mining, combat and movement feed hearing; dropped flesh offers a short decoy. */
public final class ThreatSenses {
    static void noise(ServerPlayer player, int amount) {
        if (!LunarDread.player(player)) return;
        var d = player.getPersistentData();
        d.putInt("lunar_dread.noise", Math.min(24, d.getInt("lunar_dread.noise") + amount));
    }
    @SubscribeEvent public void broken(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) noise(player, player.isShiftKeyDown() ? 1 : 4);
    }
    @SubscribeEvent public void placed(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) noise(player, player.isShiftKeyDown() ? 1 : 2);
    }
    @SubscribeEvent public void attack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) noise(player, 3);
    }
    static boolean baited(Mob mob) {
        return mob.getPersistentData().getLong("lunar_dread.bait_until") > mob.level().getGameTime();
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        var server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!LunarDread.player(player)) continue;
            ServerLevel level = player.serverLevel();
            if (!AnimalCombat.enabled(level)) continue;
            var d = player.getPersistentData();
            long now = level.getGameTime();
            Vec3 previous = new Vec3(d.getDouble("lunar_dread.last_x"), d.getDouble("lunar_dread.last_y"), d.getDouble("lunar_dread.last_z"));
            double movement = previous.distanceToSqr(player.position());
            d.putDouble("lunar_dread.last_x", player.getX()); d.putDouble("lunar_dread.last_y", player.getY()); d.putDouble("lunar_dread.last_z", player.getZ());
            if (movement < 400 && movement > 0.3 && !player.isShiftKeyDown()) {
                if (player.isSprinting()) noise(player, 3);
                else if (!player.onGround()) noise(player, 2);
                else if (server.getTickCount() % 40 == 0) noise(player, 1);
            }
            int noise = d.getInt("lunar_dread.noise");
            boolean flesh = player.getInventory().countItem(Items.ROTTEN_FLESH) > 0;
            boolean wounded = player.getHealth() <= 8;
            boolean moon = MoonDread.marked(player);
            int range = Math.min(80, 16 + noise * 2 + (flesh ? 24 : 0) + (wounded ? 16 : 0) + (moon ? 32 : 0) + SurvivalRules.day(level) / 5);
            var mobs = level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(range),
                m -> !m.isNoAi() && m.isAlive() && m instanceof Enemy);
            var baits = level.getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(48),
                e -> e.isAlive() && e.getItem().is(Items.ROTTEN_FLESH) && e.getAge() < 200
                    && e.getTags().contains("lunar_dread.player_drop"));
            int processed = 0;
            for (Mob mob : mobs) {
                if (++processed > 80) break;
                if(BloodHuntDirector.assigned(mob)||mob instanceof HorrorMob)continue;
                ItemEntity bait = null;
                double closest = 24 * 24;
                for (ItemEntity item : baits) if (mob.distanceToSqr(item) < closest) {
                    closest = mob.distanceToSqr(item); bait = item;
                }
                if (bait != null && mob instanceof Enemy) {
                    mob.getPersistentData().putLong("lunar_dread.bait_until", now + 25);
                    mob.setTarget(null);
                    mob.getNavigation().moveTo(bait.getX(), bait.getY(), bait.getZ(), 1.25);
                    if (closest < 2) bait.discard();
                    continue;
                }
                if (noise < 3 && !flesh && !wounded && !moon) continue;
                if (mob.getTarget() != null && mob.getTarget() != player && LunarDread.player(mob.getTarget())
                    && mob.distanceToSqr(mob.getTarget()) < mob.distanceToSqr(player)) continue;
                mob.setTarget(player);
                mob.getNavigation().moveTo(player, 1.25);
            }
            // Cave encounters now belong to HorrorDirector's shared week budget.
            d.remove("lunar_dread.echo_at");
            d.putInt("lunar_dread.noise", Math.max(0, noise - 1));
        }
    }
}
