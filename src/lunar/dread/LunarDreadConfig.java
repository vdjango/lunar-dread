package lunar.dread;

import net.neoforged.neoforge.common.ModConfigSpec;

/** User-facing NeoForge settings. */
public final class LunarDreadConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue ANIMALS_ENABLED;
    public static final ModConfigSpec.DoubleValue ANIMAL_CHASE_SCALE;
    public static final ModConfigSpec.BooleanValue HORROR_ENCOUNTERS_ENABLED;
    public static final ModConfigSpec.BooleanValue HEARTBEAT_ENABLED;
    public static final ModConfigSpec.DoubleValue HEARTBEAT_VOLUME_SCALE;
    public static final ModConfigSpec.BooleanValue SURVIVAL_BOOK_ON_JOIN;
    public static final ModConfigSpec.BooleanValue MOON_BOOK_ON_JOIN;
    public static final ModConfigSpec.BooleanValue TENSION_MUSIC_ENABLED;
    public static final ModConfigSpec.DoubleValue TENSION_MUSIC_VOLUME_SCALE;
    public static final ModConfigSpec.BooleanValue MOON_FOG_ENABLED;
    public static final ModConfigSpec.DoubleValue MOON_FOG_DISTANCE_SCALE;
    public static final ModConfigSpec.DoubleValue BLOOD_FOG_PEAK_DISTANCE;
    public static final ModConfigSpec.DoubleValue MOON_RAY_WIDTH_SCALE;

    static {
        var server = new ModConfigSpec.Builder();
        server.push("animals");
        ANIMALS_ENABLED = server.comment("Enable the custom animal pursuit and attack abilities.")
            .translation("lunar_dread.config.animals.enabled")
            .define("enabled", true);
        ANIMAL_CHASE_SCALE = server.comment("Scales the duration of animal pursuit; 1.0 keeps the weekly schedule.")
            .translation("lunar_dread.config.animals.chaseDurationScale")
            .defineInRange("chaseDurationScale", 1.0, 0.5, 2.0);
        server.pop();
        server.push("encounters");
        HORROR_ENCOUNTERS_ENABLED = server.comment("Enable the player-specific cave, building and hunting ambushes.")
            .translation("lunar_dread.config.encounters.horrorEnabled")
            .define("horrorEnabled", true);
        server.pop();
        server.push("moon");
        HEARTBEAT_ENABLED = server.comment("Play the moon's periodic heartbeat on dangerous nights.")
            .translation("lunar_dread.config.moon.heartbeatEnabled")
            .define("heartbeatEnabled", true);
        HEARTBEAT_VOLUME_SCALE = server.comment("Multiplies the moon heartbeat volume; 0.0 is silent, 1.0 is designed level.")
            .translation("lunar_dread.config.moon.heartbeatVolumeScale")
            .defineInRange("heartbeatVolumeScale", 1.0, 0.0, 2.0);
        server.pop();
        server.push("books");
        SURVIVAL_BOOK_ON_JOIN = server.comment("Give the original survival handbook once per player; the /lunardread guide command remains available.")
            .define("survivalGuideOnFirstJoin", true);
        MOON_BOOK_ON_JOIN = server.comment("Give the colored moon codex once per player; /lunardread guide moon remains available.")
            .define("moonCodexOnFirstJoin", true);
        server.pop();
        SERVER_SPEC = server.build();

        var client = new ModConfigSpec.Builder();
        client.push("audio");
        TENSION_MUSIC_ENABLED = client.comment("Play the looping danger score when hostile mobs approach.")
            .translation("lunar_dread.config.audio.tensionMusicEnabled")
            .define("tensionMusicEnabled", true);
        TENSION_MUSIC_VOLUME_SCALE = client.comment("Local volume for the danger score; 0.0 is silent.")
            .translation("lunar_dread.config.audio.tensionMusicVolumeScale")
            .defineInRange("tensionMusicVolumeScale", 1.0, 0.0, 2.0);
        client.pop();
        client.push("visuals");
        MOON_FOG_ENABLED = client.comment("Render phase-specific ground fog for all 16 moon phases. Sky and moon remain visible.")
            .define("moonFogEnabled", true);
        MOON_FOG_DISTANCE_SCALE = client.comment("Scales non-blood moon fog sight distance; smaller values produce thicker fog.")
            .defineInRange("moonFogDistanceScale", 1.0, 0.5, 2.0);
        BLOOD_FOG_PEAK_DISTANCE = client.comment("Surface sight distance at the blood moon's fog peak, in blocks; default 7.0.")
            .defineInRange("bloodFogPeakDistance", 7.0, 4.0, 24.0);
        MOON_RAY_WIDTH_SCALE = client.comment("Visual width of the moon strike beam and warning rings.")
            .defineInRange("moonRayWidthScale", 1.0, 0.5, 2.0);
        client.pop();
        CLIENT_SPEC = client.build();
    }

    private LunarDreadConfig() {}
}
