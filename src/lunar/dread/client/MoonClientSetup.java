package lunar.dread.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import lunar.dread.MoonNetwork;

@EventBusSubscriber(modid = "lunar_dread", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class MoonClientSetup {
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        MoonNetwork.receiver = MoonClient::receive;
        MoonNetwork.dreadReceiver = MoonClient::receiveDread;
        lunar.dread.MoonUpdates.worldReceiver=MoonAtmosphere::receive;
        lunar.dread.MoonUpdates.personalReceiver=MoonAtmosphere::receivePersonal;
        lunar.dread.MoonUpdates.navigationReceiver=MoonAtmosphere::navigate;
        lunar.dread.GravityPhysics.clientForce=MoonAtmosphere::force;
        lunar.dread.GravityPhysics.clientTumbling=e->MoonAtmosphere.tumbleAge(e,0)>=0;
    }
    @SubscribeEvent public static void renderers(net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers event){
        event.registerEntityRenderer(lunar.dread.MoonContent.CANOPY.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        event.registerEntityRenderer(lunar.dread.MoonContent.CRATE.get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
        for(int i=0;i<5+lunar.dread.MoonContent.ACTORS.length;i++)event.registerEntityRenderer(lunar.dread.MoonContent.horror(i).get(),net.minecraft.client.renderer.entity.NoopRenderer::new);
    }
}
