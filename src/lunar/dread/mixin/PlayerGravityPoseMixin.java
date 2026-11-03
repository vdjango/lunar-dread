package lunar.dread.mixin;

import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lunar.dread.GravityPhysics;

/** Compact physical pose allows a drifting player to be reeled through a window, not an upright doorway only. */
@Mixin(Player.class)
public abstract class PlayerGravityPoseMixin {
    @Inject(method="updatePlayerPose",at=@At("HEAD"),cancellable=true)
    private void lunarPosture(CallbackInfo ci){var p=(Player)(Object)this;if(GravityPhysics.tumbling(p)||p.getVehicle() instanceof lunar.dread.HorrorMob){p.setPose(Pose.SWIMMING);ci.cancel();}}
}
