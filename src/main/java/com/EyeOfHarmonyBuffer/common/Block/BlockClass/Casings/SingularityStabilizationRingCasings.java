package com.EyeOfHarmonyBuffer.common.Block.BlockClass.Casings;

import com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs;
import com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockBase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.GregTechAPI;
import net.minecraft.block.Block;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.EyeOfHarmonyBuffer.common.Block.BasicBlocks.SingularityStabilizationRingCasingsUpgrade;
import static com.EyeOfHarmonyBuffer.common.Block.BlockClass.BlockStaticDataClientOnly.iconsSingularityStabilizationRingCasingsUpgradeMap;
import static com.EyeOfHarmonyBuffer.utils.MetaItemStackUtils.initMetaItemStack;
import static com.EyeOfHarmonyBuffer.utils.TextHandler.texter;

public class SingularityStabilizationRingCasings extends BlockBase {

    public SingularityStabilizationRingCasings() {
        this.setHardness(9.0F);
        this.setResistance(5.0F);
        this.setHarvestLevel("wrench", 1);
        this.setCreativeTab(EOHBCreativeTabs.tabGTCMGeneralTab);
        SingularityStabilizationRingCasingsSet.add(0);
        GregTechAPI.registerMachineBlock(this, -1);
    }

    public SingularityStabilizationRingCasings(String unlocalizedName, String localName) {
        this();
        this.unlocalizedName = unlocalizedName;
        texter(localName, unlocalizedName + ".name");
    }

    // endregion
    // -----------------------
    // region Member Variables

    public static Set<Integer> SingularityStabilizationRingCasingsSet = new HashSet<>();

    /**
     * Tooltips of these blocks' ItemBlock.
     */
    public static String[][] SingularityStabilizationRingCasingsTooltipsArray = new String[14][];
    private IIcon blockIcon;
    private String unlocalizedName;

    // endregion
    // -----------------------
    // region Meta Generator

    public static ItemStack SingularityStabilizationRingCasingsMeta(String i18nName, int meta) {

        return initMetaItemStack(i18nName, meta, SingularityStabilizationRingCasingsUpgrade, SingularityStabilizationRingCasingsSet);
    }

    public static ItemStack SingularityStabilizationRingCasingsMeta(String i18nName, int meta, String[] tooltips) {
        // Handle the tooltips
        SingularityStabilizationRingCasingsTooltipsArray[meta] = tooltips;
        return SingularityStabilizationRingCasingsMeta(i18nName, meta);
    }

    @Override
    public String getUnlocalizedName() {
        return this.unlocalizedName;
    }

    // endregion
    // -----------------------
    // region Overrides
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int side, int meta) {
        return meta < iconsSingularityStabilizationRingCasingsUpgradeMap.size() ? iconsSingularityStabilizationRingCasingsUpgradeMap.get(meta)
            : iconsSingularityStabilizationRingCasingsUpgradeMap.get(0);
    }

    public static int[] RecipeLevel = new int[] { 1,2,3,4,5,6,7,8,9,10,11,12,13,14 };
    // LV MV HV EV IV LuV ZPM UV UHV UEV UIV UMV UXV MAX

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister reg) {
        this.blockIcon = reg.registerIcon("eyeofharmonybuffer:PhotonControllerUpgrades/0");
        for (int Meta : SingularityStabilizationRingCasingsSet) {
            iconsSingularityStabilizationRingCasingsUpgradeMap
                .put(Meta, reg.registerIcon("eyeofharmonybuffer:PhotonControllerUpgrades/" + Meta));
        }
    }

    @Override
    public void onBlockAdded(World aWorld, int aX, int aY, int aZ) {
        if (GregTechAPI.isMachineBlock(this, aWorld.getBlockMetadata(aX, aY, aZ))) {
            GregTechAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }
    }

    @Override
    public void breakBlock(World aWorld, int aX, int aY, int aZ, Block aBlock, int aMetaData) {
        if (GregTechAPI.isMachineBlock(this, aWorld.getBlockMetadata(aX, aY, aZ))) {
            GregTechAPI.causeMachineUpdate(aWorld, aX, aY, aZ);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubBlocks(Item aItem, CreativeTabs aCreativeTabs, List list) {
        for (int Meta : SingularityStabilizationRingCasingsSet) {
            list.add(new ItemStack(aItem, 1, Meta));
        }
    }
}
