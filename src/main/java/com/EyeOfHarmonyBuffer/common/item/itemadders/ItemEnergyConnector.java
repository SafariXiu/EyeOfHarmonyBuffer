package com.EyeOfHarmonyBuffer.common.item.itemadders;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.common.misc.LinkNodeEntry;
import com.EyeOfHarmonyBuffer.common.misc.OrundumLinkNetworkData;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.world.World;

import java.util.UUID;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;

public class ItemEnergyConnector extends Item {

    private static final String TAG_PARENT_NODE_ID = "EOHB_ParentNodeId";
    private static final String TAG_PARENT_NODE_TYPE = "EOHB_ParentNodeType";
    private static final String TAG_PARENT_DIM = "EOHB_ParentDim";
    private static final String TAG_PARENT_X = "EOHB_ParentX";
    private static final String TAG_PARENT_Y = "EOHB_ParentY";
    private static final String TAG_PARENT_Z = "EOHB_ParentZ";
    private static final String TAG_PARENT_MAX_DIST = "EOHB_ParentMaxDist";

    public ItemEnergyConnector() {
        super();

        this.setMaxStackSize(1);
        this.setUnlocalizedName("EnergyConnector");
        this.setTextureName(EyeOfHarmonyBuffer.MODID + ":EnergyConnector");
        this.setCreativeTab(tabMetaItem01);
    }

