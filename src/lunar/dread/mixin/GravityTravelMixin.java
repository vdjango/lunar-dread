package lunar.dread.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lunar.dread.GravityPhysics;

@Mixin(LivingEntity.class)
public abstract class GravityTravelMixin {
    @Inject(method="travel",at=@At("HEAD"),cancellable=true)
    private void weightless(Vec3 input,CallbackInfo ci){if(GravityPhysics.travel((LivingEntity)(Object)this,input))ci.cancel();}
    @Inject(method="jumpFromGround",at=@At("HEAD"),cancellable=true)
    private void noFlightJump(CallbackInfo ci){if(GravityPhysics.acceleration((LivingEntity)(Object)this).lengthSqr()>0)ci.cancel();}
}
