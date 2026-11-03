package lunar.dread.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lunar.dread.MoonGravity;

@Mixin(FishingHook.class)
public abstract class FishingRescueMixin {
    @Inject(method="pullEntity",at=@At("TAIL"))
    private void rescue(Entity target,CallbackInfo ci){MoonGravity.hooked(target);lunar.dread.AbductionController.hooked(target);}
}
