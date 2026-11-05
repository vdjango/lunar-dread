package lunar.dread;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/** Time-of-day shortcuts must not erase the elapsed days of a world. */
public final class CalendarCommands {
    private static final long DAY_TICKS = 24000L;

    private static int apply(CommandSourceStack source, long time) {
        for (var level : source.getServer().getAllLevels()) level.setDayTime(time);
        source.getServer().forceTimeSynchronization();
        source.sendSuccess(() -> Component.literal("时间已调整：第 " + (time / DAY_TICKS + 1)
            + " 天，时刻 " + time % DAY_TICKS + "。"), false);
        return (int)(time % DAY_TICKS);
    }

    private static int clock(CommandSourceStack source, int clock) {
        long current = Math.max(0, source.getServer().overworld().getDayTime());
        return apply(source, current / DAY_TICKS * DAY_TICKS + clock);
    }

    private static int day(CommandSourceStack source, int day) {
        long clock = Math.floorMod(source.getServer().overworld().getDayTime(), DAY_TICKS);
        return apply(source, (day - 1L) * DAY_TICKS + clock);
    }

    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunardread")
            .then(Commands.literal("time").requires(source -> source.hasPermission(2))
                .then(Commands.literal("clock").then(Commands.argument("ticks", IntegerArgumentType.integer(0, 23999))
                    .executes(c -> clock(c.getSource(), IntegerArgumentType.getInteger(c, "ticks")))))
                .then(Commands.literal("day").then(Commands.argument("day", IntegerArgumentType.integer(1))
                    .executes(c -> day(c.getSource(), IntegerArgumentType.getInteger(c, "day")))))));
    }

    @SubscribeEvent public void command(CommandEvent event) {
        if (!LunarDreadConfig.PRESERVE_DAY_ON_TIME_CHANGE.get()) return;
        var parsed = event.getParseResults();
        var source = parsed.getContext().getSource();
        if (!source.hasPermission(2) || !parsed.getExceptions().isEmpty() || parsed.getReader().canRead()) return;
        String[] parts = parsed.getReader().getString().trim().split("\\s+");
        int target = -1;
        // FTB Library's inventory buttons call setDayTime(6000/18000)
        // directly, rather than invoking Minecraft's /time command.
        if (parts.length == 2 && parts[0].equals("ftblibrary")) {
            if (parts[1].equals("day")) target = 6000;
            else if (parts[1].equals("night")) target = 18000;
        } else if (parts.length == 3 && (parts[0].equals("time") || parts[0].equals("minecraft:time"))
                && parts[1].equals("set")) {
            target = switch (parts[2]) {
                case "day" -> 1000;
                case "noon" -> 6000;
                case "night" -> 13000;
                case "midnight" -> 18000;
                default -> {
                    try { int ticks = Integer.parseInt(parts[2]); yield ticks >= 0 && ticks < DAY_TICKS ? ticks : -1; }
                    catch (NumberFormatException ignored) { yield -1; }
                }
            };
        }
        if (target < 0) return;
        event.setParseResults(source.getServer().getCommands().getDispatcher()
            .parse("lunardread time clock " + target, source));
    }
}
