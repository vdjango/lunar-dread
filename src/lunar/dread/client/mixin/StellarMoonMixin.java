package lunar.dread.client.mixin;

import dev.corgitaco.enhancedcelestials.EnhancedCelestials;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.povstalec.stellarview.common.util.Color;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import lunar.dread.client.MoonClient;
import lunar.dread.client.MoonAtmosphere;

@Mixin(targets = "net.povstalec.stellarview.compatibility.enhancedcelestials.EnhancedCelestialsCompatibility", remap = false)
public abstract class StellarMoonMixin {
    @Inject(method = "getMoonSize", at = @At("HEAD"), cancellable = true)
    private static void size(ClientLevel level, float original, CallbackInfoReturnable<Float> cir) {
        // Stellar View 0.5.3 calls a method absent from EC 6.0.2.6. Use the
        // actual pinned forecast API for every moon, including normal nights.
        float stock = EnhancedCelestials.lunarForecastWorldData(level).map(data -> Mth.clampedLerp(
            data.lastLunarEvent().getClientSettings().moonSize(), data.currentLunarEvent().getClientSettings().moonSize(), data.getBlend())).orElse(original);
        float partial=net.minecraft.client.Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        if(MoonAtmosphere.current()&&MoonAtmosphere.kind()==lunar.dread.MoonCycle.BLOOD)cir.setReturnValue(MoonClient.size(partial));
        else {
            boolean ordinary=MoonAtmosphere.current()&&MoonAtmosphere.kind()==0&&Math.floorMod(level.getDayTime(),24000)>=13000;
            cir.setReturnValue(ordinary?stock*(1+.003f*(float)Math.sin(MoonAtmosphere.clock(partial)*Math.PI/28)):stock);
        }
    }
    @Inject(method = "getMoonColor", at = @At("HEAD"), cancellable = true)
    private static void color(ClientLevel level, float partial, CallbackInfoReturnable<Color.FloatRGBA> cir) {
        if (MoonAtmosphere.current()&&(MoonAtmosphere.special()||MoonAtmosphere.kind()>=3&&MoonAtmosphere.kind()<=5)) {
            var c=MoonAtmosphere.kind()==lunar.dread.MoonCycle.BLOOD?MoonClient.color(partial):MoonAtmosphere.tint(partial);
            var mc=net.minecraft.client.Minecraft.getInstance();
            float alpha=MoonAtmosphere.nearShell(mc.player)?0:1;
            cir.setReturnValue(new Color.FloatRGBA(c.x,c.y,c.z,alpha));
        }
    }
    @Inject(method = "adjustLightmapColors", at = @At("TAIL"))
    private static void light(ClientLevel level, float partial, float sky, float block, float flicker, int x, int y, Vector3f light, CallbackInfo ci) {
        if (!MoonAtmosphere.special()) return;
        var c = MoonAtmosphere.tint(partial);
        // Keep torches usable: tint increases with skylight and decreases with block light.
        float strength=MoonAtmosphere.giant()?.8f:.52f;
        float weight=Math.min(strength,x/15f*(1-y/18f)*strength);
        float grey=(light.x+light.y+light.z)/3;
        light.lerp(new Vector3f(grey,grey,grey),weight*.55f);
        light.mul(1-weight*.24f,1-weight*.86f,1-weight*.6f).add(c.mul(weight*.065f));
    }
}
