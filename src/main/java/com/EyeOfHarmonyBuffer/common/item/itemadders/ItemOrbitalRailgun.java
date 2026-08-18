package com.EyeOfHarmonyBuffer.common.item.itemadders;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.utils.TextHandler;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

/**
 * 轨道炮物品（移植自 orbital-railgun，MIT License，见 LICENSE-orbital-railgun.txt）。
 * 按住右键充能 -> 充能完成后左键开火，服务端收到请求后执行轨道打击。
 * 物品材质暂时复用 KuangMaiCaiJIZhe，后续替换为正式模型。
 */
public class ItemOrbitalRailgun extends Item {

    public static final String TOOLTIP_KEY_00 = "EOHB_OrbitalRailgun_Tooltip_00";
    public static final String TOOLTIP_KEY_01 = "EOHB_OrbitalRailgun_Tooltip_01";

    public ItemOrbitalRailgun() {
        super();
        this.setUnlocalizedName("OrbitalRailgun");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":KuangMaiCaiJIZhe");
        this.setCreativeTab(tabMetaItem01);
        this.setMaxStackSize(1);
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.none;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    @Override
    public void onPlayerStoppedUsing(ItemStack stack, World world, EntityPlayer player, int timeLeft) {
        // 松开右键即取消充能，无其他副作用
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(TextHandler.texter("Hold right-click to charge the orbital strike, then left-click to fire.", TOOLTIP_KEY_00));
        list.add(TextHandler.texter("The target area is completely annihilated. Even bedrock will not survive.", TOOLTIP_KEY_01));
    }
}
