package com.EyeOfHarmonyBuffer.Mixins.BetterQuesting;

import betterquesting.api.questing.IQuestLine;
import betterquesting.commands.admin.QuestCommandDefaults;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestLineDatabase;
import com.hfstudio.bqapi.BQApi;
import com.hfstudio.bqapi.runtime.BQReinjector;
import cpw.mods.fml.common.FMLCommonHandler;
import net.minecraft.command.ICommandSender;
import net.minecraft.nbt.NBTTagList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;


@Mixin(value = QuestCommandDefaults.class, remap = false)
public abstract class MixinQuestCommandDefaults {

    @Redirect(
        method = "save(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestLineDatabase;getOrderedEntries()Ljava/util/List;"
        )
    )
    private static List<Map.Entry<UUID, IQuestLine>>
    eyeofharmonybuffer$filterQuestLinesForSave(QuestLineDatabase instance) {
        Set<UUID> managedChapterUuids = BQApi.getRegisteredChapterUuids();
        if (managedChapterUuids.isEmpty()) {
            return instance.getOrderedEntries();
        }
        return instance.getOrderedEntries()
            .stream()
            .filter(entry -> !managedChapterUuids.contains(entry.getKey()))
            .collect(Collectors.toCollection(ArrayList::new));
    }

    @Redirect(
        method = "save(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target = "Lbetterquesting/questing/QuestDatabase;entrySet()Ljava/util/Set;"
        )
    )
    private static Set<Map.Entry<UUID, betterquesting.api.questing.IQuest>>
    eyeofharmonybuffer$filterQuestsForSave(QuestDatabase instance) {
        Set<UUID> managedQuestUuids = BQApi.getRegisteredQuestUuids();
        if (managedQuestUuids.isEmpty()) {
            return instance.entrySet();
        }
        return instance.entrySet()
            .stream()
            .filter(entry -> !managedQuestUuids.contains(entry.getKey()))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Redirect(
        method = "saveLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target =
                "Lbetterquesting/questing/QuestDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"
        )
    )
    private static NBTTagList eyeofharmonybuffer$filterQuestNbtForSaveLegacy(
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
        method = "saveLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;)V",
        at = @At(
            value = "INVOKE",
            target =
                "Lbetterquesting/questing/QuestLineDatabase;writeToNBT(Lnet/minecraft/nbt/NBTTagList;Ljava/util/List;)Lnet/minecraft/nbt/NBTTagList;"
        )
    )
    private static NBTTagList eyeofharmonybuffer$filterQuestLineNbtForSaveLegacy(
        QuestLineDatabase instance,
        NBTTagList nbt,
        List<UUID> subset
    ) {
        return instance.writeToNBT(
            nbt,
            filteredSubset(subset, BQApi.getRegisteredChapterUuids(), instance.keySet())
        );
    }

    @Inject(
        method = "load(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V",
        at = @At("TAIL")
    )
    private static void eyeofharmonybuffer$afterLoad(
        ICommandSender sender,
        String databaseName,
        File dataDir,
        boolean loadWorldSettings,
        CallbackInfo ci
    ) {
        BQReinjector.reinject(
            FMLCommonHandler.instance().getMinecraftServerInstance()
        );
    }

    @Inject(
        method = "loadLegacy(Lnet/minecraft/command/ICommandSender;Ljava/lang/String;Ljava/io/File;Z)V",
        at = @At("TAIL")
    )
    private static void eyeofharmonybuffer$afterLoadLegacy(
        ICommandSender sender,
        String databaseName,
        File legacyFile,
        boolean loadWorldSettings,
        CallbackInfo ci
    ) {
        BQReinjector.reinject(
            FMLCommonHandler.instance().getMinecraftServerInstance()
        );
    }

    /**
     * 从 subset/allAvailable 里滤掉由 BQAPI 管理的 UUID。
     */
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
