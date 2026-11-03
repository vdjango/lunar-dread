package lunar.dread;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class AbductionController {
    private static final Map<UUID,UUID> active=new HashMap<>();
    public static boolean busy(ServerPlayer p){return p.getVehicle() instanceof HorrorMob;}
    static boolean available(ServerLevel l){return active.isEmpty();}
    static boolean capture(HorrorMob m,ServerPlayer p){
        if(!available(p.serverLevel())||MoonIncidents.busy(p)||p.isPassenger()||!m.hasLineOfSight(p)||m.distanceToSqr(p)>3.2*3.2)return false;
        if(!p.startRiding(m,true))return false;active.put(p.getUUID(),m.getUUID());m.captured(p);return true;
    }
    public static void release(ServerPlayer p,boolean hooked){
        UUID id=active.remove(p.getUUID());Entity vehicle=p.getVehicle();
        if(vehicle instanceof HorrorMob m){var velocity=p.getDeltaMovement();p.stopRiding();m.released();if(hooked)p.setDeltaMovement(velocity);}
        if(id!=null||vehicle instanceof HorrorMob){MoonIncidents.protect(p,160);p.hurtMarked=true;}
    }
    public static void hooked(Entity entity){
        if(entity instanceof ServerPlayer p){p.getPersistentData().remove("lunar_dread.rope_lock");release(p,true);}
        else if(entity instanceof HorrorMob m){m.getPersistentData().remove("lunar_dread.rope_until");if(m.getTarget() instanceof ServerPlayer p)p.getPersistentData().remove("lunar_dread.rope_lock");if(m.getFirstPassenger() instanceof ServerPlayer p)release(p,true);}
    }
    @SubscribeEvent public void attack(AttackEntityEvent e){if(e.getEntity() instanceof ServerPlayer p&&p.getAttackStrengthScale(.5f)>=.8f)p.getPersistentData().putLong("lunar_dread.valid_swing",p.level().getGameTime());}
    @SubscribeEvent public void tick(ServerTickEvent.Post e){
        for(var id:java.util.List.copyOf(active.keySet())){
            var p=e.getServer().getPlayerList().getPlayer(id);
            if(p==null){active.remove(id);continue;}
            if(!LunarDread.player(p)||!(p.getVehicle() instanceof HorrorMob)||p.getVehicle().isRemoved())release(p,false);
        }
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)release(p,false);}
    @SubscribeEvent public void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)release(p,false);}
    @SubscribeEvent public void stopped(ServerStoppedEvent e){active.clear();}
}
