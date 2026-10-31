package net.addon.superbstealth.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * SuperbWarfare × Stealth 連携アドオンの共通設定 (superbstealth-common.toml) の定義クラス。
 */
public class SuperbStealthConfig {

    public static final ForgeConfigSpec SPEC;
    public static final SuperbStealthConfig CONFIG;

    // --- [occlusion] ---
    public final ForgeConfigSpec.DoubleValue floorCeilingPenalty;
    public final ForgeConfigSpec.DoubleValue wallPenalty;
    public final ForgeConfigSpec.DoubleValue detourPenalty;
    public final ForgeConfigSpec.IntValue maxDetourHops;
    public final ForgeConfigSpec.IntValue maxPenetrations;
    public final ForgeConfigSpec.IntValue shooterExitScanRadius;

    // --- [gun] ---
    public final ForgeConfigSpec.DoubleValue suppressorRadiusMultiplier;
    public final ForgeConfigSpec.DoubleValue suppressorMinRadius;
    public final ForgeConfigSpec.DoubleValue fallbackGunVolume;

    // --- [stealth] ---
    public final ForgeConfigSpec.DoubleValue unsuppressedAlertLevel;
    public final ForgeConfigSpec.DoubleValue suppressedMinAlertGain;
    public final ForgeConfigSpec.DoubleValue suppressedMaxAlertGain;
    public final ForgeConfigSpec.DoubleValue suppressedMaxAlert;
    public final ForgeConfigSpec.DoubleValue soundPriorityMin;
    public final ForgeConfigSpec.DoubleValue soundPriorityMax;

    // --- [sculk] ---
    public final ForgeConfigSpec.IntValue suppressedSculkFrequency;
    public final ForgeConfigSpec.IntValue unsuppressedSculkFrequency;

    static {
        Pair<SuperbStealthConfig, ForgeConfigSpec> pair = new ForgeConfigSpec.Builder().configure(SuperbStealthConfig::new);
        CONFIG = pair.getLeft();
        SPEC = pair.getRight();
    }

