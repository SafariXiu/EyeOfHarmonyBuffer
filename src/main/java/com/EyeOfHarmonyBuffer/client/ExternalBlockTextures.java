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

    public static int HEMPCRETE_META14_INDEX;
    public static int HEMPCRETE_META15_INDEX;
    public static int HEMPCRETE_META12_INDEX;

    public static void registerCommon() {
        HEMPCRETE_META14_INDEX = allocIndex("chisel:hempCrete/concrete/red");
        HEMPCRETE_META15_INDEX = allocIndex("chisel:hempCrete/concrete/black");
        HEMPCRETE_META12_INDEX = allocIndex("chisel:hempCrete/concrete/brown");
    }

    private static int allocIndex(String key) {
        int slot = switch (key) {
            case "chisel:hempCrete/concrete/red" -> 0;
            case "chisel:hempCrete/concrete/black" -> 1;
            case "chisel:hempCrete/concrete/brown" -> 2;
            default -> throw new IllegalArgumentException("Unknown casing key: " + key);
        };
        return (CUSTOM_CASING_PAGE << 7) + slot;
    }

    @SideOnly(Side.CLIENT)
    public static void registerClientTextures() {
        putTextureAtSlot(0, "chisel:hempCrete/concrete/red");
        putTextureAtSlot(1, "chisel:hempCrete/concrete/black");
        putTextureAtSlot(2, "chisel:hempCrete/concrete/brown");
    }

    @SideOnly(Side.CLIENT)
    private static void putTextureAtSlot(int slot, String texturePath) {
        byte page = (byte) CUSTOM_CASING_PAGE;
        if (Textures.BlockIcons.casingTexturePages[page] == null) {
            Textures.BlockIcons.casingTexturePages[page] = new ITexture[128];
        }
        ITexture[] pageArray = Textures.BlockIcons.casingTexturePages[page];

        IIconContainer iconContainer = Textures.BlockIcons.custom(texturePath);
        ITexture texture = TextureFactory.of(iconContainer);

        pageArray[slot] = texture;
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
