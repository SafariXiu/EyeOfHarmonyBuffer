package com.EyeOfHarmonyBuffer.common.dyson;

/**
 * 单支队伍的戴森球建造进度（云 / 框架 / 贴片三计数器）。
 * <p>
 * - 云：发电源与贴片原料，上限 50,000；
 * - 框架：只提供贴片容量（1 框架 = 4 贴片槽），上限 500,000；
 * - 贴片：完工进度条与发电主体，上限 4 × 框架（满 2,000,000）。
 */
public class DysonTeamProgress {

    public int cloudCount;
    public int frameCount;
    public int pasteCount;

    /** 首次发射的世界时间（领先者并列时按先到者排序）。 */
    public long firstLaunchTick;

    public String teamName;

    public DysonTeamProgress() {
        this.teamName = "";
    }

    public DysonTeamProgress(String teamName) {
        this.teamName = teamName == null ? "" : teamName;
    }

    /** 当前框架可容纳的贴片数。 */
    public int getPasteCapacity(int pastePerFrame) {
        return pastePerFrame * frameCount;
    }
}
