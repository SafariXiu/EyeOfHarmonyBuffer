package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.FuelRod;

import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public final class GTCMReactorFuelCells {

    public static Item YuanShiDepletedFuelRod1;
    public static Item YuanShiDepletedFuelRod2;
    public static Item YuanShiDepletedFuelRod4;

    public static Item YuanShiFuelRod1;
    public static Item YuanShiFuelRod2;
    public static Item YuanShiFuelRod4;

    private GTCMReactorFuelCells() {
    }

    public static void init() {
        //registerDepletedCells();
        registerFuelCells();
    }

    private static void registerDepletedCells() {
        YuanShiDepletedFuelRod1 = new Item()
            .setUnlocalizedName("YuanShiDepletedFuelRod1")
            .setTextureName("gtcm:DepletedRodThorium")
            .setCreativeTab(tabMetaItem01);
        GTCMItemList.YuanShiDepletedFuelRod1.set(
            registryAndCallback(YuanShiDepletedFuelRod1, "YuanShiDepletedFuelRod1")
        );

        YuanShiDepletedFuelRod2 = new Item()
            .setUnlocalizedName("YuanShiDepletedFuelRod2")
            .setTextureName("gtcm:DepletedRodThorium2")
            .setCreativeTab(tabMetaItem01);
        GTCMItemList.YuanShiDepletedFuelRod2.set(
            registryAndCallback(YuanShiDepletedFuelRod2, "YuanShiDepletedFuelRod2")
        );

        YuanShiDepletedFuelRod4 = new Item()
            .setUnlocalizedName("YuanShiDepletedFuelRod4")
            .setTextureName("gtcm:DepletedRodThorium4")
            .setCreativeTab(tabMetaItem01);
        GTCMItemList.YuanShiDepletedFuelRod4.set(
            registryAndCallback(YuanShiDepletedFuelRod4, "YuanShiDepletedFuelRod4")
        );
    }

    private static class FuelCellDef {
        final String registryName;
        final GTCMItemList enumEntry;
        final int cellCount;
        final int maxDamage;
        final float energy;
        final int radiation;
        final float heatMultiplier;
        final boolean isMox;
        final float moxBonus;
        final GTCMItemList depletedEnum;

        FuelCellDef(String registryName,
                    GTCMItemList enumEntry,
                    int cellCount,
                    int maxDamage,
                    float energy,
                    int radiation,
                    float heatMultiplier,
                    boolean isMox,
                    float moxBonus,
                    GTCMItemList depletedEnum) {
            this.registryName = registryName;
            this.enumEntry = enumEntry;
            this.cellCount = cellCount;
            this.maxDamage = maxDamage;
            this.energy = energy;
            this.radiation = radiation;
            this.heatMultiplier = heatMultiplier;
            this.isMox = isMox;
            this.moxBonus = moxBonus;
            this.depletedEnum = depletedEnum;
        }
    }

    private static void registerFuelCells() {
        List<FuelCellDef> defs = new ArrayList<>();

        defs.add(new FuelCellDef(
            "YuanShiFuelRod1",
            GTCMItemList.YuanShiFuelRod1,
            1, // 单棒
            50_000, // 寿命
            64F, // 每脉冲基础能量
            0, // 辐射
            1.0F, // 发热倍率
            false, // 是否 MOX
            1.0F, // MOX 加成
            null
        ));

        defs.add(new FuelCellDef(
            "YuanShiFuelRod2",
            GTCMItemList.YuanShiFuelRod2,
            2,
            50_000,
            512F,
            0,
            1.0F,
            false,
            1.0F,
            null
        ));

        defs.add(new FuelCellDef(
            "YuanShiFuelRod4",
            GTCMItemList.YuanShiFuelRod4,
            4,
            50_000,
            4096F,
            0,
            1.0F,
            false,
            1.0F,
            null
        ));

        for (FuelCellDef def : defs) {
            ItemStack depletedStack = def.depletedEnum == null ? null : def.depletedEnum.get(1);

            ItemReactorFuelCell cell = new ItemReactorFuelCell(
                def.registryName,
                def.cellCount,
                def.maxDamage,
                def.energy,
                def.radiation,
                def.heatMultiplier,
                depletedStack,
                def.isMox,
                def.moxBonus
            );
            cell.setTextureName("eyeofharmonybuffer:Arknights/" + def.registryName)
                .setCreativeTab(tabMetaItem01);

            ItemStack stack = registryAndCallback(cell, def.registryName);
            def.enumEntry.set(stack);

            if ("YuanShiFuelRod1".equals(def.registryName)) {
                YuanShiFuelRod1 = cell;
            } else if ("YuanShiFuelRod2".equals(def.registryName)) {
                YuanShiFuelRod2 = cell;
            } else if ("YuanShiFuelRod4".equals(def.registryName)) {
                YuanShiFuelRod4 = cell;
            }
        }
    }

    private static ItemStack registryAndCallback(Item item, String name) {
        GameRegistry.registerItem(item, name);
        return new ItemStack(item, 1, 0);
    }
}
