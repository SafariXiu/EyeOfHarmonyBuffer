package com.EyeOfHarmonyBuffer.common.misc;

import com.EyeOfHarmonyBuffer.utils.TextEnums;
import net.minecraft.util.EnumChatFormatting;

public enum OverclockType {
        NONE(1, 1),
            NormalOverclock(2, 4),
            LowSpeedPerfectOverclock(2, 2),
            PerfectOverclock(4, 4),
            SingularityPerfectOverclock(8, 4),
            EOHStupidOverclock(2, 8);

        public final int timeReduction;
        public final int powerIncrease;
        public final double powerIncreaseMultiplierPerOverclock;
        public final boolean perfectOverclock;

    private OverclockType(int timeReduction, int powerIncrease) {
        this.timeReduction = timeReduction;
        this.powerIncrease = powerIncrease;
        this.perfectOverclock = timeReduction >= powerIncrease;
        if (timeReduction == powerIncrease) {
            this.powerIncreaseMultiplierPerOverclock = 1.0;
        } else {
            this.powerIncreaseMultiplierPerOverclock = Math.pow(2.0, (double)(powerIncrease - timeReduction));
        }

    }

        public boolean isPerfectOverclock() {
        return this.perfectOverclock;
    }

        public static com.EyeOfHarmonyBuffer.common.misc.OverclockType checkOverclockType(int timeReduction, int powerIncrease) {
            com.EyeOfHarmonyBuffer.common.misc.OverclockType[] var2 = values();
        int var3 = var2.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            com.EyeOfHarmonyBuffer.common.misc.OverclockType t = var2[var4];
            if (t.timeReduction == timeReduction && t.powerIncrease == powerIncrease) {
                return t;
            }
        }

        return NormalOverclock;
    }

        public int getID() {
        return this.ordinal();
    }

        public String getDescription() {
        return TextEnums.tr("OverclockType.Description.01") + " " + EnumChatFormatting.AQUA + this.timeReduction + EnumChatFormatting.GRAY + " , " + TextEnums.tr("OverclockType.Description.02") + " " + EnumChatFormatting.RED + this.powerIncrease + EnumChatFormatting.GRAY + " .";
    }

        public static com.EyeOfHarmonyBuffer.common.misc.OverclockType getFromID(int ID) {
        return values()[ID];
    }
}
