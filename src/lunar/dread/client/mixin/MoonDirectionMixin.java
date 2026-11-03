package lunar.dread.client.mixin;

import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.povstalec.stellarview.client.resourcepack.ViewCenter;
import net.povstalec.stellarview.common.util.SphericalCoords;
import net.povstalec.stellarview.common.util.TextureLayer;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import lunar.dread.client.MoonClient;

@Mixin(targets = "net.povstalec.stellarview.client.render.space_objects.resourcepack.MoonRenderer", remap = false)
public abstract class MoonDirectionMixin {
    @ModifyVariable(method = "renderTextureLayer", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private long fullMoon(long ticks) {
        return MoonClient.active(net.minecraft.client.Minecraft.getInstance().level) ? 0 : ticks;
    }
    @Inject(method = "renderTextureLayer", at = @At("HEAD"))
    private void direction(TextureLayer texture, ViewCenter view, ClientLevel level, Camera camera, Tesselator tess,
                           Matrix4f matrix, SphericalCoords coords, double distance, long ticks, double brightness, float partial, CallbackInfo ci) {
        // Stellar View's lunar orbit can place the moon below the horizon at night
        // on particular dates. Event moons follow a visible nocturnal arc instead.
        // Change the coordinates actually rendered, then capture that same position.
        if(lunar.dread.client.MoonAtmosphere.kind()!=lunar.dread.MoonCycle.NORMAL){
            double angle=(Math.floorMod(level.getDayTime(),24000)-12000+partial)*Math.PI/12000;
            var local=new org.joml.Vector3f((float)Math.cos(angle),(float)Math.sin(angle),.16f).normalize();
            camera.rotation().conjugate(new org.joml.Quaternionf()).transform(local);
            new Matrix4f(matrix).invert().transformDirection(local);
            local.normalize().mul((float)coords.r);coords.fromCartesian(local);
        }
        MoonClient.capture(SphericalCoords.sphericalToCartesianF(coords), matrix, camera);
    }
}
