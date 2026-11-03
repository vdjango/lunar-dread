package lunar.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import com.google.gson.JsonParser;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** File-controlled commands in an opt-in, isolated single-player fixture only. */
@EventBusSubscriber(modid="lunar_validation")
public final class IntegratedMoonProbe {
    private static String last="";
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        String control=System.getProperty("lunar_dread.integrated.control");
        if(control==null||event.getServer().isDedicatedServer())return;
        var server=event.getServer();
        if(server.getPlayerList().getPlayerCount()==0)return;
        try{
            var path=Path.of(control);if(!Files.exists(path))return;
            var value=JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            String id=value.get("id").getAsString();if(id.equals(last))return;last=id;
            for(var command:value.getAsJsonArray("commands")){
                if(command.getAsString().equals("__halt"))server.halt(false);
                else server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),command.getAsString());
            }
            System.out.println("LUNAR_INTEGRATED_BARRIER "+id);
        }catch(Exception e){throw new IllegalStateException("Integrated lunar fixture",e);}
    }
}
