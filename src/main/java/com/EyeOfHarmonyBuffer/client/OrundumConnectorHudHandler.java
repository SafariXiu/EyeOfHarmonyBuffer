package com.EyeOfHarmonyBuffer.client;

import com.EyeOfHarmonyBuffer.common.item.itemadders.ItemEnergyConnector;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.client.event.RenderGameOverlayEvent;

public class OrundumConnectorHudHandler {

    private static final String TAG_PARENT_NODE_ID = "EOHB_ParentNodeId";
    private static final String TAG_PARENT_DIM = "EOHB_ParentDim";
    private static final String TAG_PARENT_X = "EOHB_ParentX";
    private static final String TAG_PARENT_Y = "EOHB_ParentY";
    private static final String TAG_PARENT_Z = "EOHB_ParentZ";
    private static final String TAG_PARENT_MAX_DIST = "EOHB_ParentMaxDist";

    private final Minecraft mc = Minecraft.getMinecraft();

    @SubscribeEvent
    public void onRenderGameOverlay(RenderGameOverlayEvent.Text event) {
        EntityPlayer player = mc.thePlayer;
        if (player == null) return;

        ItemStack held = player.getCurrentEquippedItem();
        if (held == null) return;
        if (!(held.getItem() instanceof ItemEnergyConnector)) return;

        NBTTagCompound tag = held.getTagCompound();
        if (tag == null || !tag.hasKey(TAG_PARENT_NODE_ID)) return;

        if (!tag.hasKey(TAG_PARENT_DIM) ||
            !tag.hasKey(TAG_PARENT_X) ||
            !tag.hasKey(TAG_PARENT_Y) ||
            !tag.hasKey(TAG_PARENT_Z)) {
            return;
        }

        int parentDim = tag.getInteger(TAG_PARENT_DIM);
        int playerDim = player.worldObj.provider.dimensionId;
        if (parentDim != playerDim) {
            drawCenteredString("父节点在另一维度，无法连接。", 0xFFFF5555);
            return;
        }

        int px = tag.getInteger(TAG_PARENT_X);
        int py = tag.getInteger(TAG_PARENT_Y);
        int pz = tag.getInteger(TAG_PARENT_Z);

        double dx = (px + 0.5) - player.posX;
        double dy = (py + 0.5) - player.posY;
        double dz = (pz + 0.5) - player.posZ;

        double horizontal = Math.sqrt(dx * dx + dz * dz);
        int horizInt = Math.round((float) horizontal);

        int maxDist = tag.hasKey(TAG_PARENT_MAX_DIST) ? tag.getInteger(TAG_PARENT_MAX_DIST) : -1;

        String text;
        int color;

        if (maxDist > 0) {
            text = String.format("父节点距离: %d 格 (上限 %d)", horizInt, maxDist);
            color = (horizInt <= maxDist) ? 0xFF55FF55 : 0xFFFF5555;
        } else {
            text = String.format("父节点距离: %d 格", horizInt);
            color = 0xFFFFFFFF;
        }

        drawCenteredString(text, color);
    }

    private void drawCenteredString(String text, int color) {
        ScaledResolution sr = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        int x = width / 2;
        int y = height - 60;

        mc.fontRenderer.drawStringWithShadow(text,
            x - mc.fontRenderer.getStringWidth(text) / 2,
            y,
            color
        );
    }
}
