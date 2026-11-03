package lunar.dread;

import java.util.ArrayList;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Recovery nights really clear loaded hostiles and interrupt animal attacks. */
public final class RecoveryNight {
    private final WeakHashMap<ServerLevel,Integer> clearedDay = new WeakHashMap<>();

    static boolean hostile(Entity entity) {
        if(entity instanceof Enemy || entity.getType().getCategory()==MobCategory.MONSTER)return true;
        if(entity.getTags().contains("lunar_dread.hostile_tnt") || entity.getTags().contains("lunar_dread.sheep_tnt")
            || entity.getTags().contains("lunar_dread.trick_egg"))return true;
        return entity instanceof Projectile shot && (shot.getOwner() instanceof Enemy || shot.getOwner() instanceof Animal);
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void target(LivingChangeTargetEvent event) {
        if(event.getEntity() instanceof Animal && event.getEntity().level() instanceof ServerLevel level
            && SurvivalRules.recoveryNight(level))event.setNewAboutToBeSetTarget(null);
    }
    @SubscribeEvent public void damage(LivingIncomingDamageEvent event) {
        if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player
            && SurvivalRules.recoveryNight(player.serverLevel())
            && event.getSource().getEntity() instanceof Animal)event.setCanceled(true);
    }
    @SubscribeEvent public void tick(ServerTickEvent.Post event) {
        ServerLevel level=event.getServer().overworld();
        if(!SurvivalRules.recoveryNight(level))return;
        int day=SurvivalRules.day(level);
        if(clearedDay.getOrDefault(level,-1)==day && level.getGameTime()%20!=0)return;
        clearedDay.put(level,day);
        var hostiles=new ArrayList<Entity>();
        for(var entity:level.getAllEntities()){
            if(hostile(entity)){
                hostiles.add(entity);
            }else if(entity instanceof Animal animal){
                var data=animal.getPersistentData();
                animal.setTarget(null);
                animal.setLastHurtByMob(null);
                animal.getNavigation().stop();
                data.putBoolean("lunar_dread.pursuing",false);
                data.putInt("lunar_dread.skill_windup",0);
                data.putInt("lunar_dread.windup",0);
                data.putInt("lunar_dread.charge_ticks",0);
                data.remove("lunar_dread.melee_phase");
                data.remove("lunar_dread.retaliate_target");
                if(animal.removeTag("lunar_dread.preparing"))level.broadcastEntityEvent(animal,(byte)59);
                animal.removeTag("lunar_dread.ramming");
            }
        }
        for(var entity:hostiles)entity.discard();
    }
}
