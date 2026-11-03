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
    public static final ModConfigSpec.BooleanValue TENSION_MUSIC_ENABLED;
    public static final ModConfigSpec.DoubleValue TENSION_MUSIC_VOLUME_SCALE;

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
        CLIENT_SPEC = client.build();
    }

    private LunarDreadConfig() {}
}
