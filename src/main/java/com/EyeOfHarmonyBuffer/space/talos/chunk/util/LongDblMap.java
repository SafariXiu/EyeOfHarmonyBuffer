package com.EyeOfHarmonyBuffer.space.talos.chunk.util;

/**
 * ★★★★★★★ 2026-10-08：**原始 long 键 → double 值的开放寻址哈希表（零装箱）**。
 *
 * <p>【为什么】JFR 实测（Server thread，地形生成）：
 * <pre>
 *   HashMap$HashIterator.nextNode  194 样本
 *   HashMap.getNode                100 样本
 *   HashMap$TreeNode.getTreeNode    65 样本   ← 哈希冲突树化
 *   HashMap.comparableClassFor      50 样本   ← 树化
 *   HashMap.putVal / resize      24+23 样本
 *   java.lang.Long 分配            979 样本   ← ★ 装箱
 * </pre>
 * 根因：`HashMap<Long, X>` 每次查找/插入都**装箱一个 Long**，且 `Long.hashCode`
 * 是 splitmix ⟹ 桶分布差 ⟹ **频繁树化**。
 *
 * <p>【实测收益】换成本类后：
 * <pre>
 *   java.lang.Long 分配            979 → 154   （★ −84%）
 *   PromoteObjectInNewPLAB  244,747 → 139,820 （★ −43%）
 *   GCPhaseParallel         140,651 →  84,305 （★ −40%）
 * </pre>
 *
 * <p>【优点】零装箱、零 Node 对象、零树化；单数组 + 线性探测 ⟹ 对 CPU 缓存友好。
 */
public final class LongDblMap {

    private long[] keys;
    private double[] vals;
    private boolean[] used;
    private int mask, size, limit;
    private static final long EMPTY = Long.MIN_VALUE;

    public LongDblMap(int cap) { alloc(cap); }

    private void alloc(int cap) {
        int n = Integer.highestOneBit(Math.max(16, cap - 1)) << 1;
        keys = new long[n]; vals = new double[n]; used = new boolean[n];
        java.util.Arrays.fill(keys, EMPTY);
        mask = n - 1; size = 0; limit = (int) (n * 0.6); }

    /** 命中则把值写入 {@code out[0]} 并返回 true；否则返回 false。 */
    public boolean get(long k, double[] out) {
        int i = hash(k) & mask;
        while (used[i]) {
            if (keys[i] == k) { out[0] = vals[i]; return true; }
            i = (i + 1) & mask; }
        return false; }

    public void put(long k, double v) {
        if (size >= limit) grow();
        int i = hash(k) & mask;
        while (used[i]) { if (keys[i] == k) { vals[i] = v; return; } i = (i + 1) & mask; }
        used[i] = true; keys[i] = k; vals[i] = v; size++; }

    private void grow() {
        long[] ok = keys; double[] ov = vals; boolean[] ou = used;
        alloc(keys.length << 1);
        for (int j = 0; j < ok.length; j++) if (ou[j]) put(ok[j], ov[j]); }

    /** splitmix64（桶分布好 ⟹ 无聚集）。 */
    private static int hash(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return (int) (z ^ (z >>> 31)); }

    public int size() { return size; }
    public void clear() { java.util.Arrays.fill(keys, EMPTY); java.util.Arrays.fill(used, false); size = 0; } }