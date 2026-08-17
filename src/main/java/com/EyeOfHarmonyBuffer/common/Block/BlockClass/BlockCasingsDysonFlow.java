package com.EyeOfHarmonyBuffer.common.Block.BlockClass;

import com.EyeOfHarmonyBuffer.common.item.items.ItemBlockGlowCasing;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_DysonCoreInfoFlow_00;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.Tooltip_DysonCoreInfoFlow_01;

/**
 * 戴森核心信息导流块（可点亮机械外壳，带 CTM 连接纹理）。
 * meta 0 = 熄灯态（默认材质），meta 8 = 点亮态（蓝色发光贴图 + 光照 15）。
 * 连接贴图按约定放在文件夹 Arknights/HunNingTuDaoXian/ 内：
 *   conn_0..15 + conn_&lt;M&gt;_&lt;bits&gt;（熄灯）与 Ligth_conn_*（点亮）
 *
 * <p>后续新方块参考本类的 tooltip 注册方式：构造器里调用
 * {@code register(meta, null, () -> TextLocalization.某条目)}（GT 的 AnimatedTooltipHandler 样式）。
 */
public class BlockCasingsDysonFlow extends BlockGlowCasingBase {

    public static final int META_DYSON_FLOW = 0;

    public BlockCasingsDysonFlow() {
        super(ItemBlockGlowCasing.class, "eoh.blockcasingsdysonflow");
        register(META_DYSON_FLOW, null, () -> Tooltip_DysonCoreInfoFlow_00, () -> Tooltip_DysonCoreInfoFlow_01);
    }

    @Override
    protected int getVariantCount() {
        return 1;
    }

    @Override
    protected String getIconBasePath(int variant) {
        return "Arknights/HunNingTuDaoXian/";
    }
}
