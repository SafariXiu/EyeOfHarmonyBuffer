package com.EyeOfHarmonyBuffer.space.talos.chunk.mountain_layer.runtime;

/**
 * 山地后台预构建服务：单守护线程，从中心向外环形扫描 1024 tile，
 * 按距离提前发现并构建山带，消除"第一个区块才现算"的小卡顿。
 *
 * 线程安全前提：WorldgenAPI / TectonicWorld 的缓存已是并发容器，
 * 本线程与主线程的生成查询可以安全并发。
 */
public final class MountainPrebuildService implements Runnable {

    /** 预构建扫描半径（tile）：跟随玩家周围约 3km，快速增量补齐。 */
    private static final int SCAN_RADIUS_TILES = 3;

    /** 启动预热：给出生区块生成让路（秒）。 */
    private static final long WARMUP_MS = 2000L;

    /** 每环间隔：控制与主线程的采样竞争（毫秒）。 */
    private static final long SCAN_INTERVAL_MS = 100L;

    private final MountainWorldState state;
    private final Thread thread;

    private volatile boolean running = true;
    private volatile double centerX;
    private volatile double centerZ;

    public MountainPrebuildService(MountainWorldState state,
                                   double centerX, double centerZ) {
        this.state = state;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.thread = new Thread(this, "Talos-Mountain-Prebuild");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    public void stop() {
        running = false;
        thread.interrupt();
    }

    public void updateCenter(double x, double z) {
        centerX = x;
        centerZ = z;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(WARMUP_MS);
        } catch (InterruptedException e) {
            return;
        }
        while (running) {
            try {
                int tileX = Math.floorDiv(
                    (int) Math.floor(centerX), MountainWorldState.TILE_BLOCKS
                );
                int tileZ = Math.floorDiv(
                    (int) Math.floor(centerZ), MountainWorldState.TILE_BLOCKS
                );
                scanAround(tileX, tileZ, SCAN_RADIUS_TILES);
                Thread.sleep(SCAN_INTERVAL_MS);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void scanAround(int centerTileX, int centerTileZ, int radius) {
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                state.scanTile(centerTileX + dx, centerTileZ + dz);
            }
        }
    }
}
