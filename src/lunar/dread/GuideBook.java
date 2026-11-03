package lunar.dread;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Two independent mod-owned books; the original survival guide stays intact. */
public final class GuideBook {
    private static final String GUIDE_RECEIVED = "lunar_dread.guide_received";
    private static final String MOON_RECEIVED = "lunar_dread.moon_codex_received";
    private static final String GUIDE_MARKER = "lunar_dread.guide";
    private static final String MOON_MARKER = "lunar_dread.moon_codex";
    private static final WrittenBookContent GUIDE = load("/lunar_dread/guide.json");
    private static final WrittenBookContent MOON = load("/lunar_dread/moon_codex.json");
    private static WrittenBookContent load(String resource) {
        try (var stream = GuideBook.class.getResourceAsStream(resource)) {
            if (stream == null) throw new IllegalStateException("Missing book resource: " + resource);
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Filterable<Component>> pages = new ArrayList<>();
            for (var page : data.getAsJsonArray("pages")) {
                if (page.isJsonPrimitive()) {
                    pages.add(Filterable.passThrough(Component.literal(page.getAsString())));
                    continue;
                }
                var colored = Component.empty();
                for (var segment : page.getAsJsonArray()) {
                    var part = segment.getAsJsonObject();
                    var line = Component.literal(part.get("text").getAsString());
                    int rgb = Integer.parseInt(part.get("color").getAsString().substring(1), 16);
                    boolean bold = part.has("bold") && part.get("bold").getAsBoolean();
                    colored.append(line.withStyle(style -> style.withColor(TextColor.fromRgb(rgb)).withBold(bold)));
                }
                pages.add(Filterable.passThrough(colored));
            }
            return new WrittenBookContent(Filterable.passThrough(data.get("title").getAsString()),
                data.get("author").getAsString(), 0, pages, true);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static ItemStack book(boolean moon) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, moon ? MOON : GUIDE);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean(moon ? MOON_MARKER : GUIDE_MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return stack;
    }
    private static boolean isBook(ItemStack stack, boolean moon) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(moon ? MOON_MARKER : GUIDE_MARKER);
    }
    private static void refresh(ItemStack stack) {
        if (isBook(stack, false)) stack.set(DataComponents.WRITTEN_BOOK_CONTENT, GUIDE);
        if (isBook(stack, true)) stack.set(DataComponents.WRITTEN_BOOK_CONTENT, MOON);
    }
    private static void deliver(ServerPlayer player, boolean moon) {
        for (ItemStack stack : player.getInventory().items) if (isBook(stack, moon)) {
            refresh(stack); return;
        }
        for (ItemStack stack : player.getInventory().offhand) if (isBook(stack, moon)) {
            refresh(stack); return;
        }
        ItemStack book = book(moon);
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ItemStack stack : player.getInventory().items) refresh(stack);
        for (ItemStack stack : player.getInventory().offhand) refresh(stack);
        if (LunarDreadConfig.SURVIVAL_BOOK_ON_JOIN.get() && !player.getPersistentData().getBoolean(GUIDE_RECEIVED)) {
            deliver(player, false); player.getPersistentData().putBoolean(GUIDE_RECEIVED, true);
        }
        if (LunarDreadConfig.MOON_BOOK_ON_JOIN.get() && !player.getPersistentData().getBoolean(MOON_RECEIVED)) {
            deliver(player, true); player.getPersistentData().putBoolean(MOON_RECEIVED, true);
        }
    }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        for (String key : List.of(GUIDE_RECEIVED, MOON_RECEIVED))
            if (event.getOriginal().getPersistentData().getBoolean(key)) event.getEntity().getPersistentData().putBoolean(key, true);
    }
    private static int command(ServerPlayer player, boolean moon) {
        String cooldown = moon ? "lunar_dread.moon_codex_next" : "lunar_dread.guide_next";
        long now = player.serverLevel().getGameTime();
        if (now < player.getPersistentData().getLong(cooldown)) return 0;
        player.getPersistentData().putLong(cooldown, now + 200);
        deliver(player, moon);
        return 1;
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("guide")
            .executes(context -> command(context.getSource().getPlayerOrException(), false))
            .then(Commands.literal("survival").executes(context -> command(context.getSource().getPlayerOrException(), false)))
            .then(Commands.literal("moon").executes(context -> command(context.getSource().getPlayerOrException(), true)))));
    }
}
