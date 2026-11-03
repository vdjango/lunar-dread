package lunar.dread.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import lunar.dread.MoonNetwork;
import net.neoforged.neoforge.network.PacketDistributor;

/** All effects are computed per frame; no mutation of EC settings or original moon assets. */
@EventBusSubscriber(modid = "lunar_dread", value = Dist.CLIENT)
public final class MoonClient {
    private static MoonNetwork.State state;
    private static ClientLevel receivedLevel;
    private static long receivedAt;
    public static Vec3 moonDirection = new Vec3(0, 1, 0);
    public static boolean capturedMoon;
    private static MoonNetwork.Dread dread = new MoonNetwork.Dread(0, 0);
    private static ClientLevel dreadLevel;
    private static long dreadAt;
    public static void receiveDread(MoonNetwork.Dread value) {
        dread = value; dreadLevel = Minecraft.getInstance().level;
        dreadAt = dreadLevel == null ? 0 : dreadLevel.getGameTime();
    }
    public static int gaze() { return personal() ? dread.gaze() : 0; }
    public static int mark() { return personal() ? dread.mark() : 0; }
    private static boolean personal() {
        var level = Minecraft.getInstance().level;
        return active(level) && level == dreadLevel && level.getGameTime() - dreadAt <= 40;
    }
    @SubscribeEvent public static void attention(ClientTickEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || !active(mc.level) || mc.player.tickCount % 4 != 0) return;
        boolean looking = capturedMoon && mc.screen == null && !mc.player.isCreative() && !mc.player.isSpectator()
            && moonDirection.y > 0 && mc.player.getLookAngle().dot(moonDirection) > Math.cos(Math.toRadians(12));
        if (looking) {
            var from = mc.player.getEyePosition();
            var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(from, from.add(moonDirection.scale(192)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
            looking = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
        }
        PacketDistributor.sendToServer(new MoonNetwork.Gaze(looking));
    }
    public static void receive(MoonNetwork.State value) {
        state = value; receivedLevel = Minecraft.getInstance().level;
        receivedAt = receivedLevel == null ? 0 : receivedLevel.getGameTime();
    }
    public static boolean active(ClientLevel level) {
        return level != null && level == receivedLevel && level.dimension() == Level.OVERWORLD
            && state != null && state.active() && Math.floorMod(level.getDayTime(), 24000) >= 13000
            && level.getGameTime() - receivedAt <= 100;
    }
    public static int phase() { return state == null ? 0 : state.phase(); }
    public static double time(float partial) {
        var level = Minecraft.getInstance().level;
        return state == null || level == null ? 0 : state.tick() + level.getGameTime() - receivedAt + partial;
    }
    public static float size(float partial) {
        return MoonAtmosphere.bloodSize(partial);
    }
    public static Vector3f color(float partial) {
        if (phase() == 1) return new Vector3f(0.68f, 1, 0.76f);
        if (phase() == 2) return new Vector3f(1, 0.94f, 0.58f);
        if (phase() == 3) return new Vector3f(1, 0.68f, 0.23f);
        float breath = (float)((Math.sin(time(partial) * Math.PI * 2 / 140) + 1) * 0.5);
        return new Vector3f(0.34f + breath * 0.59f, 0.008f + breath * 0.035f, 0.025f + breath * 0.085f);
    }
    public static void capture(Vector3f local, Matrix4f modelView, net.minecraft.client.Camera camera) {
        Vector3f view = modelView.transformDirection(new Vector3f(local).normalize());
        camera.rotation().transform(view);
        if (Float.isFinite(view.x) && view.lengthSquared() > 0.00001) {
            view.normalize(); moonDirection = new Vec3(view.x, view.y, view.z); capturedMoon = true;
        }
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null; receivedLevel = null; capturedMoon = false;
        dread = new MoonNetwork.Dread(0, 0); dreadLevel = null;
    }
    @SubscribeEvent public static void fog(ViewportEvent.ComputeFogColor event) {
        // World fog is composed once by MoonAtmosphere, separately from the moon face.
    }
    @SubscribeEvent public static void vignette(RenderGuiEvent.Post event) {
        if (!active(Minecraft.getInstance().level) || Minecraft.getInstance().options.hideGui || phase()==0) return;
        var gui = event.getGuiGraphics(); int w = gui.guiWidth(), h = gui.guiHeight();
        var color = color(0); int alpha = phase() == 0 && gaze() < 40 && mark() == 0 ? 22 : 52;
        int tint = alpha << 24 | ((int)(color.x * 255) << 16) | ((int)(color.y * 255) << 8) | (int)(color.z * 255);
        gui.fillGradient(0, 0, w, h / 4, tint, 0);
        gui.fillGradient(0, h * 3 / 4, w, h, 0, tint);
    }
    private static void vertex(BufferBuilder b, Matrix4f m, Vec3 p, Vector3f color, float alpha) {
        b.addVertex(m, (float)p.x, (float)p.y, (float)p.z).setColor(color.x, color.y, color.z, alpha);
    }
    static void quad(BufferBuilder b, Matrix4f m, Vec3 a, Vec3 c, Vec3 d, Vec3 e, Vector3f color, float alpha) {
        vertex(b,m,a,color,alpha); vertex(b,m,c,color,alpha); vertex(b,m,d,color,alpha); vertex(b,m,e,color,alpha);
    }
    static void ring(BufferBuilder b, Matrix4f m, Vec3 center, double radius, Vector3f color, float alpha) {
        for (int i = 0; i < 64; i++) {
            double a = i * Math.PI / 32, c = (i + 1) * Math.PI / 32;
            quad(b, m, center.add(Math.cos(a)*radius,0,Math.sin(a)*radius),
                center.add(Math.cos(c)*radius,0,Math.sin(c)*radius),
                center.add(Math.cos(c)*(radius-.11),0,Math.sin(c)*(radius-.11)),
                center.add(Math.cos(a)*(radius-.11),0,Math.sin(a)*(radius-.11)), color, alpha);
        }
    }
    static void ray(BufferBuilder b, Matrix4f m, Vec3 from, Vec3 end, double width, Vector3f color, float alpha) {
        Vec3 axis = from.subtract(end).normalize();
        Vec3 side = axis.cross(new Vec3(0, 1, 0));
        if (side.lengthSqr() < 0.001) side = new Vec3(1, 0, 0);
        side = side.normalize().scale(width);
        Vec3 other = axis.cross(side).normalize().scale(width);
        quad(b,m,from.add(side),end.add(side),end.subtract(side),from.subtract(side),color,alpha);
        quad(b,m,from.add(other),end.add(other),end.subtract(other),from.subtract(other),color,alpha);
    }
    @SubscribeEvent public static void beams(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !active(Minecraft.getInstance().level)
            || state.spots().isEmpty()) return;
        Vec3 camera = event.getCamera().getPosition();
        // AFTER_PARTICLES already has the camera rotation in RenderSystem's
        // model-view stack. Applying the event model-view again rotates twice.
        Matrix4f matrix = event.getPoseStack().last().pose();
        var color = color(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        RenderSystem.enableBlend(); RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA, com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull(); RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        try {
            BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (var spot : state.spots()) {
                Vec3 center = spot.aim().subtract(camera).add(0, 0.08, 0);
                ring(b,matrix,center,3,color,0.8f);
                ring(b,matrix,center.add(0,0.03,0),2.65 + 0.12 * Math.sin(time(0)*.15),color,0.42f);
                Vec3 from = moonDirection.scale(180);
                Vec3 end = (phase() == 3 ? spot.impact() : spot.aim().add(0,1,0)).subtract(camera);
                if (phase() == 3) {
                    ray(b,matrix,from,end,.65,color,.26f);
                    ray(b,matrix,from,end,.12,new Vector3f(1,1,.88f),.95f);
                } else ray(b,matrix,from,end,.025,color,phase() == 2 ? .65f : .2f);
            }
            BufferUploader.drawWithShader(b.buildOrThrow());
        } finally {
            RenderSystem.depthMask(true); RenderSystem.enableCull(); RenderSystem.defaultBlendFunc(); RenderSystem.disableBlend();
            RenderSystem.setShaderColor(1,1,1,1);
        }
    }
}
