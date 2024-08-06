package com.runemate.volcanic;

import com.runemate.ui.setting.annotation.open.*;
import com.runemate.ui.setting.open.Settings;

@SettingsGroup
public interface VolcanicSettings extends Settings {


    @SettingsSection(title = "Stop Settings", description = "Settings related to stopping the bot", order = 0)
    String stopSection = "stopSection";

    @Setting(
            key = "unlockedFreeEntry",
            title = "Paid 3000 Numulites for permanent access",
            description = "Tick this if you have unlocked free entry!",
            order = 0
    )
    default boolean unlockedFreeEntry() {
        return false;
    }

    @Setting(key = "foodAmount", title = "Food Amount", description = "How much food to withdraw when banking", order = 1)
    @Range(min = 5, max = 20)
    default int foodAmount() {
        return 15;
    }
    @Setting(key = "minFood", title = "Minimum Food", description = "How much food in inventory before we trigger banking", order = 2)
    @Range(min = 1, max = 20)
    default int minFood() {
        return 5;
    }

    @Setting(
            key = "eatPercent",
            title = "Eat at % (Damage is high!)",
            description = "Eat at %, Volcanic Mine can hit multiple 20s in a row. So don't put this too low",
            order = 3
    )
    @Range(min = 10, max = 90)
    @Suffix("%")
    default int eatPercent() {
        return 70;
    }

    @Setting(
            key = "useStamina",
            title = "Use Stamina Potions",
            description = "Whether to use stamina potions (Default is true)",
            order = 4
    )
    default boolean useStamina() {
        return true;
    }

    @Setting(
            key = "equipVessel",
            title = "Equip Water Vessel",
            description = "Whether to equip water vessel (Default is false)",
            hidden = true
    )
    default boolean equipVessel() {
        return false;
    }

    @Setting(
            key = "stopMinutes",
            title = "Stop after x minutes",
            description = "Stop after x minutes",
            section = stopSection,
            order = 0
    )
    default int stopMinutes() {
        return 0;
    }

    @Setting(
            key = "stopLevel",
            title = "Stop after x level",
            description = "Stop after x level",
            section = stopSection,
            order = 1
    )
    @Range(min = 0, max = 99)
    default int stopLevel() {
        return 0;
    }
}