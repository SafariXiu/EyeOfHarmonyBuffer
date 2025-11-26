package com.EyeOfHarmonyBuffer.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

public class ExternalBlockTextures {

    private ExternalBlockTextures() {}

    public static final int CUSTOM_CASING_PAGE = 60;

    public static int HEMPCRETE_META14_INDEX = -1;
    public static int HEMPCRETE_META15_INDEX = 0;
    public static int HEMPCRETE_META12_INDEX = 1;

    @SideOnly(Side.CLIENT)
    public static void register() {
        HEMPCRETE_META14_INDEX = registerCasingTexture("chisel:hempCrete/concrete/red");
        HEMPCRETE_META15_INDEX = registerCasingTexture("chisel:hempCrete/concrete/black");
        HEMPCRETE_META12_INDEX = registerCasingTexture("chisel:hempCrete/concrete/brown");
    }

    @SideOnly(Side.CLIENT)
    public static int registerCasingTexture(String texturePath) {
        byte page = CUSTOM_CASING_PAGE;
        if (Textures.BlockIcons.casingTexturePages[page] == null) {
            Textures.BlockIcons.casingTexturePages[page] = new ITexture[128];
        }

        ITexture[] pageArray = Textures.BlockIcons.casingTexturePages[page];
        int freeIndex = 0;
        while (freeIndex < 128 && pageArray[freeIndex] != null) freeIndex++;

        IIconContainer iconContainer = new Textures.BlockIcons.CustomIcon(texturePath);
        ITexture texture = TextureFactory.of(iconContainer);

        pageArray[freeIndex] = texture;
        int casingIndex = (page << 7) + freeIndex;

        System.out.println("[EOHB] Registered " + texturePath +
            " -> casingTexturePages[" + page + "][" + freeIndex + "], casingIndex = " + casingIndex);
        return casingIndex;
    }

    @SideOnly(Side.CLIENT)
    public static ITexture getCasingFromIndex(int casingIndex) {
        if (casingIndex < 0) return null;

        int page  = casingIndex >> 7;
        int index = casingIndex & 0x7F;

        ITexture[][] pages = Textures.BlockIcons.casingTexturePages;
        if (page < 0 || page >= pages.length) return null;

        ITexture[] pageArray = pages[page];
        if (pageArray == null || index < 0 || index >= pageArray.length) return null;

        return pageArray[index];
    }
}
