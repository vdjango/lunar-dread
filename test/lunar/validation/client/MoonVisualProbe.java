package lunar.validation.client;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import lunar.dread.client.MoonClient;

/** Opt-in visual harness, compiled only into the isolated validation fixture. */
@EventBusSubscriber(modid="lunar_validation", value=Dist.CLIENT)
public final class MoonVisualProbe {
    private static boolean connecting;
    private static String captured="";
    private static String pending="";
    private static boolean ready;
    private static String lastScreen="";
    private static int remaining, index, interval, untilCapture;
    private static String sequence="";
    private static String delayed="";
    private static int cameraSettle;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String address=System.getProperty("lunar_dread.visual.address");
        String world=System.getProperty("lunar_dread.visual.world");
        if (address==null&&world==null) return;
        var mc=Minecraft.getInstance();
        mc.options.pauseOnLostFocus=false;
        if (mc.level==null) {
            String screen=mc.screen==null ? "null" : mc.screen.getClass().getName();
            if (!screen.equals(lastScreen)) { lastScreen=screen; System.out.println("MOON_PROBE_SCREEN "+screen); }
            if(world!=null&&mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen confirm){
                try{
                    var field=net.minecraft.client.gui.screens.BackupConfirmScreen.class.getDeclaredField("onProceed");field.setAccessible(true);
                    ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener)field.get(confirm)).proceed(false,false);
                }catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
                return;
            }
            if (!connecting && mc.screen instanceof TitleScreen) {
                connecting=true;
                if(world!=null)mc.createWorldOpenFlows().openWorld(world,()->{});
                else ConnectScreen.startConnecting(mc.screen,mc,ServerAddress.parseString(address),
                        new ServerData("Moon visual check",address,ServerData.Type.OTHER),false,null);
            }
            return;
        }
        if (mc.player==null || !MoonClient.capturedMoon) return;
        // Dismiss upstream update announcements in this opt-in photography
        // instance so screenshots show the actual world instead of a modal.
        if (mc.screen!=null) mc.setScreen(null);
        if (!ready) { ready=true; System.out.println("MOON_PROBE_READY"); }
        try {
            Path path=Path.of(System.getProperty("lunar_dread.visual.control"));
            if (!Files.exists(path)) return;
            var control=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String label=control.get("label").getAsString();
            String aim=control.get("aim").getAsString();
            mc.options.hideGui=control.has("clean") && control.get("clean").getAsBoolean();
            if(control.has("fov"))mc.options.fov().set(control.get("fov").getAsInt());
            if(control.has("camera"))mc.options.setCameraType(net.minecraft.client.CameraType.values()[control.get("camera").getAsInt()]);
            if(control.has("width"))org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),control.get("width").getAsInt(),control.get("height").getAsInt());
            if (aim.equals("moon")) {
                var d=MoonClient.moonDirection;
                mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
                mc.player.setXRot((float)-Math.toDegrees(Math.asin(d.y)));
            } else if (aim.equals("ground")) { mc.player.setXRot(25); }
            else if (aim.equals("impact")) { mc.player.setYRot(135); mc.player.setXRot(12); }
            else if (aim.equals("look")) {
                var point=control.getAsJsonArray("look");
                var d=new net.minecraft.world.phys.Vec3(point.get(0).getAsDouble(),point.get(1).getAsDouble(),point.get(2).getAsDouble()).subtract(mc.player.getEyePosition());
                mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z)));
                mc.player.setXRot((float)-Math.toDegrees(Math.atan2(d.y,Math.sqrt(d.x*d.x+d.z*d.z))));
            }
            if (label.equals(captured) || label.isEmpty()) {
                if (!delayed.isEmpty() && --cameraSettle<=0) { pending=delayed; delayed=""; }
                if (remaining>0 && --untilCapture<=0) {
                    pending=sequence+String.format("_%03d",index++);
                    --remaining; untilCapture=interval;
                }
                return;
            }
            captured=label;
            if(control.has("strike")&&control.get("strike").getAsBoolean()){
                var trigger=new net.neoforged.neoforge.client.event.InputEvent.InteractionKeyMappingTriggered(0,mc.options.keyAttack,net.minecraft.world.InteractionHand.MAIN_HAND);
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(trigger);
                System.out.println("CAPTIVE_CLIENT_INPUT intercepted="+trigger.isCanceled()+" carrier="+(mc.player.getVehicle() instanceof lunar.dread.HorrorMob));
            }
            float size=net.povstalec.stellarview.compatibility.enhancedcelestials.EnhancedCelestialsCompatibility.getMoonSize(mc.level,20);
            System.out.println("MOON_VISUAL_FRAME "+label+" active="+MoonClient.active(mc.level)+" phase="+MoonClient.phase()+" size="+size+" dir="+MoonClient.moonDirection+" gaze="+MoonClient.gaze()+" mark="+MoonClient.mark());
            System.out.println("LUNAR_CLIENT "+label+" pos="+mc.player.position()+" velocity="+mc.player.getDeltaMovement()+" force="+lunar.dread.client.MoonAtmosphere.force(mc.player)+" tumble="+lunar.dread.client.MoonAtmosphere.tumbleAge(mc.player,0)+" pose="+mc.player.getPose()+" blackout="+lunar.dread.client.MoonAtmosphere.blackout(0));
            dev.corgitaco.enhancedcelestials.EnhancedCelestials.lunarForecastWorldData(mc.level).ifPresent(data->System.out.println("LUNAR_SKY "+label+" event="+data.currentLunarEventHolder().unwrapKey()+" blend="+data.getBlend()+" sky="+mc.level.getSkyColor(mc.player.getEyePosition(),0)+" time="+mc.level.getDayTime()));
            if (control.has("frames")) {
                sequence=label; remaining=control.get("frames").getAsInt(); index=0;
                interval=control.has("interval") ? control.get("interval").getAsInt() : 2;
                untilCapture=1;
            } else { remaining=0; delayed=label; cameraSettle=2; }
        } catch (Exception e) { throw new IllegalStateException("Visual harness",e); }
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event) {
        if (pending.isEmpty()) return;
        String label=pending; pending="";
        var mc=Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty("lunar_dread.visual.output")).toFile(),label+".png",mc.getMainRenderTarget(),message -> System.out.println("MOON_VISUAL_SAVED "+label));
    }
}