    private SuperbStealthConfig(ForgeConfigSpec.Builder builder) {
        // [occlusion]
        builder.comment(
                "Sound Occlusion and Acoustic Physics Settings",
                "--------------------------------------------------------------------------------------------------------",
                "TIP: You can inspect sound propagation raycasts, diffraction detour paths, and wall penetration",
                "in real-time in-game by running the '/superbstealth debug' command!",
                "--------------------------------------------------------------------------------------------------------"
        ).push("occlusion");

        floorCeilingPenalty = builder
                .comment("Sound distance penalty (in meters) incurred when penetrating a floor or ceiling slab.",
                        "Higher values make multistory vertical sound propagation much harder.")
                .defineInRange("floorCeilingPenalty", 12.0, 0.0, 128.0);

        wallPenalty = builder
                .comment("Sound distance penalty (in meters) incurred when penetrating an interior wall or closed door.")
                .defineInRange("wallPenalty", 8.0, 0.0, 128.0);

        detourPenalty = builder
                .comment("Sound distance penalty (in meters) incurred per diffraction detour hop through openings (doors, stairwells).")
                .defineInRange("detourPenalty", 2.0, 0.0, 128.0);

        maxDetourHops = builder
                .comment("Maximum number of diffraction hops (opening-to-opening bounces) allowed when searching for sound detour paths.",
                        "Higher values allow sound to navigate through complex corridors but slightly increase raycast checks.")
                .defineInRange("maxDetourHops", 3, 1, 8);

        maxPenetrations = builder
                .comment("Maximum number of consecutive solid blocks a sound wave can penetrate along a direct path.",
                        "Acts as a safety limit to prevent infinite collision checks.")
                .defineInRange("maxPenetrations", 8, 1, 32);

        shooterExitScanRadius = builder
                .comment("Block radius around the shooter to scan for room exits (e.g., physically open doors) upon firing.")
                .defineInRange("shooterExitScanRadius", 4, 1, 8);

        builder.pop();

        // [gun]
        builder.comment("Firearm Sound and Suppressor Settings").push("gun");

        suppressorRadiusMultiplier = builder
                .comment("Multiplier applied to the effective sound radius when a silencer/suppressor is equipped (BARREL == 2).",
                        "0.25 reduces an unsuppressed ~90m gunshot down to ~22.4m (25% of original distance).",
                        "Setting to 0.0 makes suppressed shots completely silent (capped by suppressorMinRadius).")
                .defineInRange("suppressorRadiusMultiplier", 0.25, 0.0, 1.0);

        suppressorMinRadius = builder
                .comment("Minimum guaranteed effective sound radius (in meters) for suppressed shots.",
                        "Prevents shots from becoming completely inaudible at point-blank range.")
                .defineInRange("suppressorMinRadius", 8.0, 0.0, 64.0);

        fallbackGunVolume = builder
                .comment("Fallback firearm volume value used when a gun lacks GunProp.SOUND_RADIUS.",
                        "Note: Actual in-game hearing distance in blocks is calculated as (volume * 0.4 * 16.0) = (volume * 6.4).",
                        "For example, volume 14.0 translates to ~89.6m.")
                .defineInRange("fallbackGunVolume", 14.0, 1.0, 100.0);

        builder.pop();

        // [stealth]
        builder.comment("Stealth by Karl Karlmann AI and Alert Integration Settings").push("stealth");

        unsuppressedAlertLevel = builder
                .comment("Alert level instantly applied to mobs hearing an unsuppressed gunshot.",
                        "Mobs will immediately start moving to investigate the sound origin if alert level >= 0.45.")
                .defineInRange("unsuppressedAlertLevel", 0.80, 0.0, 1.0);

        suppressedMinAlertGain = builder
                .comment("Minimum alert gain added to mobs hearing a suppressed gunshot at the edge of hearing range.",
                        "Mobs will look towards the sound origin (Phase 1) if total alert level >= 0.20.")
                .defineInRange("suppressedMinAlertGain", 0.20, 0.0, 1.0);

        suppressedMaxAlertGain = builder
                .comment("Maximum alert gain added to mobs hearing a suppressed gunshot at point-blank range.",
                        "Mobs will begin investigating (Phase 2) if alert gain pushes total alert level >= 0.45.")
                .defineInRange("suppressedMaxAlertGain", 0.50, 0.0, 1.0);

        suppressedMaxAlert = builder
                .comment("Maximum alert level cap for a single suppressed gunshot.",
                        "Prevents a single suppressed shot from triggering full combat alert (> 0.85).")
                .defineInRange("suppressedMaxAlert", 0.85, 0.0, 1.0);

        soundPriorityMin = builder
                .comment("Priority assigned to the gunshot position in the mob's memory at minimum audible range.",
                        "Vanilla footsteps use priority 5.0.")
                .defineInRange("soundPriorityMin", 5.0, 0.0, 100.0);

        soundPriorityMax = builder
                .comment("Priority assigned to the gunshot position in the mob's memory at point-blank range.")
                .defineInRange("soundPriorityMax", 15.0, 0.0, 100.0);

        builder.pop();

        // [sculk]
        builder.comment("Sculk Sensor and Vibration Integration Settings").push("sculk");

        suppressedSculkFrequency = builder
                .comment("Vibration frequency (and resulting redstone comparator output strength 1-15) triggered on sculk sensors by suppressed gunshots.",
                        "Default 10 corresponds to vanilla projectile shooting frequency.")
                .defineInRange("suppressedSculkFrequency", 10, 1, 15);

        unsuppressedSculkFrequency = builder
                .comment("Vibration frequency (and resulting redstone comparator output strength 1-15) triggered on sculk sensors by unsuppressed gunshots.",
                        "Default 15 corresponds to maximum loudness (vanilla explosion frequency).")
                .defineInRange("unsuppressedSculkFrequency", 15, 1, 15);

        builder.pop();
    }
}
