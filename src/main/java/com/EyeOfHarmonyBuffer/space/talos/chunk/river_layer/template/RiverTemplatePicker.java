package com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.template;

import com.EyeOfHarmonyBuffer.space.talos.chunk.river_layer.integration.RiverRegistry;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class RiverTemplatePicker {

    private RiverTemplatePicker() {}

    /** 模板 ID 排序快照：模板在 preInit 一次性加载且之后不可变，只排一次。 */
    private static volatile List<String> SORTED_TEMPLATE_IDS;

    /** (worldSeedInt, superId) -> templateId 缓存；空串表示「无模板」。 */
    private static final Long2ObjectOpenHashMap<String> TEMPLATE_CACHE =
        new Long2ObjectOpenHashMap<String>();

    private static final String NO_TEMPLATE = "";

    private static long key(int worldSeedInt, int superId) {
        return (((long) worldSeedInt) << 32) ^ (superId & 0xffffffffL);
    }

    /**
     * 选择某个超大陆使用的河流模板。
     *
     * 结果与旧实现完全一致（哈希 + 排序后取模），只是：
     *   - 模板列表只排序一次（旧实现每次调用都重新排序）；
     *   - (seed, superId) 的结果缓存，避免重复哈希。
     */
    public static String pickTemplateIdForSupercontinent(int worldSeedInt, int superId) {
        long k = key(worldSeedInt, superId);
        String cached = TEMPLATE_CACHE.get(k);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        if (TEMPLATE_CACHE.size() > 4096) {
            TEMPLATE_CACHE.clear();
        }

        String id = compute(worldSeedInt, superId);
        TEMPLATE_CACHE.put(k, id != null ? id : NO_TEMPLATE);
        return id;
    }

    private static String compute(int worldSeedInt, int superId) {
        Collection<String> ids = RiverRegistry.getAllTemplateIds();
        if (ids.isEmpty()) {
            return null;
        }

        List<String> list = SORTED_TEMPLATE_IDS;
        if (list == null || list.size() != ids.size()) {
            // 模板集合在 preInit 后固定；若数量变化则重建快照
            list = new ArrayList<String>(ids);
            Collections.sort(list);
            SORTED_TEMPLATE_IDS = list;
        }

        int h = Objects.hash(worldSeedInt, superId);
        return list.get(Math.floorMod(h, list.size()));
    }
}
