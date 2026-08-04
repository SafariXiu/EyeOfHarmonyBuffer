package com.EyeOfHarmonyBuffer.space.talos.chunk.cave_layer.format;

/**
 * 洞穴区域风格标签：按 256 格单元确定，供装饰器与外部层共同读取。
 * 后续新增风味洞穴时在这里扩展枚举。
 */
public enum CaveTag {
    DEFAULT("default", "普通洞穴"),
    SPIKE_ZONE("spike_zone", "石笋区");

    public final String id;
    public final String displayName;

    CaveTag(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }
}
