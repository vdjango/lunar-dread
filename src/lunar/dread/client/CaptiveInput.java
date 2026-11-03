package lunar.dread.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import lunar.dread.HorrorMob;

/** Vanilla picking excludes one's own vehicle. Keep left-click counterattacks usable. */
@EventBusSubscriber(modid="lunar_dread",value=Dist.CLIENT)
public final class CaptiveInput {
    @SubscribeEvent public static void attack(InputEvent.InteractionKeyMappingTriggered e){
        var mc=Minecraft.getInstance();
        if(!e.isAttack()||mc.screen!=null||mc.player==null||mc.gameMode==null
            ||!(mc.player.getVehicle() instanceof HorrorMob carrier)||carrier.phase()!=2)return;
        if(mc.hitResult instanceof EntityHitResult hit&&hit.getEntity()!=carrier)return;
        // Native attack packet, reach, cooldown and actual damage remain server checked.
        mc.gameMode.attack(mc.player,carrier);
        mc.player.swing(e.getHand());e.setSwingHand(false);e.setCanceled(true);
    }
}
