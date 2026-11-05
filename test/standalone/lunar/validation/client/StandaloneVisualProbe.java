package lunar.validation.client;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import lunar.dread.client.MoonClient;
import lunar.dread.client.MoonAtmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Only loaded by the isolated client verification mod, never by the release. */
@EventBusSubscriber(modid="lunar_standalone_validation",value=Dist.CLIENT)
public final class StandaloneVisualProbe {
    private static boolean opening,ready;
    private static String captured="",pending="";
    private static int settle,frames,index,ticks,interval;
    private static float pitchOffset,pan;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        String world=System.getProperty("lunar_dread.standalone.world");if(world==null)return;
        var mc=Minecraft.getInstance();mc.options.pauseOnLostFocus=false;
        if(mc.level==null) {
            if(mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen confirm) {
                try {
                    var field=confirm.getClass().getDeclaredField("onProceed");field.setAccessible(true);
                    ((net.minecraft.client.gui.screens.BackupConfirmScreen.Listener)field.get(confirm)).proceed(false,false);
                }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            }else if(!opening&&mc.screen instanceof TitleScreen){opening=true;mc.createWorldOpenFlows().openWorld(world,()->{});}
            return;
        }
        if(mc.player==null)return;
        if(mc.screen!=null)mc.setScreen(null);
        if(!ready){ready=true;System.out.println("STANDALONE_CLIENT_READY");}
        try {
            Path path=Path.of(System.getProperty("lunar_dread.standalone.visual"));if(!Files.exists(path))return;
            var data=JsonParser.parseString(Files.readString(path)).getAsJsonObject();String label=data.get("label").getAsString();
            if(label.isEmpty())return;
            mc.options.hideGui=true;
            if(!label.equals(captured)) {
                captured=label;ticks=0;index=0;
                frames=data.has("frames")?data.get("frames").getAsInt():0;
                interval=data.has("interval")?data.get("interval").getAsInt():2;
                pitchOffset=data.has("pitchOffset")?data.get("pitchOffset").getAsFloat():0;
                pan=data.has("pan")?data.get("pan").getAsFloat():0;
                if(data.has("fov"))mc.options.fov().set(data.get("fov").getAsInt());
                if(frames==0){pending=label;settle=12;}
                System.out.println("STANDALONE_FRAME "+label+" kind="+MoonAtmosphere.kind()+" size="+MoonClient.size(0)+" captured="+MoonClient.capturedMoon+" dir="+MoonClient.moonDirection);
            }
            var d=MoonClient.moonDirection;
            double sweep=frames>1?pan*Math.sin(Math.PI*2*index/(frames-1)):0;
            mc.player.setYRot((float)Math.toDegrees(Math.atan2(-d.x,d.z))+(float)sweep);
            mc.player.setXRot((float)-Math.toDegrees(Math.asin(d.y))+pitchOffset);
            if(frames>0&&index<frames&&++ticks>=20&&(ticks-20)%interval==0) {
                pending=label+String.format("_%03d",index++);settle=0;
            }
        }catch(Exception e){throw new IllegalStateException("Standalone photography",e);}
    }
    @SubscribeEvent public static void capture(RenderFrameEvent.Post event) {
        if(pending.isEmpty()||--settle>0)return;
        String label=pending;pending="";var mc=Minecraft.getInstance();
        Screenshot.grab(Path.of(System.getProperty("lunar_dread.standalone.output")).toFile(),label+".png",mc.getMainRenderTarget(),message->System.out.println("STANDALONE_SAVED "+label));
    }
}
