package com.EyeOfHarmonyBuffer.common.material;

import java.util.HashSet;
import java.util.Set;

import gregtech.api.enums.Element;
import gregtech.api.enums.MaterialBuilder;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TextureSet;
import gregtech.api.interfaces.IMaterialHandler;

/**
 * Talos 三种矿石的 GT 原生材料注册。
 * 必须在 GT preInit 的 Materials.init() 之前通过 Materials.add 注册
 * （由主类静态块触发），这样 GTOreAdapter 才能为所有石头类型
 * 生成对应的矿石变体（普通石、黑/红花岗岩、大理石、玄武岩、深板岩、凝灰岩等）。
 */
public class EOHBGTMaterials implements IMaterialHandler {

    public static Materials YiTie;
    public static Materials QingMengKuang;
    public static Materials ZhuanZhiYan;

    @Override
    public void onMaterialsInit() {
        YiTie = new MaterialBuilder()
            .setName("Oriron")
            .setDefaultLocalName("Oriron")
            .setElement(Element._NULL)
            .setARGB(0xFF607078)
            .setIconSet(TextureSet.SET_METALLIC)
            .addOreItems()
            .constructMaterial();
        assignFreeMaterialId(YiTie);

        QingMengKuang = new MaterialBuilder()
            .setName("ManganeseOre")
            .setDefaultLocalName("Manganese Ore")
            .setElement(Element._NULL)
            .setARGB(0xFF74608A)
            .setIconSet(TextureSet.SET_SHINY)
            .addOreItems()
            .constructMaterial();
        assignFreeMaterialId(QingMengKuang);

        ZhuanZhiYan = new MaterialBuilder()
            .setName("TransmutedSalt")
            .setDefaultLocalName("Transmuted Salt")
            .setElement(Element._NULL)
            .setARGB(0xFFE6E4DA)
            .setIconSet(TextureSet.SET_FINE)
            .addOreItems()
            .constructMaterial();
        assignFreeMaterialId(ZhuanZhiYan);
    }

    /**
     * GT 的 MaterialBuilder 不会自动分配材料 ID（mMetaItemSubID 默认 -1），
     * 没有 ID 的材料不会生成任何矿石/物品。这里从 GT 已注册材料中找出空闲 ID 并占用。
     */
    private static void assignFreeMaterialId(Materials material) {
        Set<Integer> used = new HashSet<>();
        for (Materials m : Materials.getMaterialsMap().values()) {
            if (m.mMetaItemSubID >= 0) {
                used.add(m.mMetaItemSubID);
            }
        }
        for (int id = 0; id < 1000; id++) {
            if (!used.contains(id)) {
                material.mMetaItemSubID = id;
                return;
            }
        }
        throw new IllegalStateException("No free GT material ID available for " + material.getInternalName());
    }
}
