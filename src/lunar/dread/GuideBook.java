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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** A short, deliberately spoiler-free handbook, delivered by the mod on login. */
public final class GuideBook {
    private static final String RECEIVED = "lunar_dread.guide_received";
    private static final WrittenBookContent CONTENT = load();
    private static WrittenBookContent load() {
        try (var stream = GuideBook.class.getResourceAsStream("/lunar_dread/guide.json")) {
            if (stream == null) throw new IllegalStateException("Missing survival guide");
            var data = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            List<Filterable<Component>> pages = new ArrayList<>();
            for (var page : data.getAsJsonArray("pages")) pages.add(Filterable.passThrough(Component.literal(page.getAsString())));
            return new WrittenBookContent(Filterable.passThrough(data.get("title").getAsString()),
                data.get("author").getAsString(), 0, pages, true);
        } catch (java.io.IOException error) { throw new IllegalStateException(error); }
    }
    private static ItemStack book() {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, CONTENT);
        CompoundTag marker = new CompoundTag();
        marker.putBoolean("lunar_dread.guide", true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(marker));
        return stack;
    }
    private static boolean isGuide(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean("lunar_dread.guide");
    }
    private static void deliver(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) if (isGuide(stack)) {
            stack.set(DataComponents.WRITTEN_BOOK_CONTENT, CONTENT); return;
        }
        ItemStack book = book();
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        for (ItemStack stack : player.getInventory().items) if (isGuide(stack)) stack.set(DataComponents.WRITTEN_BOOK_CONTENT, CONTENT);
        if (player.getPersistentData().getBoolean(RECEIVED)) return;
        deliver(player);
        player.getPersistentData().putBoolean(RECEIVED, true);
    }
    @SubscribeEvent public void clone(PlayerEvent.Clone event) {
        if (event.getOriginal().getPersistentData().getBoolean(RECEIVED))
            event.getEntity().getPersistentData().putBoolean(RECEIVED, true);
    }
    @SubscribeEvent public void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("lunardread").then(Commands.literal("guide").executes(context -> {
            ServerPlayer player = context.getSource().getPlayerOrException();
            long now = player.serverLevel().getGameTime();
            if (now < player.getPersistentData().getLong("lunar_dread.guide_next")) return 0;
            player.getPersistentData().putLong("lunar_dread.guide_next", now + 200);
            deliver(player);
            return 1;
        })));
    }
}
