package com.EyeOfHarmonyBuffer;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class ExampleMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        System.out.println("[ExampleMixinPlugin] Loaded for package: " + mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals("com.EyeOfHarmonyBuffer.Mixins.TST.IndistinctTentaclePrototypeMK2Mixin")) {
            try {
                Class.forName("com.Nxer.TwistSpaceTechnology.common.modularizedMachine.MM_DimensionallyTranscendentMatterPlasmaForgePrototypeMK2", false, this.getClass().getClassLoader());
                System.out.println("[ExampleMixinPlugin] Target class found, applying Mixin: " + mixinClassName);
                return true;
            } catch (ClassNotFoundException e) {
                System.out.println("[ExampleMixinPlugin] Target class not found, skipping Mixin: " + mixinClassName);
                return false;
            }
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {

    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }

    @Override
    public void postApply(String s, ClassNode classNode, String s1, IMixinInfo iMixinInfo) {

    }
}
