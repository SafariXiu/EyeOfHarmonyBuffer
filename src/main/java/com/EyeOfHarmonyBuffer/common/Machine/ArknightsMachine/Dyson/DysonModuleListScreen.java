package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import com.EyeOfHarmonyBuffer.client.holo.HoloCanvas;
import com.EyeOfHarmonyBuffer.client.holo.HoloScreen;
import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.DysonCore.HoloModuleData;

/**
 * Q/R/S 模块列表屏：显示已链接模块（是否开启、维度 + 坐标）。
 * <p>
 * 三块屏主体相同，各自显示一段槽位（锚点由结构 'Q'/'R'/'S' 位决定，服务端按链接顺序切段）：
 * <ul>
 *   <li>segment 0（Q）：槽 1~10</li>
 *   <li>segment 1（R）：槽 11~20</li>
 *   <li>segment 2（S）：槽 21~32（仅戴森球成型后允许）</li>
 * </ul>
 * 每槽三种状态：有模块（显示类型/是否开启/维度/坐标）、激活范围内未连接（空）、超出激活槽（未激活）。
 * 模块列表是动态连接列表（断链自动消失、后续模块前移补位），由服务端同步。
 */
public class DysonModuleListScreen extends HoloScreen {

    private final DysonCore core;
    /** 0=Q(槽1-10) 1=R(槽11-20) 2=S(槽21-32)。 */
    private final int segment;

    private static final int TITLE_H = 40;
    private static final int ROW_H = 40;
    private static final int MARGIN = 16;

    private static final int C_BG = 0xF0101820;
    private static final int C_ACCENT = 0xFF2A6B8F;
    private static final int C_WHITE = 0xFFFFFFFF;
    private static final int C_GRAY = 0xFFAAAAAA;
    private static final int C_DIM = 0xFF666666;
    private static final int C_GREEN = 0xFF88FF88;
    private static final int C_RED = 0xFFFF5555;
    private static final int C_GOLD = 0xFFFFAA00;
    private static final int C_TRACK = 0xFF0A0A0A;

    public DysonModuleListScreen(DysonCore core, int segment) {
        super(448, 576);
        this.core = core;
        this.segment = segment;
    }

    private static int rows(int seg) {
        return seg == 2 ? 12 : 10;
    }

    @Override
    protected void buildWidgets() {
        // 纯展示屏：无控件
    }

    @Override
    protected int baseColor() {
        return C_BG;
    }

    @Override
    protected void drawBackground(HoloCanvas c) {
    }

    @Override
    protected void drawOverlay(HoloCanvas c) {
        String segName = segment == 0 ? "Q" : segment == 1 ? "R" : "S";
        // 顶部标题 + 蓝色装饰条
        c.rect(0, 0, w, 3, C_ACCENT);
        c.textCentered(w / 2, 8, "模块列表 (" + segName + ")", C_WHITE);
        c.textCentered(w / 2, 22,
            "激活槽 " + core.holoActiveSlots + " / " + core.holoModules.size() + " 台已连接", C_GRAY);

        int rows = rows(segment);
        int startIdx = segment * 10; // Q:0, R:10, S:20
        for (int r = 0; r < rows; r++) {
            int idx = startIdx + r;
            int y = TITLE_H + MARGIN + r * ROW_H;
            int slotNo = idx + 1;
            if (idx < core.holoModules.size()) {
                HoloModuleData m = core.holoModules.get(idx);
                c.text(MARGIN, y, String.format("槽 %02d  %s", slotNo, typeName(m.type)), C_GOLD);
                String status = m.active ? "开启" : "关闭";
                c.text(170, y, status, m.active ? C_GREEN : C_RED);
                c.text(250, y, String.format("维度 %d  (%d,%d,%d)", m.dim, m.x, m.y, m.z), C_WHITE);
            } else if (idx < core.holoActiveSlots) {
                // 激活范围内但未连接：空槽
                c.text(MARGIN, y, String.format("槽 %02d  ——", slotNo), C_DIM);
                c.text(170, y, "未连接", C_DIM);
            } else {
                // 超出当前激活槽数：未激活
                c.text(MARGIN, y, String.format("槽 %02d  ——", slotNo), C_DIM);
                c.text(170, y, "未激活", C_DIM);
            }
            if (r < rows - 1) {
                c.rect(MARGIN, y + ROW_H - 1, w - MARGIN * 2, 1, 0xFF1A1A1A);
            }
        }
    }

    /** ModuleType ordinal → 显示名。 */
    private static String typeName(int ordinal) {
        switch (ordinal) {
            case 0:
                return "制造";
            case 1:
                return "发射";
            case 2:
                return "接收";
            case 3:
                return "功能";
            default:
                return "未知";
        }
    }
}
