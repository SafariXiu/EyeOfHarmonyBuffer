package com.EyeOfHarmonyBuffer.Mixins;

import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import com.EyeOfHarmonyBuffer.Config.MainConfig;

public class MixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackae) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        switch (mixinClassName) {
            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyGas":
                return MainConfig.GasInPut;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyLV":
                return MainConfig.EOHLV;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyBus":
                return MainConfig.EOHinputBusMe;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyAstralArrayAmount":
                return MainConfig.EOHAstralArrayAmount;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyZeroPowerStart":
                return MainConfig.EOHZeroPowerStart;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonySuccessRateControl":
                return MainConfig.EOHSuccessRateControls;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyOutputRateControl":
                return MainConfig.EOHOutputRateControl;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyWorkTime":
                return MainConfig.EOHWorkTime;

            case "com.EyeOfHarmonyBuffer.Mixins.EyeOfHarmonyEU":
                return MainConfig.EOHOpenEuOutPut;

            case "com.EyeOfHarmonyBuffer.Mixins.DTPFBuffer":
                return MainConfig.DTPFOpen;

            case "com.EyeOfHarmonyBuffer.Mixins.FOGShardsAvailable":
                return MainConfig.FOGUpDate;

            default:
                return true;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null; // 可以返回 null 表示不干预
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 空实现，除非你需要在 Mixin 应用之前做一些事情，否则留空即可
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 空实现，除非你需要在 Mixin 应用之后做一些事情，否则留空即可
    }
}