package com.EyeOfHarmonyBuffer.space.talos.chunk.util;

import com.EyeOfHarmonyBuffer.space.talos.chunk.world.TalosSeed;
import net.minecraft.world.World;

/**
 * ★★★★★ <b>2026-10-14 审计新增：`com.EyeOfHarmonyBuffer.space.talos.chunk.util.TalosSeedMc.of(World)` 的 MC 侧落点。</b>
 *
 * <h3>为什么要有这个类</h3>
 * <p>探针 harness（{@code runprobe4.bat}）的隐含约定是：**复制清单里的类必须 MC-free** ✓
 * —— 因为它的 javac **没有 classpath** ✗（{@code javac -encoding UTF-8 -nowarn -d out @srcs.txt} ✓）。
 *
 * <p>而 {@code TalosSeed} 原本带一个 {@code import net.minecraft.world.World} ✗（只为
 * {@code of(World)} ✓），**违反了该约定** ✗ —— 但长期被 staging 里的一份**陈旧副本**掩盖 ✓
 * （那份副本是 {@code of(World)} 加进来之前的版本 ✓）。
 * 2026-10-14 清空 staging 后暴露：探针编译 {@code TalosSeed} 时报「程序包 net.minecraft.world 不存在」✗。
 *
 * <h3>修法（行为中性 ✓）</h3>
 * <p>把 {@code of(World)} **搬到这里** ✓（{@code TalosSeed} 保持纯算术 ✓、MC-free ✓），
 * 并把 11 个 MC 侧调用点改成 {@code com.EyeOfHarmonyBuffer.space.talos.chunk.util.TalosSeedMc.of(world)} ✓。
 * <p>★ <b>规则不变</b>：世界种子派生的唯一权威仍是 {@link TalosSeed} ✓ ——
 * 本类只是它的一个 MC 适配器 ✓（{@code World -> long -> int} ✓，不做任何派生 ✓）。
 */
public final class TalosSeedMc {

    private TalosSeedMc() {}

    /** ★ 官方入口（MC 侧）：{@code World.getSeed() -> 非负 int}。规则见 {@link TalosSeed#ofLong}。 */
    public static int of(World world) {
        return TalosSeed.ofLong(world.getSeed());
    }
}
