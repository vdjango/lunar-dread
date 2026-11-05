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
    private static int mode(CommandSourceStack source,LunarDreadConfig.EventMode mode) {
        LunarDreadConfig.EVENT_MODE.set(mode);LunarDreadConfig.EVENT_MODE.save();
        var level=source.getServer().overworld();EventCalendar.ensure(level);MoonCycle.ensure(level);MoonCycle.sync(level);
        source.sendSuccess(()->Component.literal("已切换为"+(mode==LunarDreadConfig.EventMode.PROBABILITY?"概率模式":"百日模式")+"，世界天数保留。"),false);
        return 1;
    }
    private static <T> int set(CommandSourceStack source, ModConfigSpec.ConfigValue<T> option, T value) {
        option.set(value);
        option.save();
        source.sendSuccess(() -> Component.literal("已保存 " + String.join(".", option.getPath()) + " = " + option.get()), false);
        return 1;
    }

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        var settings = Commands.literal("set");
        settings.then(Commands.literal("keeptime").then(Commands.argument("enabled", BoolArgumentType.bool())
            .executes(c -> set(c.getSource(), LunarDreadConfig.PRESERVE_DAY_ON_TIME_CHANGE, BoolArgumentType.getBool(c, "enabled")))));
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
                    + " 心跳音量×" + LunarDreadConfig.HEARTBEAT_VOLUME_SCALE.get()
                    + " 保留天数=" + LunarDreadConfig.PRESERVE_DAY_ON_TIME_CHANGE.get()), false);
                s.sendSuccess(() -> Component.literal("世界规则：config/lunar_dread-server.toml；个人配乐：config/lunar_dread-client.toml"), false);
                return 1;
            });
        config.then(settings);
        event.getDispatcher().register(Commands.literal("lunardread")
            .executes(c -> {
                c.getSource().sendSuccess(() -> Component.literal("/lunardread guide · /lunardread supply locate · /lunardread supply track · /lunardread sound test heart"), false);
                return 1;
            })
            .then(config)
            .then(Commands.literal("mode").requires(source->source.hasPermission(2))
                .executes(c->{var l=c.getSource().getServer().overworld();c.getSource().sendSuccess(()->Component.literal(
                    "模式="+LunarDreadConfig.EVENT_MODE.get()+" · 第 "+SurvivalRules.day(l)+" 天 · 调度="+(EventCalendar.packOwns(l)?"整合包":"内置")
                    +" · 特殊月相概率="+Math.round(LunarDreadConfig.EVENT_CHANCE.get()*100)+"%"),false);return 1;})
                .then(Commands.literal("probability").executes(c->mode(c.getSource(),LunarDreadConfig.EventMode.PROBABILITY)))
                .then(Commands.literal("hundred_days").executes(c->mode(c.getSource(),LunarDreadConfig.EventMode.HUNDRED_DAYS)))));
    }
}
