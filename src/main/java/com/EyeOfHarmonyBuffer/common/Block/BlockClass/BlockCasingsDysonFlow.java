package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockGlowCasing;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_DysonCoreInfoFlow_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_DysonCoreInfoFlow_01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_DysonLaunchCenterEnergyFlow_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_DysonLaunchCenterEnergyFlow_01;

/**
 * 戴森系列可点亮机械外壳（可点亮，带 CTM 连接纹理）。
 * <p>
 * meta 布局：低 3 位 = 变体，第 3 位（+8）= 点亮标志。
 * <ul>
 *   <li>变体 0 = 戴森核心信息导流块（贴图文件夹 Arknights/HunNingTuDaoXian/）</li>
 *   <li>变体 1 = 戴森发射中心能量流转块（贴图文件夹 Arknights/FaSheDanYuanDaoXian/DS_LaunchUnit_）</li>
 * </ul>
 * 连接贴图命名约定见 {@link BlockGlowCasingBase} 类注释。
 */
public class BlockCasingsDysonFlow extends BlockGlowCasingBase {

    public static final int META_DYSON_FLOW = 0;
    public static final int META_DYSON_LAUNCH_FLOW = 1;

    public BlockCasingsDysonFlow() {
        super(ItemBlockGlowCasing.class, "eoh.blockcasingsdysonflow");
        register(META_DYSON_FLOW, null, () -> Tooltip_DysonCoreInfoFlow_00, () -> Tooltip_DysonCoreInfoFlow_01);
        register(META_DYSON_LAUNCH_FLOW, null,
            () -> Tooltip_DysonLaunchCenterEnergyFlow_00,
            () -> Tooltip_DysonLaunchCenterEnergyFlow_01);
    }

    @Override
    protected int getVariantCount() {
        return 2;
    }

    @Override
    protected String getIconBasePath(int variant) {
        switch (variant) {
            case META_DYSON_LAUNCH_FLOW:
                return "Arknights/FaSheDanYuanDaoXian/DS_LaunchUnit_";
            case META_DYSON_FLOW:
            default:
                return "Arknights/HunNingTuDaoXian/DS_Core_";
        }
    }
}
