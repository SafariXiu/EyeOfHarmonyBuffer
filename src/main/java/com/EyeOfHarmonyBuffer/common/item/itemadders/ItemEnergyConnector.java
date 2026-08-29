package com.EyeOfHarmonyBuffer.common.item.itemadders;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import com.EyeOfHarmonyBuffer.common.misc.LinkNodeEntry;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
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
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_NotOrundumDevice));
            return true;
        }

        IGregTechTileEntity base = (IGregTechTileEntity) te;
        if (base.getMetaTileEntity() == null ||
            !(base.getMetaTileEntity() instanceof OrundumWirelessMultiMachineBase)) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_NotOrundumDevice));
            return true;
        }

        @SuppressWarnings("unchecked")
        OrundumWirelessMultiMachineBase<?> machine =
            (OrundumWirelessMultiMachineBase<?>) base.getMetaTileEntity();

        OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
        if (data == null) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_DataNotLoaded));
            return true;
        }

        UUID nodeId = machine.getOrundumLinkNodeId();
        if (nodeId == null) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_NotRegistered));
            return true;
        }

        LinkNodeEntry.NodeType nodeType = machine.getNodeTypeForConnector();
        if (nodeType == null) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_NotParticipating));
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
                String.format(TextLocalization.EOHB_EnergyConnector_SelectedParent, nodeId.toString(), nodeType.name())
            ));
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_RClickToLink));
            return true;
        }

        String parentIdStr = tag.getString(TAG_PARENT_NODE_ID);
        UUID parentId;
        try {
            parentId = UUID.fromString(parentIdStr);
        } catch (IllegalArgumentException e) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_InvalidParentId));
            clearParentTag(stack);
            return true;
        }

        UUID childId = nodeId;

        if (parentId.equals(childId)) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_CannotSelfLink));
            return true;
        }

        LinkNodeEntry parentEntry = data.getNode(parentId);
        if (parentEntry == null) {
            player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_ParentGone));
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
                    String.format(TextLocalization.EOHB_EnergyConnector_LinkCreated, parentId, childId, maxDistance)
                ));
                clearParentTag(stack);
            }
            case INVALID_ID -> player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_InvalidId));
            case NODE_NOT_FOUND -> player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_NodeNotFound));
            case DIFFERENT_TEAM -> player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_DifferentTeam));
            case DIFFERENT_DIM -> player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_DifferentDim));
            case TOO_FAR -> player.addChatMessage(new ChatComponentText(
                String.format(TextLocalization.EOHB_EnergyConnector_TooFar, maxDistance)
            ));
            case SAME_NODE -> player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_CannotSelfLink));
            case ALREADY_HAS_PARENT -> player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_AlreadyHasParent));
        }

        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote && player.isSneaking()) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag != null && tag.hasKey(TAG_PARENT_NODE_ID)) {
                clearParentTag(stack);
                player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_ClearedParent));
            } else {
                player.addChatMessage(new ChatComponentText(TextLocalization.EOHB_EnergyConnector_NoParent));
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
            list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_SelectedParent + parentId);

            if (tag.hasKey(TAG_PARENT_NODE_TYPE)) {
                String typeName = tag.getString(TAG_PARENT_NODE_TYPE);
                list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_ParentType + typeName);
            }

            if (tag.hasKey(TAG_PARENT_MAX_DIST)) {
                int maxDist = tag.getInteger(TAG_PARENT_MAX_DIST);
                list.add(String.format(TextLocalization.EOHB_EnergyConnector_Tooltip_MaxDist, maxDist));
            }

            list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_LinkHint);
            list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_ClearHint);
        } else {
            list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_SelectHint);
            list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_LinkHint2);
            list.add(TextLocalization.EOHB_EnergyConnector_Tooltip_ClearHint);
        }
    }
}
