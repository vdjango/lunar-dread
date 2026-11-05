package lunar.validation;

import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid="lunar_standalone_validation")
public final class StandaloneProbe {
    private static String lastCommand="";
    @SubscribeEvent public static void tick(net.neoforged.neoforge.event.tick.ServerTickEvent.Post event) {
        String path=System.getProperty("lunar_dread.standalone.control");
        var server=event.getServer();
        if(path==null||server.isDedicatedServer()||server.getPlayerCount()==0)return;
        try {
            var file=java.nio.file.Path.of(path);if(!java.nio.file.Files.exists(file))return;
            var data=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
            String id=data.get("id").getAsString();if(id.equals(lastCommand))return;lastCommand=id;
            for(var command:data.getAsJsonArray("commands")) {
                if(command.getAsString().equals("__halt"))server.halt(false);
                else server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4),command.getAsString());
            }
            System.out.println("STANDALONE_BARRIER "+id);
        }catch(Exception error){throw new IllegalStateException("Standalone controls",error);}
    }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("__lunar_standalone").requires(s->s.hasPermission(4))
            .then(Commands.literal("check").executes(c->{
                invoke("run",c.getSource().getServer().overworld());return 1;
            }))
            .then(Commands.literal("persisted").executes(c->{
                invoke("persisted",c.getSource().getServer().overworld());return 1;
            })));
    }
    private static void invoke(String method,net.minecraft.server.level.ServerLevel level) {
        try { Class.forName("lunar.dread.StandaloneChecks").getMethod(method,net.minecraft.server.level.ServerLevel.class).invoke(null,level); }
        catch(Exception error) { throw new IllegalStateException("Standalone validation",error); }
    }
}
