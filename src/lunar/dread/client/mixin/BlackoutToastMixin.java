package lunar.dread.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lunar.dread.client.MoonAtmosphere;

/** Toasts render after the HUD; retain them, but hide them during total darkness. */
@Mixin(ToastComponent.class)
public abstract class BlackoutToastMixin {
    @Inject(method="render",at=@At("HEAD"),cancellable=true)
    private void lunarDread$darkness(GuiGraphics graphics,CallbackInfo ci){
        var mc=Minecraft.getInstance();
        if(MoonAtmosphere.blackout(mc.getTimer().getGameTimeDeltaPartialTick(false))>=1)ci.cancel();
    }
}
