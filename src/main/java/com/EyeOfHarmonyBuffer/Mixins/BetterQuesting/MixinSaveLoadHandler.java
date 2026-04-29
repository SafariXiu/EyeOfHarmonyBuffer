package com.EyeOfHarmonyBuffer.Mixins.BetterQuesting;

import betterquesting.handlers.SaveLoadHandler;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import com.hfstudio.bqapi.BQApi;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.*;
import java.util.stream.Collectors;

@Mixin(value = SaveLoadHandler.class, remap = false)
public abstract class MixinSaveLoadHandler {

    @Redirect(
        method = "saveConfig()Ljava/util/concurrent/Future;",
        at = @At(
            value = "INVOKE",
            target =
                "Lbetterquesting/questing/QuestDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"
        )
    )
    private NBTTagList eyeofharmonybuffer$filterQuestNbtForWorldSave(
        QuestDatabase instance,
        NBTTagList nbt,
        List<UUID> subset
    ) {
        return instance.writeToNBT(
            nbt,
            filteredSubset(subset, BQApi.getRegisteredQuestUuids(), instance.keySet())
        );
    }

    @Redirect(
        method = "saveConfig()Ljava/util/concurrent/Future;",
        at = @At(
            value = "INVOKE",
            target =
                "Lbetterquesting/questing/QuestLineDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"
        )
    )
    private NBTTagList eyeofharmonybuffer$filterQuestLineNbtForWorldSave(
        QuestLineDatabase instance,
        NBTTagList nbt,
        List<UUID> subset
    ) {
        return instance.writeToNBT(
            nbt,
            filteredSubset(subset, BQApi.getRegisteredChapterUuids(), instance.keySet())
        );
    }

    private static List<UUID> filteredSubset(
        List<UUID> subset,
        Set<UUID> managedUuids,
        Set<UUID> allAvailable
    ) {
        if (managedUuids.isEmpty()) {
            return subset;
        }
        if (subset == null) {
            return allAvailable.stream()
                .filter(id -> !managedUuids.contains(id))
                .collect(Collectors.toCollection(ArrayList::new));
        }
        return subset.stream()
            .filter(id -> !managedUuids.contains(id))
            .collect(Collectors.toCollection(ArrayList::new));
    }
}
