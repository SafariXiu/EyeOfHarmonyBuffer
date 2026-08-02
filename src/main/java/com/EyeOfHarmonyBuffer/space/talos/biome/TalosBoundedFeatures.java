package com.EyeOfHarmonyBuffer.space.talos.biome;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.api.TalosRiverSystem;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.Random;

/**
 * 群系装饰特征集。
 *
 * 读取只限当前区块；写入允许跨到已加载的相邻区块（±1 区块）。
 * populate 阶段 3×3 邻域已经生成，因此跨区块写入不会触发新区块生成，
 * 树冠等大特征可以和原版一样跨过区块边界，不再被裁掉。
 */
public final class TalosBoundedFeatures {

    private TalosBoundedFeatures() {}

    /** 公共基类：封装局部坐标读写与跨界安全写入。 */
    public abstract static class Base implements TalosBoundedFeature {

        protected World world;
        protected Random rand;
        protected Chunk chunk;
        protected int chunkX;
        protected int chunkZ;

        @Override
        public final boolean generate(World world, Random random, Chunk chunk,
                                      int localX, int localZ) {
            if (localX < 0 || localX >= 16 || localZ < 0 || localZ >= 16) {
                return false;
            }
            this.world = world;
            this.rand = random;
            this.chunk = chunk;
            this.chunkX = chunk.xPosition;
            this.chunkZ = chunk.zPosition;
            return generateAt(localX, localZ);
        }

        protected abstract boolean generateAt(int localX, int localZ);

        /**
         * 当前区块内的地表方块 y；越界返回 -1。
         *
         * 1.7.10 的 Chunk.getHeightValue 通常指向"地表上方一格"（空气），
         * 只有地表恰好压在 section 顶（63/79/95…）时才碰巧指到草皮，
         * 因此这里照原版 WorldGenTallGrass 的做法向下扫描，
         * 跳过空气与树叶，返回第一个实心方块的高度。
         */
        protected final int height(int lx, int lz) {
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
                return -1;
            }
            return surfaceYWorld(this.chunkX * 16 + lx, this.chunkZ * 16 + lz);
        }

