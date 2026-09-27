package com.EyeOfHarmonyBuffer.sim.ocean;

/**
 * **第三步接线的唯一世界级入口**（设计冻结 §162）。
 *
 * <h3>为什么单独有这个类</h3>
 * 生产里知道 {@code worldSeedInt} 的世界级入口是 {@code WorldChunkManagerTalos2} 的构造器，
 * 但它 {@code extends WorldChunkManagerSpace}（Galacticraft）⇒ 探针**编译不进去**，
 * 于是「接线代码本身」落在探针盲区里（runprobe4 的 SCOPE 段会报 NOT_IN_SCOPE）。
 * 把接线逻辑搬到这里，探针就能**和跑生产完全同一段代码**来装 SST，口径不可能漂。
 * `WorldChunkManagerTalos2` 只剩一行调用。
 *
 * <h3>它做三件事</h3>
 * <ol>
 *   <li>{@link OceanField#install(int)} —— 装 {@code Atmosphere.SST_PROVIDER}；</li>
 *   <li>后台线程 {@link OceanField#warmAll(int)} —— **预热 64 条纬度行**；</li>
 *   <li>★ <b>§7503</b>：{@link com.EyeOfHarmonyBuffer.sim.hydro.WaterField#install(int)} ——
 *       把地表水系场也记到同一个世界上（清掉上一个世界的 tile 缓存）。
 *       <b>它不改变任何气候输出</b>（只清缓存 + 记种子），所以这一步不需要单独验收。</li>
 * </ol>
 *
 * <p>为什么预热是必须的而不是优化：单行首解 1~7 s（P466/P467 实测），而查询发生在
 * 区块生成线程里 ⇒ 懒解 = 世界生成期间 64 次秒级卡顿。预热线程是 daemon + 最低优先级，
 * 解行时 {@code solveRow} 自己加锁，其它线程查询只会**等正在解的那一行**，不会读到半成品。
 */
public final class OceanWiring {

    private OceanWiring() {}

    private static volatile int startedFor = Integer.MIN_VALUE;
    private static volatile Thread worker;
    private static volatile boolean done;

    /** 世界级接线入口。可重复调用（同一个种子只启一次预热线程）。 */
    public static synchronized void onWorld(int worldSeedInt) {
        OceanField.install(worldSeedInt);
        // ★ §7503：水系场也跟同一个世界走。这里【只清缓存 + 记种子】，不产生气候效应。
        com.EyeOfHarmonyBuffer.sim.hydro.WaterField.install(worldSeedInt);
        if (!OceanField.ENABLED) return;
        if (startedFor == worldSeedInt && worker != null && worker.isAlive()) return;
        if (worker != null && worker.isAlive()) worker.interrupt();
        startedFor = worldSeedInt;
        done = false;
        final int s = worldSeedInt;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                try { OceanField.warmAll(s); } finally { done = true; }
            }
        }, "Talos-SST-Warmup");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        worker = t;
        t.start();
    }

    /** 预热是否已完成（诊断/探针用）。 */
    public static boolean isWarm() { return done || OceanField.warmRowsDone >= OceanField.ROWS; }

    /** 已预热行数（诊断/探针用）。 */
    public static int warmRows() { return OceanField.warmRowsDone; }

    /** 卸线（回到 SST' == 0 的旧口径；A/B 对比用）。 */
    public static synchronized void off() {
        if (worker != null) worker.interrupt();
        worker = null; startedFor = Integer.MIN_VALUE; done = false;
        OceanField.uninstall();
    }
}
