package com.EyeOfHarmonyBuffer.common.item.itemadders;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_KuangMaiCaiJiZhe_Tooltip_00;

public class ItemKuangMaiCaiJiZhe extends Item {

    public ItemKuangMaiCaiJiZhe() {
        super();

        this.setMaxStackSize(1);
        this.setUnlocalizedName("KuangMaiCaiJiZhe");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":KuangMaiCaiJIZhe");
        this.setCreativeTab(tabMetaItem01);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(EOHB_KuangMaiCaiJiZhe_Tooltip_00);
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world,
                             int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {

        Block block = world.getBlock(x, y, z);

        if (isCollectableBlock(block)) {

            if (!world.isRemote) {
                int meta = world.getBlockMetadata(x, y, z);

                world.setBlockToAir(x, y, z);

                ItemStack drop = new ItemStack(block, 1, meta);

                float dx = 0.5F;
                float dy = 0.5F;
                float dz = 0.5F;

                EntityItem entityItem = new EntityItem(world,
                    x + dx, y + dy, z + dz,
                    drop);

                world.spawnEntityInWorld(entityItem);
            }

            return true;
        }

        return false;
    }

    private boolean isCollectableBlock(Block block) {
        return block == GTCMItemList.YuanShiMainBlock.getBlock()
            || block == GTCMItemList.LanTieMainBlock.getBlock()
            || block == GTCMItemList.ChiTongMainBlock.getBlock()
            || block == GTCMItemList.ZiJingMainBlock.getBlock()
            || block == GTCMItemList.XiRangQiMainBlock.getBlock()
            || block == GTCMItemList.DuoQiMainBlock.getBlock();
    }
}
