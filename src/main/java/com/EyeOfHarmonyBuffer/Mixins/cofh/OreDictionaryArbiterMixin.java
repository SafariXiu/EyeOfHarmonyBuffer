package com.EyeOfHarmonyBuffer.Mixins.cofh;

import cofh.core.util.oredict.OreDictionaryArbiter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.ArrayList;
import java.util.Collection;

@Mixin(value = OreDictionaryArbiter.class,remap = false)
public abstract class OreDictionaryArbiterMixin {

    @Redirect(
        method = "initialize",
        at = @At(value = "INVOKE", target = "Ljava/util/ArrayList;addAll(Ljava/util/Collection;)Z")
    )
    private static boolean safeAddAll(ArrayList<Object> list, Collection<?> collection) {
        if (collection == null) {
            System.err.println("[Mixin Fix] OreDictionaryArbiter attempted to addAll(null). Skipped safely.");
            return false;
        }
        return list.addAll(collection);
    }
}
