package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTLanguageManager;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import java.util.*;

import static com.EyeOfHarmonyBuffer.common.Block.BasicBlocks.MetaBlock01;
import static com.EyeOfHarmonyBuffer.utils.MetaItemStackUtils.initMetaItemStack;
import static com.EyeOfHarmonyBuffer.utils.MetaItemStackUtils.metaItemStackTooltipsAdd;

public class ItemBlockBase01 extends ItemBlock {

    // region statics

    public static final Map<Integer, String[]> MetaBlockTooltipsMap01 = new HashMap<>();
    public static final Set<Integer> MetaBlockSet01 = new HashSet<>();

    private static final String KEY_NO_MOBS = "gt.nomobspawnsonthisblock";
    private static final String KEY_NO_TILEENTITY = "gt.notileentityinthisblock";

    // endregion
    // -----------------------
    // region Constructors

    public ItemBlockBase01(Block aBlock) {
        super(aBlock);
        setHasSubtypes(true);
        setMaxDamage(0);
        this.setCreativeTab(EOHBCreativeTabs.TAB_META_BLOCKS);
    }

    // endregion
    // -----------------------
    // region MetaBlock Generators

    public static ItemStack initMetaBlock01(String i18nName, int Meta) {
        return initMetaItemStack(i18nName, Meta, MetaBlock01, MetaBlockSet01);
    }

    public static ItemStack initMetaBlock01(String i18nName, int Meta, String[] tooltips) {
        if (tooltips != null) {
            metaItemStackTooltipsAdd(MetaBlockTooltipsMap01, Meta, tooltips);
        }
        return initMetaBlock01(i18nName, Meta);
    }

    // endregion
    // -----------------------
    // region Overrides

    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack aItemStack, EntityPlayer player, List tooltip, boolean advanced) {
        int meta = aItemStack.getItemDamage();

        String[] extra = MetaBlockTooltipsMap01.get(meta);
        if (extra != null) {
            tooltip.addAll(Arrays.asList(extra));
        }

        tooltip.add(StatCollector.translateToLocal(KEY_NO_MOBS));
        tooltip.add(StatCollector.translateToLocal(KEY_NO_TILEENTITY));
    }

    @Override
    public String getUnlocalizedName(ItemStack aStack) {
        return this.field_150939_a.getUnlocalizedName() + "." + this.getDamage(aStack);
    }

    @Override
    public int getMetadata(int aMeta) {
        return aMeta;
    }

    // endregion
}
