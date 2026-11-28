package com.EyeOfHarmonyBuffer.sim.ocean.staticcore;

import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 区域解缓存 + 单线程预热队列（无限世界架构 §8）。
 *
 * <p>语义：
 * <ul>
 *   <li>{@link #get} —— 命中直接返回；未命中【同步解】并缓存（阻塞首解，不读半成品）；</li>
 *   <li>{@link #requestWarm} —— 只入队；后台 daemon 线程按 FIFO 解，重复请求去重；</li>
 *   <li>同键只解一次：{@link #get} 与预热线程共用同一把锁。</li>
 * </ul>
 *
 * <p>⚠ 现在（阶段 1）只有骨架：{@link #setLoader} 由阶段 2 的 patch solver 提供。
 * 默认没有 loader 时 {@link #get} 抛 IllegalStateException，不会静默返回假数据。
 */
public final class OceanRegionCache {

    /** 区域解算器：阶段 2 接 patch solver。 */
    public interface Loader {
        OceanRegionSolution load(OceanRegionKey key);
    }

    private final ConcurrentHashMap<OceanRegionKey, OceanRegionSolution> cache =
        new ConcurrentHashMap<OceanRegionKey, OceanRegionSolution>();
    private final Set<OceanRegionKey> pending = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<OceanRegionKey> queue = new LinkedBlockingQueue<OceanRegionKey>();

    private volatile Loader loader;
    private volatile Thread worker;

    public synchronized void setLoader(Loader l) {
        if (loader != null && l != null) throw new IllegalStateException("loader already set");
        loader = l;
    }

    public boolean isCached(OceanRegionKey k) { return cache.containsKey(k); }

    public int size() { return cache.size(); }

    public int warmQueueDepth() { return queue.size(); }

    /** 同步取解：命中即返回，未命中阻塞解一次。 */
    public OceanRegionSolution get(OceanRegionKey k) {
        OceanRegionSolution s = cache.get(k);
        if (s != null) return s;
        synchronized (this) {
            s = cache.get(k);
            if (s != null) return s;
            Loader l = loader;
            if (l == null) throw new IllegalStateException("OceanRegionCache has no loader");
            s = l.load(k);
            cache.put(k, s);
            pending.remove(k);
            return s;
        }
    }

    /** 入队预热：已缓存/已排队则忽略。 */
    public void requestWarm(OceanRegionKey k) {
        if (cache.containsKey(k)) return;
        if (!pending.add(k)) return;
        queue.offer(k);
        ensureWorker();
    }

    private synchronized void ensureWorker() {
        if (worker != null && worker.isAlive()) return;
        Thread t = new Thread(new Runnable() {
            @Override public void run() {
                while (true) {
                    OceanRegionKey k;
                    try {
                        k = queue.take();
                    } catch (InterruptedException e) {
                        return;
                    }
                    try {
                        if (!cache.containsKey(k)) get(k);
                    } catch (RuntimeException e) {
                        pending.remove(k);
                    }
                }
            }
        }, "Talos-OceanRegion-Warm");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        worker = t;
        t.start();
    }

    /** 换世界/换配置：清空（worker 保留，队列清空）。 */
    public synchronized void clear() {
        cache.clear();
        pending.clear();
        queue.clear();
    }
}