    @Override
    public boolean onItemUse(ItemStack stack,
                             EntityPlayer player,
                             World world,
                             int x, int y, int z,
                             int side,
                             float hitX,
                             float hitY,
                             float hitZ) {

        if (world.isRemote) {
            return true;
        }

        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity)) {
            player.addChatMessage(new ChatComponentText("这个方块不是 Orundum 设备。"));
            return true;
        }

        IGregTechTileEntity base = (IGregTechTileEntity) te;
        if (base.getMetaTileEntity() == null ||
            !(base.getMetaTileEntity() instanceof OrundumWirelessMultiMachineBase)) {
            player.addChatMessage(new ChatComponentText("这个方块不是 Orundum 设备。"));
            return true;
        }

        @SuppressWarnings("unchecked")
        OrundumWirelessMultiMachineBase<?> machine =
            (OrundumWirelessMultiMachineBase<?>) base.getMetaTileEntity();

        OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
        if (data == null) {
            player.addChatMessage(new ChatComponentText("Orundum 链路数据尚未加载。"));
            return true;
        }

        UUID nodeId = machine.getOrundumLinkNodeId();
        if (nodeId == null) {
            player.addChatMessage(new ChatComponentText("该设备尚未注册到 Orundum 链路网络（可能刚加载或未正确成型）。"));
            return true;
        }

        LinkNodeEntry.NodeType nodeType = machine.getNodeTypeForConnector();
        if (nodeType == null) {
            player.addChatMessage(new ChatComponentText("该设备不参与 Orundum 链路网络。"));
            return true;
        }

        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            tag = new NBTTagCompound();
            stack.setTagCompound(tag);
        }

        if (!tag.hasKey(TAG_PARENT_NODE_ID)) {
            tag.setString(TAG_PARENT_NODE_ID, nodeId.toString());
            tag.setString(TAG_PARENT_NODE_TYPE, nodeType.name());

            int dimId = world.provider.dimensionId;
            int px = base.getXCoord();
            int py = base.getYCoord();
            int pz = base.getZCoord();
            tag.setInteger(TAG_PARENT_DIM, dimId);
            tag.setInteger(TAG_PARENT_X, px);
            tag.setInteger(TAG_PARENT_Y, py);
            tag.setInteger(TAG_PARENT_Z, pz);

            int maxDistance = (nodeType == LinkNodeEntry.NodeType.SUBSTATION) ? 100 : 500;
            tag.setInteger(TAG_PARENT_MAX_DIST, maxDistance);

            player.addChatMessage(new ChatComponentText(
                String.format("已选择父节点：%s（类型：%s）", nodeId.toString(), nodeType.name())
            ));
            player.addChatMessage(new ChatComponentText("对另一个 Orundum 设备右键以建立链路。"));
            return true;
        }

        String parentIdStr = tag.getString(TAG_PARENT_NODE_ID);
        UUID parentId;
        try {
            parentId = UUID.fromString(parentIdStr);
        } catch (IllegalArgumentException e) {
            player.addChatMessage(new ChatComponentText("物品中记录的父节点 ID 无效，已清除。"));
            clearParentTag(stack);
            return true;
        }

        UUID childId = nodeId;

        if (parentId.equals(childId)) {
            player.addChatMessage(new ChatComponentText("不能把设备自己连接到自己。"));
            return true;
        }

        LinkNodeEntry parentEntry = data.getNode(parentId);
        if (parentEntry == null) {
            player.addChatMessage(new ChatComponentText("父节点已不存在或未加载，已清除。"));
            clearParentTag(stack);
            return true;
        }

        int maxDistance;
        if (parentEntry.type == LinkNodeEntry.NodeType.SUBSTATION) {
            maxDistance = 100;
        } else {
            maxDistance = 500;
        }

        OrundumLinkNetworkData.LinkCreationResult result =
            data.tryCreateLink(parentId, childId, maxDistance);

        switch (result) {
            case OK -> {
                player.addChatMessage(new ChatComponentText(
                    String.format("成功创建链路：%s -> %s，最大距离=%d。", parentId, childId, maxDistance)
                ));
                clearParentTag(stack);
            }
            case INVALID_ID -> player.addChatMessage(new ChatComponentText("ID 无效，无法创建链路。"));
            case NODE_NOT_FOUND -> player.addChatMessage(new ChatComponentText("父节点或子节点不存在。"));
            case DIFFERENT_TEAM -> player.addChatMessage(new ChatComponentText("两个设备不属于同一团队，无法连接。"));
            case DIFFERENT_DIM -> player.addChatMessage(new ChatComponentText("两个设备不在同一维度，无法连接。"));
            case TOO_FAR -> player.addChatMessage(new ChatComponentText(
                String.format("距离过远（>%d 格，平面距离）。", maxDistance)
            ));
            case SAME_NODE -> player.addChatMessage(new ChatComponentText("不能把设备自己连接到自己。"));
            case ALREADY_HAS_PARENT -> player.addChatMessage(new ChatComponentText("子节点已经有上游了，先断开再重连。"));
        }

        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey(TAG_PARENT_NODE_ID)) {
                clearParentTag(stack);
                player.addChatMessage(new ChatComponentText("已清除当前选择的父节点。"));
            } else {
                player.addChatMessage(new ChatComponentText("当前没有已选择的父节点。"));
            }
        }

        return stack;
    }

    private void clearParentTag(ItemStack stack) {
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;

        tag.removeTag(TAG_PARENT_NODE_ID);
        tag.removeTag(TAG_PARENT_NODE_TYPE);
        tag.removeTag(TAG_PARENT_DIM);
        tag.removeTag(TAG_PARENT_X);
        tag.removeTag(TAG_PARENT_Y);
        tag.removeTag(TAG_PARENT_Z);
        tag.removeTag(TAG_PARENT_MAX_DIST);

        if (tag.hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings("unchecked")
    public void addInformation(ItemStack stack,
                               EntityPlayer player,
                               java.util.List list,
                               boolean advanced) {
        super.addInformation(stack, player, list, advanced);

        NBTTagCompound tag = stack.getTagCompound();
        if (tag != null && tag.hasKey(TAG_PARENT_NODE_ID)) {
            String parentId = tag.getString(TAG_PARENT_NODE_ID);
            list.add("已选择父节点: " + parentId);

            if (tag.hasKey(TAG_PARENT_NODE_TYPE)) {
                String typeName = tag.getString(TAG_PARENT_NODE_TYPE);
                list.add("父节点类型: " + typeName);
            }

            if (tag.hasKey(TAG_PARENT_MAX_DIST)) {
                int maxDist = tag.getInteger(TAG_PARENT_MAX_DIST);
                list.add("最大连接距离: " + maxDist + " 格（平面距离）");
            }

            list.add("对另一个 Orundum 设备右键以建立链路。");
            list.add("潜行+右键空气以清除当前父节点。");
        } else {
            list.add("对一个 Orundum 设备右键以选择父节点。");
            list.add("再对另一个设备右键以建立链路。");
            list.add("潜行+右键空气以清除当前父节点。");
        }
    }
}
