package lunar.dread;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded local sampling keeps each lunar fog close to terrain and dense in natural caves. */
public final class MoonFogProfile {
    public record Range(float near, float far) {}
    public record Exposure(float layer, float cave) {}

    // Surface and cave sight distances for normal, peaceful and hostile moons in MoonCycle.IDS order.
    private static final float[] SURFACE_FAR = {
        80, 104, 72, 56, 54, 64, 7, 18, 28, 20, 30, 24, 32, 22, 20, 24
    };
    private static final float[] CAVE_FAR = {
        12, 12, 12, 11, 12, 12, 5, 8, 10, 8, 9, 10, 10, 9, 8, 9
    };
    private static final float[] COLOR_WEIGHT = {
        .48f, .45f, .5f, .54f, .54f, .52f, .96f, .74f, .66f, .76f, .68f, .66f, .64f, .72f, .72f, .74f
    };

    private MoonFogProfile() {}

    private static float ease(float value) {
        float x = Math.clamp(value, 0, 1);
        return x * x * (3 - 2 * x);
    }

    public static float intensity(long dayTime) { return intensity(dayTime, 13000, 17500, 19500, 24000); }

    /** The ordered config ranges make zero-duration transitions impossible. */
    public static float intensity(long dayTime, int dusk, int peakStart, int peakEnd, int dawn) {
        long clock = Math.floorMod(dayTime, 24000);
        if (clock < dusk) return 0;
        if (clock < peakStart) return ease((clock - dusk) / (float)(peakStart - dusk));
        if (clock <= peakEnd) return 1;
        if (clock >= dawn) return 0;
        return ease((dawn - clock) / (float)(dawn - peakEnd));
    }

    /** A soft upper edge avoids a hard horizontal line at the top of the fog bank. */
    public static float layerWeight(float aboveSurface, float height, float topFade) {
        float edge = Math.min(Math.max(.25f, topFade), Math.max(.25f, height));
        return 1 - ease((aboveSurface - (height - edge)) / edge);
    }

    public static Exposure exposure(Level level, BlockPos camera, double cameraY, float height, float topFade) {
        if (!level.hasChunkAt(camera)) return new Exposure(0, 0);
        // WORLD_SURFACE is sent to clients; MOTION_BLOCKING_NO_LEAVES is not populated
        // on a newly received client chunk and would incorrectly clear all surface fog.
        int surface = groundHeight(level, camera);
        int natural = 0;
        if (!level.canSeeSky(camera)) {
            // Sample only this column, twice per second. Do not scan an entire cave or chunk.
            for (int dy = 1; dy <= 48 && camera.getY() + dy < surface + 2; dy += dy < 16 ? 1 : 2) {
                if (natural(level, camera.above(dy)) && ++natural >= 3) break;
            }
            if (natural < 3 && surface - camera.getY() > 48) {
                for (int dy = 1; dy <= 3; dy++) if (natural(level, new BlockPos(camera.getX(), surface - dy, camera.getZ()))) natural++;
            }
        }
        float cave = natural >= 3 ? 1 : 0;
        float layer = cave > 0 ? 1 : layerWeight((float)(cameraY - surface), height, topFade);
        return new Exposure(layer, cave);
    }

    /** Follow the terrain in this column, skipping tree crowns and ordinary building materials. */
    private static int groundHeight(Level level, BlockPos camera) {
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, camera.getX(), camera.getZ());
        for (int dy = 1; dy <= 96 && surface - dy >= level.getMinBuildHeight(); dy++) {
            var pos = new BlockPos(camera.getX(), surface - dy, camera.getZ());
            var block = level.getBlockState(pos);
            if (natural(level, pos) || block.is(BlockTags.TERRACOTTA) || block.is(BlockTags.ICE)
                || block.is(net.minecraft.world.level.block.Blocks.GRAVEL)
                || block.is(net.minecraft.world.level.block.Blocks.CLAY)
                || block.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                || block.is(net.minecraft.world.level.block.Blocks.SNOW)
                || block.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)
                || !block.getFluidState().isEmpty()) return pos.getY() + 1;
        }
        // Unusual modded surfaces remain usable without a scan down an entire column.
        return surface;
    }

    private static boolean natural(Level level, BlockPos pos) {
        var block = level.getBlockState(pos);
        return block.is(BlockTags.BASE_STONE_OVERWORLD) || block.is(BlockTags.DIRT) || block.is(BlockTags.SAND);
    }

    /** Caves thicken earlier than the surface but still clear completely by dawn. */
    public static float strength(float night, float layer, float cave) {
        float underground = Math.clamp(cave, 0, 1);
        return Math.clamp(night * Math.clamp(layer, 0, 1) * (1 - underground)
            + Math.min(1, night * 3) * underground, 0, 1);
    }

    public static float colorWeight(int kind, float strength, float colorScale) {
        return Math.clamp(COLOR_WEIGHT[Math.clamp(kind, 0, COLOR_WEIGHT.length - 1)] * strength * colorScale, 0, 1);
    }

    public static Range range(int kind, float cave, float strength, float originalNear, float originalFar,
                              float bloodSurface, float bloodCave, float otherSurfaceScale, float otherCaveScale) {
        int index = Math.clamp(kind, 0, SURFACE_FAR.length - 1);
        float surface = index == MoonCycle.BLOOD ? bloodSurface : SURFACE_FAR[index] * otherSurfaceScale;
        float underground = index == MoonCycle.BLOOD ? bloodCave : CAVE_FAR[index] * otherCaveScale;
        float peakFar = Math.max(2.5f, surface + (underground - surface) * Math.clamp(cave, 0, 1));
        float peakNear = Math.min(peakFar - .8f, index == MoonCycle.BLOOD ? .5f : Math.max(1.2f, peakFar * .12f));
        float amount = Math.clamp(strength, 0, 1);
        float far = originalFar + (Math.min(originalFar, peakFar) - originalFar) * amount;
        float near = originalNear + (Math.min(originalNear, peakNear) - originalNear) * amount;
        return new Range(Math.min(near, far - .25f), far);
    }
}
