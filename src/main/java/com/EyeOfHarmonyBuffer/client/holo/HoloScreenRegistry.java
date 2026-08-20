package com.EyeOfHarmonyBuffer.client.holo;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 全息屏注册表：类型 id → 工厂。每次 create 都返回独立实例（状态互不共享）。
 * 框架本身不注册任何具体屏 —— 具体屏由业务侧注册（如 RBMK 在 RbmkHoloPoC 里注册 panel/core）。
 * 新增一块屏 = 写一个 HoloScreen 子类 + register 一行，无需改框架。
 */
public class HoloScreenRegistry {

    private static final Map<String, Supplier<HoloScreen>> TYPES = new HashMap<>();

    public static void register(String id, Supplier<HoloScreen> factory) {
        TYPES.put(id, factory);
    }

    /** 创建一块新屏（独立实例）；未注册的 id 返回 null。 */
    public static HoloScreen create(String id) {
        Supplier<HoloScreen> f = TYPES.get(id);
        return f == null ? null : f.get();
    }

    public static boolean contains(String id) {
        return TYPES.containsKey(id);
    }
}
