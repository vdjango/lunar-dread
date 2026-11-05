package lunar.dread.client.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import lunar.dread.MoonCycle;
import lunar.dread.client.MoonAtmosphere;
import lunar.dread.client.MoonClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native sky support: keep the resource pack's moon atlas, tint/size only its draw. */
@Mixin(LevelRenderer.class)
public abstract class VanillaMoonMixin {
    private static float partial() { return Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false); }
    @ModifyConstant(method="renderSky",constant=@Constant(floatValue=20.0f))
    private float lunarSize(float original) {
        if(!MoonAtmosphere.current())return original;
        return MoonAtmosphere.kind()==MoonCycle.BLOOD?MoonClient.size(partial()):
            MoonAtmosphere.kind()==MoonCycle.HARVEST?30:MoonAtmosphere.kind()==MoonCycle.BLUE?25:original;
    }
    @Redirect(method="renderSky",at=@At(value="INVOKE",target="Lnet/minecraft/client/multiplayer/ClientLevel;getMoonPhase()I"))
    private int lunarPhase(ClientLevel level) { return MoonAtmosphere.kind()!=MoonCycle.NORMAL?0:level.getMoonPhase(); }
    @Redirect(method="renderSky",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/systems/RenderSystem;setShaderTexture(ILnet/minecraft/resources/ResourceLocation;)V"))
    private void lunarTint(int slot,ResourceLocation texture) {
        RenderSystem.setShaderTexture(slot,texture);
        if(!texture.getPath().equals("textures/environment/moon_phases.png")||!MoonAtmosphere.current())return;
        var mc=Minecraft.getInstance();float p=partial(),angle=mc.level.getTimeOfDay(p)*(float)(Math.PI*2);
        var direction=new org.joml.Vector3f(0,-1,0).rotateX(angle).rotateY(-(float)Math.PI/2).normalize();
        MoonClient.moonDirection=new Vec3(direction.x,direction.y,direction.z);MoonClient.capturedMoon=true;
        if(MoonAtmosphere.kind()==MoonCycle.NORMAL)return;
        var color=MoonAtmosphere.kind()==MoonCycle.BLOOD?MoonClient.color(p):MoonAtmosphere.tint(p);
        float opacity=MoonAtmosphere.nearShell(mc.player)?0:1-mc.level.getRainLevel(p);
        RenderSystem.setShaderColor(color.x,color.y,color.z,opacity);
    }
    @Inject(method="renderSky",at=@At(value="INVOKE",target="Lcom/mojang/blaze3d/vertex/BufferUploader;drawWithShader(Lcom/mojang/blaze3d/vertex/MeshData;)V",ordinal=2,shift=At.Shift.AFTER))
    private void lunarRestore(CallbackInfo callback) {
        float alpha=1-Minecraft.getInstance().level.getRainLevel(partial());
        RenderSystem.setShaderColor(alpha,alpha,alpha,alpha);
    }
}
