package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.common.api.EnumBottleFluid;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

import java.util.Arrays;
import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemBottleBase extends Item {

    protected final String emptyBottleTexture;
    protected final EnumBottleFluid[] allowedFluids;

    @SideOnly(Side.CLIENT)
    protected IIcon emptyIcon;

    @SideOnly(Side.CLIENT)
    protected IIcon[] fluidIcons;

    public ItemBottleBase(String unlocName,
                          String emptyBottleTexture) {
        this(unlocName, emptyBottleTexture, new EnumBottleFluid[0]);
    }

    public ItemBottleBase(String unlocName,
                          String emptyBottleTexture,
                          EnumBottleFluid... allowedFluids) {

        this.setHasSubtypes(true);
        this.setMaxDamage(0);
        this.setCreativeTab(tabMetaItem01);
        this.setUnlocalizedName(unlocName);

        this.emptyBottleTexture = emptyBottleTexture;
        this.allowedFluids = allowedFluids;
    }

    @Override
    public int getMetadata(int damage) {
        return damage;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister reg) {

        String folder = "Arknights/";

        this.emptyIcon = reg.registerIcon(EyeOfHarmonyBuffer.MODID + ":" + folder + emptyBottleTexture);

        EnumBottleFluid[] fluids = EnumBottleFluid.values();
        this.fluidIcons = new IIcon[fluids.length];
        for (int i = 0; i < fluids.length; i++) {
            if (fluids[i] == EnumBottleFluid.EMPTY) {
                fluidIcons[i] = null;
            } else {
                fluidIcons[i] = reg.registerIcon(
                    EyeOfHarmonyBuffer.MODID + ":" + folder + fluids[i].texture
                );
            }
        }

        this.itemIcon = emptyIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public int getRenderPasses(int meta) {
        return 2;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        EnumBottleFluid fluid = EnumBottleFluid.fromMeta(meta);

        if (pass == 0) {
            return emptyIcon;
        } else {
            if (fluid == EnumBottleFluid.EMPTY) return null;
            return fluidIcons[fluid.ordinal()];
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        if (allowedFluids == null || allowedFluids.length == 0) {
            return;
        }

        for (EnumBottleFluid fluid : allowedFluids) {
            list.add(new ItemStack(item, 1, fluid.meta));
        }
    }

    public boolean isFluidAllowed(EnumBottleFluid fluid) {
        if (fluid == EnumBottleFluid.EMPTY) {
            return true;
        }
        return Arrays.asList(allowedFluids).contains(fluid);
    }

    public EnumBottleFluid getFluid(ItemStack stack) {
        return EnumBottleFluid.fromMeta(stack.getItemDamage());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        EnumBottleFluid fluid = EnumBottleFluid.fromMeta(stack.getItemDamage());
        return "item." + this.getUnlocalizedName().substring(5) + "." + fluid.name().toLowerCase();
    }

    @SideOnly(Side.CLIENT)
    public IIcon getEmptyIcon() {
        return emptyIcon;
    }

    @SideOnly(Side.CLIENT)
    public IIcon getFluidIcon(EnumBottleFluid fluid) {
        if (fluid == null || fluid == EnumBottleFluid.EMPTY || fluidIcons == null) {
            return null;
        }
        int idx = fluid.ordinal();
        if (idx < 0 || idx >= fluidIcons.length) {
            return null;
        }
        return fluidIcons[idx];
    }
}
