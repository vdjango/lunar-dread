package lunar.dread.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import lunar.dread.MoonContent;
import lunar.dread.MoonCycle;
import lunar.dread.LunarDreadConfig;

/** One local, fading score per listener; no sound packets every music beat. */
@EventBusSubscriber(modid="lunar_dread", value=Dist.CLIENT)
public final class LunarAudio {
    private static ClientLevel receivedLevel;
    private static long receivedAt;
    private static int threat;
    private static TensionLoop loop;

    public static void receive(int value) {
        var mc=Minecraft.getInstance();
        threat=Math.clamp(value,0,2);
        receivedLevel=mc.level;
        receivedAt=mc.level==null?0:mc.level.getGameTime();
    }

    private static int current() {
        var mc=Minecraft.getInstance();
        return LunarDreadConfig.TENSION_MUSIC_ENABLED.get()&&LunarDreadConfig.TENSION_MUSIC_VOLUME_SCALE.get()>0
            &&mc.level!=null&&mc.level==receivedLevel&&mc.player!=null&&mc.player.isAlive()
            &&!mc.player.isCreative()&&!mc.player.isSpectator()
            &&mc.level.getGameTime()-receivedAt<=30?threat:0;
    }

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(loop!=null&&loop.isStopped())loop=null;
        if(current()>0&&loop==null){
            loop=new TensionLoop();
            mc.getSoundManager().play(loop);
        }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        threat=0;receivedLevel=null;
        if(loop!=null){loop.finish();loop=null;}
    }

    private static final class TensionLoop extends AbstractTickableSoundInstance {
        TensionLoop() {
            super(MoonContent.TENSION.get(),SoundSource.HOSTILE,RandomSource.create());
            looping=true;
            relative=true;
            attenuation=SoundInstance.Attenuation.NONE;
            volume=.02f;
            pitch=1;
        }

        @Override public void tick() {
            int level=current();
            float target=(float)(LunarDreadConfig.TENSION_MUSIC_VOLUME_SCALE.get()*(level==0?0:level==2?.60f:.43f));
            if(MoonAtmosphere.kind()==MoonCycle.BLOOD)target*=.72f;
            volume=Math.clamp(volume+(target>volume?.014f:-.014f),0,Math.max(target,volume));
            if(level==0&&volume<=.01f)stop();
        }

        void finish(){stop();}
    }
}
