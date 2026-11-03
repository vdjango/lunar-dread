package lunar.dread.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.smileycorp.hordes.common.capability.HordesCapabilities;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Orphaned horde ownership must not crash vanilla zombie/drowned conversions. */
@Mixin(targets="net.smileycorp.hordes.hordeevent.capability.HordeEvent",remap=false)
public abstract class HordeConversionGuardMixin {
    @Inject(method="registerEntity",at=@At("HEAD"),cancellable=true,remap=false)
    private void lunarDreadMissingOwner(Mob mob,ServerPlayer player,CallbackInfo callback){
        if(player!=null)return;
        var ownership=mob.getCapability(HordesCapabilities.HORDESPAWN);
        if(ownership!=null)ownership.setPlayerUUID("");
        callback.cancel();
    }
}
