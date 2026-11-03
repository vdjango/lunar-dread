package lunar.dread;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Quiet player help; explicit operator-only diagnostics and persistent settings. */
public final class LunarDreadCommands {
    private static <T> int set(CommandSourceStack source, ModConfigSpec.ConfigValue<T> option, T value) {
        option.set(value);
        option.save();
        source.sendSuccess(() -> Component.literal("已保存 " + String.join(".", option.getPath()) + " = " + option.get()), false);
        return 1;
    }

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        var settings = Commands.literal("set");
        settings.then(Commands.literal("animals").then(Commands.argument("enabled", BoolArgumentType.bool())
            .executes(c -> set(c.getSource(), LunarDreadConfig.ANIMALS_ENABLED, BoolArgumentType.getBool(c, "enabled")))));
        settings.then(Commands.literal("horror").then(Commands.argument("enabled", BoolArgumentType.bool())
            .executes(c -> set(c.getSource(), LunarDreadConfig.HORROR_ENCOUNTERS_ENABLED, BoolArgumentType.getBool(c, "enabled")))));
        settings.then(Commands.literal("heartbeat").then(Commands.argument("enabled", BoolArgumentType.bool())
            .executes(c -> set(c.getSource(), LunarDreadConfig.HEARTBEAT_ENABLED, BoolArgumentType.getBool(c, "enabled")))));
        settings.then(Commands.literal("chase").then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.5, 2.0))
            .executes(c -> set(c.getSource(), LunarDreadConfig.ANIMAL_CHASE_SCALE, DoubleArgumentType.getDouble(c, "scale")))));
        settings.then(Commands.literal("heartvolume").then(Commands.argument("scale", DoubleArgumentType.doubleArg(0.0, 2.0))
            .executes(c -> set(c.getSource(), LunarDreadConfig.HEARTBEAT_VOLUME_SCALE, DoubleArgumentType.getDouble(c, "scale")))));

        var config = Commands.literal("config").requires(source -> source.hasPermission(2))
            .executes(c -> {
                var s = c.getSource();
                s.sendSuccess(() -> Component.literal("动物=" + LunarDreadConfig.ANIMALS_ENABLED.get()
                    + " 追击×" + LunarDreadConfig.ANIMAL_CHASE_SCALE.get()
                    + " 异体=" + LunarDreadConfig.HORROR_ENCOUNTERS_ENABLED.get()
                    + " 心跳=" + LunarDreadConfig.HEARTBEAT_ENABLED.get()
                    + " 心跳音量×" + LunarDreadConfig.HEARTBEAT_VOLUME_SCALE.get()), false);
                s.sendSuccess(() -> Component.literal("配置：config/lunar_dread-server.toml（存档可覆盖）；个人配乐：config/lunar_dread-client.toml"), false);
                return 1;
            });
        config.then(settings);
        event.getDispatcher().register(Commands.literal("lunardread")
            .executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("/lunardread guide · /lunardread supply locate · /lunardread supply track · /lunardread sound test heart"), false);
                return 1;
            })
            .then(config));
    }
}
