package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.util.GTLanguageManager;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import java.util.*;

import static com.EyeOfHarmonyBuffer.utils.MetaItemStackUtils.initMetaItemStack;
import static com.EyeOfHarmonyBuffer.utils.MetaItemStackUtils.metaItemStackTooltipsAdd;
import static com.Nxer.TwistSpaceTechnology.common.init.TstBlocks.MetaBlock01;

public class ItemBlockBase01 extends ItemBlock {

    // region statics

    public static final Map<Integer, String[]> MetaBlockTooltipsMap01 = new HashMap<>();
    // public static final Map<Integer, ItemStack> MetaBlockMap01 = new HashMap<>();
    public static final Set<Integer> MetaBlockSet01 = new HashSet<>();

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
    // region Member Variables

    public final String mNoMobsToolTip = GTLanguageManager
        .addStringLocalization("gt.nomobspawnsonthisblock", "Mobs cannot Spawn on this Block");
    public final String mNoTileEntityToolTip = GTLanguageManager
        .addStringLocalization("gt.notileentityinthisblock", "This is NOT a TileEntity!");

    // endregion
    // -----------------------
    // region Overrides

    /**
     * Handle the tooltips.
     *
     * @param aItemStack
     * @param theTooltipsList
     */
    @SideOnly(Side.CLIENT)
    @Override
    @SuppressWarnings({ "unchecked" })
    public void addInformation(ItemStack aItemStack, EntityPlayer p_77624_2_, List theTooltipsList,
                               boolean p_77624_4_) {
        int meta = aItemStack.getItemDamage();
        if (null != MetaBlockTooltipsMap01.get(meta)) {
            String[] tooltips = MetaBlockTooltipsMap01.get(meta);
            theTooltipsList.addAll(Arrays.asList(tooltips));
        }
        theTooltipsList.add(mNoMobsToolTip);
        theTooltipsList.add(mNoTileEntityToolTip);
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