        /**
         * 世界坐标列的真实地表 y（向下扫描，跨区块需已加载且未填充）；
         * 无法安全读取时返回 -1。
         */
        protected final int surfaceYWorld(int worldX, int worldZ) {
            int cx = worldX >> 4;
            int cz = worldZ >> 4;
            int dx = cx - this.chunkX;
            int dz = cz - this.chunkZ;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
                return -1;
            }
            if (!this.world.getChunkProvider().chunkExists(cx, cz)) {
                return -1;
            }
            Chunk target = this.world.getChunkFromChunkCoords(cx, cz);
            // 注意：装饰期间当前区块本身已被服务端标记为 isTerrainPopulated=true
            // （ChunkProviderServer.populate 先置位再调用装饰器），所以该限制只对邻区块生效。
            if ((cx != this.chunkX || cz != this.chunkZ)
                && target.isTerrainPopulated) {
                return -1;
            }
            int lx = worldX & 15;
            int lz = worldZ & 15;
            int y = target.getHeightValue(lx, lz);
            if (y > 255) {
                y = 255;
            }
            while (y > 0) {
                Block b = target.getBlock(lx, y, lz);
                if (b != Blocks.air
                    && !b.isLeaves(this.world, worldX, y, worldZ)) {
                    break;
                }
                y--;
            }
            return y;
        }

        /** 读取当前区块内方块；越界按空气处理。 */
        protected final Block getBlock(int lx, int y, int lz) {
            if (lx < 0 || lx >= 16 || lz < 0 || lz >= 16) {
                return Blocks.air;
            }
            if (y <= 0 || y >= 256) {
                return Blocks.air;
            }
            return this.chunk.getBlock(lx, y, lz);
        }

        /**
         * 写方块：允许跨到相邻一格（±1 区块），与原版装饰一致；
         * 写入前检查目标区块已加载，绝不触发区块生成（卡顿 / 栈溢出的根因）。
         *
         * 性能关键：1.7.10 在"等于/高于高度图"的位置放置不透明方块时，
         * Chunk.func_150807_a 会逐块触发 relightBlock（整列光照重扫），
         * 若目标 section 还是空的还会全区块 generateSkylightMap；
         * 一棵树几十次、一个区块几百次就是卡顿来源。
         * 这里在放置前先把目标列高度图抬到新方块之上（高度图始终保持正确），
         * 让放置走轻量路径；透明方块（草/花/枯灌木等）不抬高度图，保持原逻辑。
         */
        protected final void setBlock(int lx, int y, int lz, Block block, int meta) {
            if (y <= 0 || y >= 256) {
                return;
            }
            int worldX = this.chunkX * 16 + lx;
            int worldZ = this.chunkZ * 16 + lz;
            int cx = worldX >> 4;
            int cz = worldZ >> 4;
            int dx = cx - this.chunkX;
            int dz = cz - this.chunkZ;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
                return;
            }
            if (!this.world.getChunkProvider().chunkExists(cx, cz)) {
                return;
            }

            // 跨区块写入只允许进入"尚未填充"的邻区块：
            // 已填充的邻区块很可能已发给客户端，再写方块会触发
            // S22/S23 方块更新包，客户端逐块 relightBlock，造成卡顿。
            if (cx != this.chunkX || cz != this.chunkZ) {
                Chunk neighbor = this.world.getChunkFromChunkCoords(cx, cz);
                if (neighbor.isTerrainPopulated) {
                    return;
                }
            }

            if (block.getLightOpacity(this.world, worldX, y, worldZ) != 0) {
                Chunk target = (cx == this.chunkX && cz == this.chunkZ)
                    ? this.chunk
                    : this.world.getChunkFromChunkCoords(cx, cz);
                int idx = (worldZ & 15) << 4 | (worldX & 15);
                int[] hm = target.heightMap;
                if (y >= hm[idx]) {
                    hm[idx] = y + 1;
                }
            }

            // flags=2：只标记更新，不触发 notifyBlockChange。
            this.world.setBlock(worldX, y, worldZ, block, meta, 2);
        }

        /**
         * 该局部坐标上的写入是否会被接受：
         * 当前区块恒可写；跨区块要求目标区块已加载且尚未填充
         * （已填充 = 很可能已发给客户端，写入会产生方块更新包风暴）。
         */
        protected final boolean canWrite(int lx, int lz) {
            if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16) {
                return true;
            }
            int cx = (this.chunkX * 16 + lx) >> 4;
            int cz = (this.chunkZ * 16 + lz) >> 4;
            int dx = cx - this.chunkX;
            int dz = cz - this.chunkZ;
            if (dx < -1 || dx > 1 || dz < -1 || dz > 1) {
                return false;
            }
            if (!this.world.getChunkProvider().chunkExists(cx, cz)) {
                return false;
            }
            return !this.world.getChunkFromChunkCoords(cx, cz).isTerrainPopulated;
        }

        /**
         * 以 (lx,lz) 为中心、半径 radius 的方形范围是否全部可写。
         * 树等大特征生成前先检查，任何一格写不进去就整棵放弃，避免半棵树。
         */
        protected final boolean canWriteArea(int lx, int lz, int radius) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if (!canWrite(lx + dx, lz + dz)) {
                        return false;
                    }
                }
            }
            return true;
        }

        protected final boolean isWater(int lx, int y, int lz) {
            Block b = getBlock(lx, y, lz);
            return b == Blocks.water || b == Blocks.flowing_water;
        }

        /** 世界坐标处是否为水（调用方需保证该区块已加载）。 */
        protected final boolean isWaterWorld(int x, int y, int z) {
            Block b = this.world.getBlock(x, y, z);
            return b == Blocks.water || b == Blocks.flowing_water;
        }

        /** 列顶是否为给定方块之一。 */
        protected final boolean topIs(int lx, int lz, Block... allowed) {
            int y = height(lx, lz);
            if (y <= 0) {
                return false;
            }
            Block b = getBlock(lx, y, lz);
            for (Block a : allowed) {
                if (a == b) {
                    return true;
                }
            }
            return false;
        }

        /**
         * 常见真实地表方块：树干 / 高草 / 植物等“假地表”不算，
         * 防止结构（石头 / 巨石 / 斑块）生成在树木顶部。
         */
        protected final boolean isGroundSurface(int worldX, int y, int worldZ) {
            Block b = this.world.getBlock(worldX, y, worldZ);
            return b == Blocks.grass || b == Blocks.dirt || b == Blocks.stone
                || b == Blocks.sand || b == Blocks.sandstone || b == Blocks.gravel
                || b == Blocks.snow || b == Blocks.packed_ice || b == Blocks.clay
                || b == Blocks.mycelium || b == Blocks.hardened_clay
                || b == Blocks.stained_hardened_clay;
        }
    }

    /** 高草（meta 1）或蕨（meta 2）：优先生成 2 格高的双株草，空间不足退化为单格。 */
    public static final class Grass extends Base {
        private final int meta;

        public Grass(int meta) {
            this.meta = meta;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            int y = height(lx, lz);
            if (y <= 0 || y >= 254) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, Blocks.grass, Blocks.dirt)) {
                return false;
            }
            if (getBlock(lx, y + 1, lz) == Blocks.air
                && getBlock(lx, y + 2, lz) == Blocks.air) {
                // 双株高草：底部 meta 2（草）/3（蕨），顶部 +8
                int plantMeta = (this.meta == 2) ? 3 : 2;
                setBlock(lx, y + 1, lz, Blocks.double_plant, plantMeta);
                setBlock(lx, y + 2, lz, Blocks.double_plant, plantMeta | 8);
            } else {
                setBlock(lx, y + 1, lz, Blocks.tallgrass, this.meta);
            }
            return true;
        }
    }

    /** 花：red_flower 随机品种，yellow_flower 固定。 */
    public static final class Flower extends Base {
        private final Block flower;

        public Flower(Block flower) {
            this.flower = flower;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            int y = height(lx, lz);
            if (y <= 0 || y >= 255) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, Blocks.grass, Blocks.dirt)) {
                return false;
            }
            int meta = (this.flower == Blocks.red_flower)
                ? this.rand.nextInt(9)
                : 0;
            setBlock(lx, y + 1, lz, this.flower, meta);
            return true;
        }
    }

    /** 枯灌木。 */
    public static final class DeadBush extends Base {

        @Override
        protected boolean generateAt(int lx, int lz) {
            int y = height(lx, lz);
            if (y <= 0 || y >= 255) {
                return false;
            }
            if (isWater(lx, y, lz)
                || !topIs(lx, lz, Blocks.grass, Blocks.dirt, Blocks.sand)) {
                return false;
            }
            setBlock(lx, y + 1, lz, Blocks.deadbush, 0);
            return true;
        }
    }

    /** 仙人掌：1~3 格，四周留空；边缘列直接放弃（邻格检查不能跨区块）。 */
    public static final class Cactus extends Base {

        @Override
        protected boolean generateAt(int lx, int lz) {
            if (lx <= 0 || lx >= 15 || lz <= 0 || lz >= 15) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 0 || y >= 254) {
                return false;
            }
            if (!topIs(lx, lz, Blocks.sand)) {
                return false;
            }
            if (getBlock(lx, y + 1, lz) != Blocks.air) {
                return false;
            }
            for (int d = -1; d <= 1; d += 2) {
                if (getBlock(lx + d, y + 1, lz) != Blocks.air
                    || getBlock(lx, y + 1, lz + d) != Blocks.air) {
                    return false;
                }
            }
            int h = 1 + this.rand.nextInt(3);
            for (int i = 0; i < h; i++) {
                setBlock(lx, y + 1 + i, lz, Blocks.cactus, 0);
            }
            return true;
        }
    }

    /** 甘蔗：2~3 格，要求邻格有水。 */
    public static final class Reed extends Base {

        @Override
        protected boolean generateAt(int lx, int lz) {
            int y = height(lx, lz);
            if (y <= 0 || y >= 253) {
                return false;
            }
            if (!topIs(lx, lz, Blocks.grass, Blocks.dirt, Blocks.sand)) {
                return false;
            }
            boolean nearWater = false;
            for (int d = -1; d <= 1 && !nearWater; d += 2) {
                if (lx + d >= 0 && lx + d < 16 && isWater(lx + d, y, lz)) {
                    nearWater = true;
                }
                if (lz + d >= 0 && lz + d < 16 && isWater(lx, y, lz + d)) {
                    nearWater = true;
                }
            }
            if (!nearWater) {
                return false;
            }
            int h = 2 + this.rand.nextInt(2);
            for (int i = 0; i < h; i++) {
                setBlock(lx, y + 1 + i, lz, Blocks.reeds, 0);
            }
            return true;
        }
    }

    /** 睡莲：放在当前区块内的水面上。 */
    public static final class Waterlily extends Base {

        @Override
        protected boolean generateAt(int lx, int lz) {
            int y = height(lx, lz);
            if (y <= 0 || y >= 255) {
                return false;
            }
            if (!isWater(lx, y, lz) || getBlock(lx, y + 1, lz) != Blocks.air) {
                return false;
            }
            setBlock(lx, y + 1, lz, Blocks.waterlily, 0);
            return true;
        }
    }

    /** 灌木：1 格树干 + 顶部 3×3 叶冠（叶冠可跨到已加载的相邻区块）。 */
    public static final class Shrub extends Base {

        @Override
        protected boolean generateAt(int lx, int lz) {
            if (!canWriteArea(lx, lz, 1)) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 0 || y >= 254) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, Blocks.grass, Blocks.dirt)) {
                return false;
            }
            setBlock(lx, y + 1, lz, Blocks.log, 0);
            for (int dz = -1; dz <= 1; dz++) {
                for (int dx = -1; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    // meta | 4 = 永不腐烂（原版树形保证叶子不离原木太远，我们的树不保证）
                    setBlock(lx + dx, y + 2, lz + dz, Blocks.leaves, 4);
                }
            }
            return true;
        }
    }

    /**
     * 通用树木：树干高度、歪斜、树冠形状/半径、树叶密度与方块
     * 全部来自群系的 TreeStyle（结构定义在群系类里，方便逐群系调参）。
     * 树叶自动带"永不腐烂"位；树冠可跨到已加载的相邻区块，写不进就整棵放弃。
     */
    public static final class Tree extends Base {
        private final TalosBiomeBase.TreeStyle style;

        public Tree(TalosBiomeBase.TreeStyle style) {
            this.style = style;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            boolean lean = this.style.leanChance > 0.0
                && this.style.shape != TalosBiomeBase.TreeShape.JUNGLE
                && this.rand.nextDouble() < this.style.leanChance;
            if (!canWriteArea(lx, lz, this.style.canopyRadius + (lean ? 1 : 0))) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 0 || y >= 250) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, this.style.groundBlocks)) {
                return false;
            }

            // 2×2 主干：4 列都必须是非水体且高度接近，
            // 否则其余 3 格主干会悬在河道 / 陡坡上方。
            if (this.style.shape == TalosBiomeBase.TreeShape.JUNGLE) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int dx = 0; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int cy = height(lx + dx, lz + dz);
                        if (cy <= 0
                            || isWaterWorld(
                                this.chunkX * 16 + lx + dx, cy,
                                this.chunkZ * 16 + lz + dz)
                            || Math.abs(cy - y) > 2) {
                            return false;
                        }
                    }
                }
            }

            int trunk = this.style.trunkMin
                + this.rand.nextInt(this.style.trunkMax - this.style.trunkMin + 1);
            int topY = y + trunk;

            int dirX = 0;
            int dirZ = 0;
            int leanAt = trunk;
            if (lean) {
                switch (this.rand.nextInt(4)) {
                    case 0: dirX = 1; break;
                    case 1: dirX = -1; break;
                    case 2: dirZ = 1; break;
                    default: dirZ = -1; break;
                }
                leanAt = 1 + this.rand.nextInt(Math.max(1, trunk - 1));
                // 歪向河道 / 水体时改为直生：
                // 否则上半截树干会横移到水面上方，形成悬空“假地表”。
                int cy = height(lx + dirX, lz + dirZ);
                if (cy <= 0 || isWaterWorld(
                        this.chunkX * 16 + lx + dirX, cy,
                        this.chunkZ * 16 + lz + dirZ)) {
                    lean = false;
                    dirX = 0;
                    dirZ = 0;
                }
            }

            // 主干（可选中途横向错开 1 格，形成原版那种歪树）
            if (this.style.shape == TalosBiomeBase.TreeShape.JUNGLE) {
                for (int i = 0; i < trunk; i++) {
                    int yy = y + 1 + i;
                    setBlock(lx, yy, lz, this.style.woodBlock, this.style.woodMeta);
                    setBlock(lx + 1, yy, lz, this.style.woodBlock, this.style.woodMeta);
                    setBlock(lx, yy, lz + 1, this.style.woodBlock, this.style.woodMeta);
                    setBlock(lx + 1, yy, lz + 1, this.style.woodBlock, this.style.woodMeta);
                }
            } else {
                for (int i = 1; i <= trunk; i++) {
                    int ox = (i > leanAt) ? dirX : 0;
                    int oz = (i > leanAt) ? dirZ : 0;
                    setBlock(lx + ox, y + i, lz + oz,
                        this.style.woodBlock, this.style.woodMeta);
                }
            }

            int topX = lx + (lean ? dirX : 0);
            int topZ = lz + (lean ? dirZ : 0);

            switch (this.style.shape) {
                case CONE: {
                    for (int dy = -3; dy <= 0; dy++) {
                        int r = (dy == -3 || dy == -2)
                            ? this.style.canopyRadius : 1;
                        leafLayer(topX, topY + dy, topZ, r, true, r * r + 1, false);
                    }
                    placeLeaf(topX, topY + 1, topZ);
                    break;
                }
                case FLAT: {
                    for (int dy = 0; dy < 3; dy++) {
                        int r = (dy < 2)
                            ? this.style.canopyRadius : this.style.canopyRadius - 1;
                        if (r <= 0) {
                            break;
                        }
                        leafLayer(topX, topY + dy, topZ, r, dy == 0, r * r, false);
                    }
                    break;
                }
                case JUNGLE: {
                    for (int dy = -2; dy <= 2; dy++) {
                        int r = (dy == -2 || dy == 2)
                            ? this.style.canopyRadius - 1 : this.style.canopyRadius;
                        leafLayer(topX, topY + dy, topZ, r, false, r * r + 2, true);
                    }
                    // 顶部封盖：2×2 主干正上方补树叶，避免原木顶面裸露
                    for (int dz = 0; dz <= 1; dz++) {
                        for (int dx = 0; dx <= 1; dx++) {
                            placeLeaf(topX + dx, topY + 1, topZ + dz);
                            placeLeaf(topX + dx, topY + 2, topZ + dz);
                        }
                    }
                    break;
                }
                case ROUND:
                default: {
                    for (int dy = -1; dy <= 1; dy++) {
                        int r = (dy == 1) ? 1 : this.style.canopyRadius;
                        leafLayer(topX, topY + dy, topZ, r, true, r * r + 1, false);
                    }
                    placeLeaf(topX, topY + 2, topZ);
                    break;
                }
            }
            return true;
        }

        /** 画一层圆形树冠；skipTrunkArea 用于跳过 2×2 主干占位。 */
        private void leafLayer(int cx, int y, int cz, int r,
                               boolean skipCenter, int radiusSq,
                               boolean skipTrunkArea) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dz * dz > radiusSq) {
                        continue;
                    }
                    if (skipCenter && dx == 0 && dz == 0) {
                        continue;
                    }
                    if (skipTrunkArea && dx >= 0 && dx <= 1 && dz >= 0 && dz <= 1) {
                        continue;
                    }
                    placeLeaf(cx + dx, y, cz + dz);
                }
            }
        }

        private void placeLeaf(int x, int y, int z) {
            // 密度判定：越低树冠越稀疏
            if (this.rand.nextDouble() > this.style.leafDensity) {
                return;
            }
            // |4 = 永不腐烂（防止冠缘离原木太远而掉叶引发卡顿/秃冠）
            setBlock(x, y, z, this.style.leafBlock, this.style.leafMeta | 4);
        }
    }

    /**
     * 蓝图树（方案 2）：按 TreeBlueprint 生成。
     *
     * 结构：歪斜/粗壮树干 → 树枝（木块 + 枝梢叶）→ 多层树冠（可带半径扰动）
     *       → 主干顶部封盖 → 可选藤蔓。
     * 所有写入仍走 Base 的安全路径：跨区块只写未填充邻块、不透明方块先抬高度图。
     */
    public static final class BlueprintTree extends Base {

        private static final int[][] DIRS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        private final TalosBiomeBase.TreeBlueprint bp;

        public BlueprintTree(TalosBiomeBase.TreeBlueprint bp) {
            this.bp = bp;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            int maxR = maxCanopyRadius() + Math.max(0, this.bp.branches.length);
            if (!canWriteArea(lx, lz, maxR)) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 1 || y >= 250) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, this.bp.groundBlocks)) {
                return false;
            }

            int trunk = this.bp.trunkMin
                + this.rand.nextInt(this.bp.trunkMax - this.bp.trunkMin + 1);
            int topY = y + trunk;

            boolean lean = !this.bp.wideTrunk
                && this.bp.leanChance > 0.0
                && this.rand.nextDouble() < this.bp.leanChance;
            int dirX = 0;
            int dirZ = 0;
            int leanAt = trunk;
            if (lean) {
                switch (this.rand.nextInt(4)) {
                    case 0: dirX = 1; break;
                    case 1: dirX = -1; break;
                    case 2: dirZ = 1; break;
                    default: dirZ = -1; break;
                }
                leanAt = 1 + this.rand.nextInt(Math.max(1, trunk - 1));
                // 歪向河道 / 水体时改为直生，避免树干悬在水面上方
                int cy = height(lx + dirX, lz + dirZ);
                if (cy <= 0 || isWaterWorld(
                        this.chunkX * 16 + lx + dirX, cy,
                        this.chunkZ * 16 + lz + dirZ)) {
                    lean = false;
                    dirX = 0;
                    dirZ = 0;
                }
            }

            if (this.bp.wideTrunk) {
                // 2×2 主干：4 列都必须是非水体且高度接近
                for (int dz = 0; dz <= 1; dz++) {
                    for (int dx = 0; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) {
                            continue;
                        }
                        int cy = height(lx + dx, lz + dz);
                        if (cy <= 0
                            || isWaterWorld(
                                this.chunkX * 16 + lx + dx, cy,
                                this.chunkZ * 16 + lz + dz)
                            || Math.abs(cy - y) > 2) {
                            return false;
                        }
                    }
                }
                for (int i = 1; i <= trunk; i++) {
                    int yy = y + i;
                    setBlock(lx, yy, lz, this.bp.woodBlock, this.bp.woodMeta);
                    setBlock(lx + 1, yy, lz, this.bp.woodBlock, this.bp.woodMeta);
                    setBlock(lx, yy, lz + 1, this.bp.woodBlock, this.bp.woodMeta);
                    setBlock(lx + 1, yy, lz + 1, this.bp.woodBlock, this.bp.woodMeta);
                }
            } else {
                for (int i = 1; i <= trunk; i++) {
                    int ox = (i > leanAt) ? dirX : 0;
                    int oz = (i > leanAt) ? dirZ : 0;
                    setBlock(lx + ox, y + i, lz + oz,
                        this.bp.woodBlock, this.bp.woodMeta);
                }
            }

            int topX = lx + (lean ? dirX : 0);
            int topZ = lz + (lean ? dirZ : 0);

            // 树枝：从树干上部斜伸出去，枝梢带两片叶子
            TalosBiomeBase.TreeBlueprint.BranchSpec br = this.bp.branches;
            if (br.chance > 0.0 && br.count > 0
                && this.rand.nextDouble() < br.chance) {
                for (int b = 0; b < br.count; b++) {
                    int below = br.startBelow
                        + this.rand.nextInt(Math.max(1, trunk - br.startBelow + 1));
                    int baseY = topY - below;
                    if (baseY <= y) {
                        continue;
                    }
                    int[] d = DIRS[this.rand.nextInt(DIRS.length)];
                    // 沿对角线逐步抬升：每一格高度都放木块，
                    // 保证枝干连续、不出现悬空分段（rise > 1 时尤其重要）。
                    int steps = br.length * br.rise;
                    for (int s = 1; s <= steps; s++) {
                        int step = (s + br.rise - 1) / br.rise;
                        setBlock(topX + d[0] * step, baseY + s,
                            topZ + d[1] * step,
                            this.bp.woodBlock, this.bp.woodMeta);
                    }
                    int tipX = topX + d[0] * br.length;
                    int tipZ = topZ + d[1] * br.length;
                    int tipY = baseY + steps;
                    placeLeaf(tipX, tipY, tipZ);
                    placeLeaf(tipX, tipY + 1, tipZ);
                }
            }

            // 多层树冠：每层按半径铺圆盘，jitter 扰动轮廓
            int lowestY = Integer.MAX_VALUE;
            int lowestR = 0;
            for (TalosBiomeBase.TreeBlueprint.CanopyLayer layer : this.bp.layers) {
                int cy = topY + layer.yOffset;
                int r = layer.radius;
                for (int dz = -r; dz <= r; dz++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (dx == 0 && dz == 0 && layer.skipCenter) {
                            continue;
                        }
                        double dist = Math.sqrt(dx * dx + dz * dz);
                        if (this.bp.jitter > 0.0) {
                            dist += (this.rand.nextDouble() - 0.5) * 2.0 * this.bp.jitter;
                        }
                        if (dist > r) {
                            continue;
                        }
                        placeLeaf(topX + dx, cy, topZ + dz);
                    }
                }
                if (cy < lowestY) {
                    lowestY = cy;
                    lowestR = r;
                }
            }

            // 顶部封盖：主干正上方补树叶，避免原木顶面裸露
            if (this.bp.wideTrunk) {
                for (int dz = 0; dz <= 1; dz++) {
                    for (int dx = 0; dx <= 1; dx++) {
                        placeLeaf(topX + dx, topY + 1, topZ + dz);
                        placeLeaf(topX + dx, topY + 2, topZ + dz);
                    }
                }
            } else {
                placeLeaf(topX, topY + 1, topZ);
                placeLeaf(topX, topY + 2, topZ);
            }

            // 藤蔓：从最低树冠层下缘垂下 1~3 格
            if (this.bp.vines && lowestY < topY) {
                int meta = 1 << this.rand.nextInt(4);
                for (int dz = -lowestR; dz <= lowestR; dz++) {
                    for (int dx = -lowestR; dx <= lowestR; dx++) {
                        if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1) {
                            continue; // 避开主干
                        }
                        if (dx * dx + dz * dz > lowestR * lowestR + 1) {
                            continue;
                        }
                        if (this.rand.nextDouble() >= this.bp.vineChance) {
                            continue;
                        }
                        int vx = topX + dx;
                        int vz = topZ + dz;
                        int vy = lowestY - 1;
                        if (!isAirWorld(vx, vy, vz)) {
                            continue;
                        }
                        int len = 1 + this.rand.nextInt(3);
                        for (int i = 0; i < len; i++) {
                            if (!isAirWorld(vx, vy - i, vz)) {
                                break;
                            }
                            setBlock(vx - this.chunkX * 16, vy - i,
                                vz - this.chunkZ * 16, Blocks.vine, meta);
                        }
                    }
                }
            }
            return true;
        }

        private void placeLeaf(int x, int y, int z) {
            if (this.rand.nextDouble() > this.bp.leafDensity) {
                return;
            }
            setBlock(x, y, z, this.bp.leafBlock, this.bp.leafMeta | 4);
        }

        private int maxCanopyRadius() {
            int max = 0;
            for (TalosBiomeBase.TreeBlueprint.CanopyLayer layer : this.bp.layers) {
                if (layer.radius > max) {
                    max = layer.radius;
                }
            }
            return max;
        }

        private boolean isAirWorld(int x, int y, int z) {
            return this.world.getBlock(x, y, z) == Blocks.air;
        }
    }

    /**
     * 小水洼：约 10×10 的不规则水体 + 外围随机沙岸。
     * 水底挖到中心地表下 2 格并铺沙，水面比四周低 1 格；
     * 与区块边界冲突（邻区块不可写）、靠近河流/河岸平滑区、
     * 或落在坡度过大的地形上时整片放弃。
     */
    public static final class Pond extends Base {
        private final TalosBiomeBase.PondConfig config;

        public Pond(TalosBiomeBase.PondConfig config) {
            this.config = config;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            int r = this.config.radius;
            if (!canWriteArea(lx, lz, r + 1)) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 3 || y >= 253) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, this.config.groundBlocks)) {
                return false;
            }

            int centerX = this.chunkX * 16 + lx;
            int centerZ = this.chunkZ * 16 + lz;

            // 避开河流及河岸平滑区：范围内任何一点的河影响掩码 > 0.5 就放弃，
            // 否则水洼会把河岸平滑地形挖坏。
            int worldSeedInt = TalosRiverSystem.getWorldSeedInt(this.world);
            for (int dz = -r - 1; dz <= r + 1; dz += 3) {
                for (int dx = -r - 1; dx <= r + 1; dx += 3) {
                    double mask = TalosRiverSystem.getRiverMask(
                        centerX + dx, centerZ + dz, worldSeedInt);
                    if (mask > 0.5) {
                        return false;
                    }
                }
            }

            // 坡度过大也放弃（例如河岸/山脚的斜坡），避免挖出难看的坑。
            int hMin = Integer.MAX_VALUE;
            int hMax = Integer.MIN_VALUE;
            for (int j = -1; j <= 1; j++) {
                for (int i = -1; i <= 1; i++) {
                    int sy = surfaceYWorld(centerX + i * r, centerZ + j * r);
                    if (sy > 0) {
                        hMin = Math.min(hMin, sy);
                        hMax = Math.max(hMax, sy);
                    }
                }
            }
            if (hMin == Integer.MAX_VALUE || hMax - hMin > 3) {
                return false;
            }

            int bed = y - this.config.depth; // 水底 = 中心地表下 depth 格

            // 不规则形状：圆形 + 每格随机扰动，中心必留
            boolean[][] blob = new boolean[2 * r + 1][2 * r + 1];
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    double d = Math.sqrt(dx * dx + dz * dz)
                        + (this.rand.nextDouble() - 0.5) * 1.6;
                    blob[dx + r][dz + r] = d <= r - 0.3;
                }
            }
            blob[r][r] = true;

            // 挖坑 + 沙底 + 水
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (!blob[dx + r][dz + r]) {
                        continue;
                    }
                    int wx = centerX + dx;
                    int wz = centerZ + dz;
                    int sy = surfaceYWorld(wx, wz);
                    if (sy < bed - 1) {
                        continue;
                    }
                    if (isWaterWorld(wx, sy, wz)) {
                        continue; // 别把河面/湖面挖掉
                    }
                    for (int yy = bed + 1; yy <= sy; yy++) {
                        setBlock(wx - this.chunkX * 16, yy, wz - this.chunkZ * 16,
                            Blocks.air, 0);
                    }
                    setBlock(wx - this.chunkX * 16, bed, wz - this.chunkZ * 16,
                        Blocks.sand, 0);
                    setBlock(wx - this.chunkX * 16, bed + 1, wz - this.chunkZ * 16,
                        Blocks.water, 0);
                }
            }

            // 外围随机沙：紧邻水体的岸格，按 50% 概率把地表换成沙子
            for (int dz = -r - 1; dz <= r + 1; dz++) {
                for (int dx = -r - 1; dx <= r + 1; dx++) {
                    if (dx >= -r && dx <= r && dz >= -r && dz <= r
                        && blob[dx + r][dz + r]) {
                        continue;
                    }
                    boolean nearWater = false;
                    for (int nz = -1; nz <= 1 && !nearWater; nz++) {
                        for (int nx = -1; nx <= 1 && !nearWater; nx++) {
                            int px = dx + nx;
                            int pz = dz + nz;
                            if (px >= -r && px <= r && pz >= -r && pz <= r
                                && blob[px + r][pz + r]) {
                                nearWater = true;
                            }
                        }
                    }
                    if (!nearWater || this.rand.nextDouble() >= this.config.rimSandChance) {
                        continue;
                    }
                    int wx = centerX + dx;
                    int wz = centerZ + dz;
                    int sy = surfaceYWorld(wx, wz);
                    if (sy <= 0) {
                        continue;
                    }
                    if (isWaterWorld(wx, sy, wz)) {
                        continue;
                    }
                    setBlock(wx - this.chunkX * 16, sy, wz - this.chunkZ * 16,
                        Blocks.sand, 0);
                }
            }
            return true;
        }
    }

    /**
     * 大型不规则石头：最大约 5×5×5，2~3 个子块叠加成团块，
     * 中心高边缘矮，底座压在草地上。
     */
    public static final class Rock extends Base {
        private final TalosBiomeBase.RockConfig config;

        public Rock(TalosBiomeBase.RockConfig config) {
            this.config = config;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            int n = this.config.footprint;
            int half = n / 2;
            if (!canWriteArea(lx, lz, half)) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 1 || y >= 248) {
                return false;
            }
            if (isWater(lx, y, lz) || !topIs(lx, lz, this.config.groundBlocks)) {
                return false;
            }

            int centerX = this.chunkX * 16 + lx;
            int centerZ = this.chunkZ * 16 + lz;

            // 占地 footprint×footprint：2~3 个随机圆形子块叠加成不规则形状
            boolean[][] cell = new boolean[n][n];
            int blobs = 2 + this.rand.nextInt(2);
            for (int b = 0; b < blobs; b++) {
                int bx = this.rand.nextInt(n);
                int bz = this.rand.nextInt(n);
                int br = 1 + this.rand.nextInt(2);
                for (int dz = -br; dz <= br; dz++) {
                    for (int dx = -br; dx <= br; dx++) {
                        if (dx * dx + dz * dz > br * br + 1) {
                            continue;
                        }
                        int px = bx + dx;
                        int pz = bz + dz;
                        if (px >= 0 && px < n && pz >= 0 && pz < n) {
                            cell[px][pz] = true;
                        }
                    }
                }
            }

            for (int dz = 0; dz < n; dz++) {
                for (int dx = 0; dx < n; dx++) {
                    if (!cell[dx][dz]) {
                        continue;
                    }
                    int dist = Math.max(Math.abs(dx - half), Math.abs(dz - half));
                    int h = this.config.minHeight
                        + this.rand.nextInt(Math.max(1,
                            this.config.maxHeight - this.config.minHeight + 1 - dist));
                    int wx = centerX + dx - half;
                    int wz = centerZ + dz - half;
                    int sy = surfaceYWorld(wx, wz);
                    if (sy <= 0 || sy + h - 1 >= 256) {
                        continue;
                    }
                    if (isWaterWorld(wx, sy, wz)) {
                        continue;
                    }
                    // 该列“地表”必须是真实地表方块，
                    // 否则石头会落在树干 / 高草等假地表上
                    if (!isGroundSurface(wx, sy, wz)) {
                        continue;
                    }
                    for (int i = 0; i < h; i++) {
                        setBlock(wx - this.chunkX * 16, sy + i, wz - this.chunkZ * 16,
                            this.config.block, 0);
                    }
                }
            }
            return true;
        }
    }

    /** 地表斑块：把草地上随机几格替换成泥土 / 石头 / 沙等（读取限当前区块）。 */
    public static final class GroundPatch extends Base {
        private final Block block;
        private final int meta;
        private final int radius;
        private final double fillChance;

        public GroundPatch(Block block, int meta, int radius, double fillChance) {
            this.block = block;
            this.meta = meta;
            this.radius = radius;
            this.fillChance = fillChance;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            // 河道 / 洪泛平原带内不放地表斑块：
            // 河面附近容易扫描到歪斜树干等“假地表”，导致斑块悬空。
            if (TalosRiverSystem.getRiverMask(
                    this.chunkX * 16 + lx,
                    this.chunkZ * 16 + lz,
                    TalosRiverSystem.getWorldSeedInt(this.world)) > 0.7) {
                return false;
            }
            boolean any = false;
            for (int dz = -this.radius; dz <= this.radius; dz++) {
                for (int dx = -this.radius; dx <= this.radius; dx++) {
                    if (this.rand.nextDouble() > this.fillChance) {
                        continue;
                    }
                    if (dx * dx + dz * dz > this.radius * this.radius) {
                        continue;
                    }
                    int px = lx + dx;
                    int pz = lz + dz;
                    int py = height(px, pz);
                    if (py <= 0) {
                        continue;
                    }
                    int wx = this.chunkX * 16 + px;
                    int wz = this.chunkZ * 16 + pz;
                    // 必须用世界坐标查水：本地 isWater 对区块外坐标一律返回空气，
                    // 会让跨到河道里的斑块漏判，把方块放在水面上。
                    if (isWaterWorld(wx, py, wz)) {
                        continue;
                    }
                    // 只替换真实地表方块，防止落在树干 / 高草 / 植物等假地表上
                    if (!isGroundSurface(wx, py, wz)) {
                        continue;
                    }
                    setBlock(px, py, pz, this.block, this.meta);
                    any = true;
                }
            }
            return any;
        }
    }

    /** 巨石：2×2 底座 + 偶尔补块（邻区块可写时完整放置，否则放弃）。 */
    public static final class Boulder extends Base {

        @Override
        protected boolean generateAt(int lx, int lz) {
            // 2×2 底座：邻区块可写时允许完整放置，否则放弃，避免半块巨石
            if (!canWriteArea(lx, lz, 1)) {
                return false;
            }
            int y = height(lx, lz);
            if (y <= 0) {
                return false;
            }
            int wx0 = this.chunkX * 16 + lx;
            int wz0 = this.chunkZ * 16 + lz;
            if (isWaterWorld(wx0, y, wz0) || !isGroundSurface(wx0, y, wz0)) {
                return false;
            }
            // 2×2 足迹的其余 3 列也必须是非水体且高度接近，
            // 否则巨石会悬在河道上方。
            for (int dz = 0; dz <= 1; dz++) {
                for (int dx = 0; dx <= 1; dx++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    int wx = this.chunkX * 16 + lx + dx;
                    int wz = this.chunkZ * 16 + lz + dz;
                    int cy = height(lx + dx, lz + dz);
                    if (cy <= 0
                        || isWaterWorld(wx, cy, wz)
                        || !isGroundSurface(wx, cy, wz)
                        || Math.abs(cy - y) > 2) {
                        return false;
                    }
                }
            }
            setBlock(lx, y, lz, Blocks.cobblestone, 0);
            setBlock(lx + 1, y, lz, Blocks.cobblestone, 0);
            setBlock(lx, y, lz + 1, Blocks.cobblestone, 0);
            setBlock(lx + 1, y, lz + 1, Blocks.cobblestone, 0);
            if (this.rand.nextInt(3) != 0) {
                setBlock(lx, y + 1, lz, Blocks.cobblestone, 0);
                setBlock(lx + 1, y + 1, lz + 1, Blocks.cobblestone, 0);
            }
            return true;
        }
    }

    /** 砾石斑：在表面铺一片砾石（半径可配）；边缘被裁掉一部分（风味）。 */
    public static final class Gravel extends Base {
        private final int radius;

        public Gravel(int radius) {
            this.radius = radius;
        }

        @Override
        protected boolean generateAt(int lx, int lz) {
            boolean any = false;
            for (int dz = -this.radius; dz <= this.radius; dz++) {
                for (int dx = -this.radius; dx <= this.radius; dx++) {
                    if (dx * dx + dz * dz > this.radius * this.radius) {
                        continue;
                    }
                    int px = lx + dx;
                    int pz = lz + dz;
                    int py = height(px, pz);
                    if (py <= 0) {
                        continue;
                    }
                    if (isWater(px, py, pz)) {
                        continue;
                    }
                    setBlock(px, py, pz, Blocks.gravel, 0);
                    any = true;
                }
            }
            return any;
        }
    }
}
