package com.EyeOfHarmonyBuffer.common.misc;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.event.world.WorldEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class OrundumLinkNetworkData extends WorldSavedData {

    private static final String DATA_NAME = "EOHB_OrundumLinkNetwork";
    /** 新格式：节点 / 邻接表 / 上游关系。 */
    private static final String LINK_NBT_NODES_TAG = "EOHB_OrundumLink_Nodes";
    private static final String LINK_NBT_ADJACENCY_TAG = "EOHB_OrundumLink_Adjacency";
    private static final String LINK_NBT_UPSTREAM_TAG = "EOHB_OrundumLink_Upstream";

    /** 按维度缓存各维度实例（WorldSavedData 本身按维度存储，静态单例在多维度下会互相覆盖）。 */
    private static final Map<Integer, OrundumLinkNetworkData> INSTANCES = new ConcurrentHashMap<>();

    /**
     * 所有节点：
     * nodeId -> LinkNodeEntry
     */
    private final HashMap<UUID, LinkNodeEntry> nodes = new HashMap<>(256, 0.9f);

    /**
     * 链路结构：
     * parentId -> childrenIds
     */
    private final HashMap<UUID, List<UUID>> adjacency = new HashMap<>(256, 0.9f);

    /**
     * 单上游：
     * childId -> parentId
     */
    private final HashMap<UUID, UUID> upstreamOf = new HashMap<>(256, 0.9f);

    /**
     * 是否需要重新计算网络激活状态
     */
    private boolean networkDirty = true;

    public OrundumLinkNetworkData() {
        super(DATA_NAME);
    }

    public OrundumLinkNetworkData(String name) {
        super(name);
    }

    public static OrundumLinkNetworkData get(World world) {
        if (world == null) {
            return null;
        }

        if (world.isRemote) {
            return INSTANCES.get(world.provider.dimensionId);
        }

        MapStorage storage = world.mapStorage;
        if (storage == null) {
            return null;
        }

        OrundumLinkNetworkData data =
            (OrundumLinkNetworkData) storage.loadData(OrundumLinkNetworkData.class, DATA_NAME);

        if (data == null) {
            data = new OrundumLinkNetworkData();
            storage.setData(DATA_NAME, data);
            data.markDirty();
            System.out.println("[EOHB] Created new OrundumLink network data.");
        }

        INSTANCES.put(world.provider.dimensionId, data);
        return data;
    }

    private static void loadInstance(World world) {
        MapStorage storage = world.mapStorage;
        OrundumLinkNetworkData data =
            (OrundumLinkNetworkData) storage.loadData(OrundumLinkNetworkData.class, DATA_NAME);
        if (data == null) {
            data = new OrundumLinkNetworkData();
            storage.setData(DATA_NAME, data);
        }
        INSTANCES.put(world.provider.dimensionId, data);
    }

    public void tick(World world) {
        if (!networkDirty) return;
        networkDirty = false;
        recomputeNetworkActive();
    }

    /**
     * Tile 在加载（或首次成型）时调用，用于向网络注册 / 更新自身信息。
     * 如果该 nodeId 不存在则创建新节点。
     */
    public void registerOrUpdateNode(UUID nodeId,
                                     LinkNodeEntry.NodeType type,
                                     UUID teamId,
                                     int dimId,
                                     int x, int y, int z) {

        if (nodeId == null) return;

        LinkNodeEntry entry = nodes.get(nodeId);
        if (entry == null) {
            entry = new LinkNodeEntry();
            entry.nodeId = nodeId;
            entry.type = type;
            entry.teamId = teamId;
            entry.dimId = dimId;
            entry.x = x;
            entry.y = y;
            entry.z = z;
            entry.physicalOnline = false;
            entry.networkActive = false;
            nodes.put(nodeId, entry);
        } else {
            entry.type = type;
            entry.teamId = teamId;
            entry.dimId = dimId;
            entry.x = x;
            entry.y = y;
            entry.z = z;
        }

        markDirty();
    }

    /**
     * Tile 被永久销毁时调用（不是区块卸载），彻底移除节点和相关链路。
     * 注意：这会把它的所有下游也一起断开（但不会删除子节点，只是让它们没有上游）。
     */
    public void removeNode(UUID nodeId) {
        if (nodeId == null) return;

        UUID parent = upstreamOf.remove(nodeId);
        if (parent != null) {
            List<UUID> children = adjacency.get(parent);
            if (children != null) {
                children.remove(nodeId);
                if (children.isEmpty()) {
                    adjacency.remove(parent);
                }
            }
        }

        List<UUID> children = adjacency.remove(nodeId);
        if (children != null) {
            for (UUID child : children) {
                UUID up = upstreamOf.get(child);
                if (nodeId.equals(up)) {
                    upstreamOf.remove(child);
                }
            }
        }

        nodes.remove(nodeId);

        networkDirty = true;
        markDirty();
    }

    /**
     * 更新节点的物理在线状态（多方块成型 + 允许工作）
     * Tile 在 onPostTick 或状态改变时调用
     */
    public void updatePhysicalOnline(UUID nodeId, boolean online) {
        if (nodeId == null) return;
        LinkNodeEntry e = nodes.get(nodeId);
        if (e == null) return;

        if (e.physicalOnline != online) {
            e.physicalOnline = online;
            networkDirty = true;
            markDirty();
        }
    }

    /**
     * 只在区块卸载时调用（可选）。
     * 简化方案：可以直接调用 updatePhysicalOnline(nodeId, false)。
     */
    public void setNodeOfflineOnUnload(UUID nodeId) {
        updatePhysicalOnline(nodeId, false);
    }

    /**
     * 供电站 / 中继器 / Core 查询自己是否网络激活
     */
    public boolean isNodeNetworkActive(UUID nodeId) {
        if (nodeId == null) return false;
        LinkNodeEntry e = nodes.get(nodeId);
        return e != null && e.networkActive;
    }

    /**
     * 获取节点当前的 entry（只读用途，注意不要直接改字段）
     */
    public LinkNodeEntry getNode(UUID nodeId) {
        return nodes.get(nodeId);
    }

    /**
     * 建立一条 parent -> child 的链路。
     *
     * - 每个 child 只能有一个 parent
     * - parent / child 必须 teamId 相同
     * - 两节点距离必须 <= maxDistance（方块距离）
     */
    public LinkCreationResult tryCreateLink(UUID parentId,
                                            UUID childId,
                                            int maxDistance) {
        if (parentId == null || childId == null) {
            return LinkCreationResult.INVALID_ID;
        }
        if (parentId.equals(childId)) {
            return LinkCreationResult.SAME_NODE;
        }

        LinkNodeEntry parent = nodes.get(parentId);
        LinkNodeEntry child = nodes.get(childId);

        if (parent == null || child == null) {
            return LinkCreationResult.NODE_NOT_FOUND;
        }

        if (parent.teamId == null || child.teamId == null || !parent.teamId.equals(child.teamId)) {
            return LinkCreationResult.DIFFERENT_TEAM;
        }

        if (parent.dimId != child.dimId) {
            return LinkCreationResult.DIFFERENT_DIM;
        }

        double dx = parent.x - child.x;
        double dz = parent.z - child.z;
        double distSq = dx * dx + dz * dz;
        if (distSq > (double) maxDistance * (double) maxDistance) {
            return LinkCreationResult.TOO_FAR;
        }

        if (upstreamOf.containsKey(childId)) {
            UUID oldParent = upstreamOf.get(childId);
            if (!parentId.equals(oldParent)) {
                return LinkCreationResult.ALREADY_HAS_PARENT;
            }
            return LinkCreationResult.OK;
        }

        upstreamOf.put(childId, parentId);

        List<UUID> list = adjacency.get(parentId);
        if (list == null) {
            list = new ArrayList<>();
            adjacency.put(parentId, list);
        }
        if (!list.contains(childId)) {
            list.add(childId);
        }

        networkDirty = true;
        markDirty();
        return LinkCreationResult.OK;
    }

    public enum LinkCreationResult {
        OK,
        INVALID_ID,
        NODE_NOT_FOUND,
        DIFFERENT_TEAM,
        DIFFERENT_DIM,
        TOO_FAR,
        SAME_NODE,
        ALREADY_HAS_PARENT
    }

    private void recomputeNetworkActive() {
        for (LinkNodeEntry e : nodes.values()) {
            e.networkActive = false;
        }

        System.out.println("[EOHB][Link] recomputeNetworkActive: nodes=" + nodes.size());

        Deque<UUID> queue = new ArrayDeque<>();

        for (LinkNodeEntry e : nodes.values()) {
            if (e.type == LinkNodeEntry.NodeType.PROTOCOL_CORE && e.physicalOnline) {
                System.out.println("[EOHB][Link] root core: " + e.nodeId +
                    " team=" + e.teamId +
                    " dim=" + e.dimId +
                    " pos=(" + e.x + "," + e.y + "," + e.z + ")");
                e.networkActive = true;
                queue.add(e.nodeId);
            }
        }

        while (!queue.isEmpty()) {
            UUID curId = queue.poll();
            List<UUID> children = adjacency.get(curId);
            if (children == null || children.isEmpty()) continue;

            for (UUID childId : children) {
                LinkNodeEntry child = nodes.get(childId);
                if (child == null) continue;
                if (!child.physicalOnline) continue;

                if (!child.networkActive) {
                    child.networkActive = true;
                    System.out.println("[EOHB][Link] active node: " + child.nodeId +
                        " type=" + child.type +
                        " dim=" + child.dimId +
                        " pos=(" + child.x + "," + child.y + "," + child.z + ")");
                    queue.add(childId);
                }
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        nodes.clear();
        adjacency.clear();
        upstreamOf.clear();

        if (!nbt.hasKey(LINK_NBT_NODES_TAG, 9)) {
            System.out.println("[EOHB] No OrundumLink NBT tag found, starting empty link network.");
            networkDirty = true;
            return;
        }

        NBTTagList nodeList = nbt.getTagList(LINK_NBT_NODES_TAG, 10);
        for (int i = 0; i < nodeList.tagCount(); i++) {
            NBTTagCompound c = nodeList.getCompoundTagAt(i);
            try {
                LinkNodeEntry e = new LinkNodeEntry();
                e.nodeId = UUID.fromString(c.getString("Id"));
                e.type = LinkNodeEntry.NodeType.valueOf(c.getString("Type"));
                String team = c.getString("Team");
                e.teamId = team.isEmpty() ? null : UUID.fromString(team);
                e.dimId = c.getInteger("Dim");
                e.x = c.getInteger("X");
                e.y = c.getInteger("Y");
                e.z = c.getInteger("Z");
                e.physicalOnline = c.getBoolean("PhysicalOnline");
                e.networkActive = c.getBoolean("NetworkActive");
                nodes.put(e.nodeId, e);
            } catch (IllegalArgumentException ignored) {
            }
        }

        if (nbt.hasKey(LINK_NBT_ADJACENCY_TAG, 9)) {
            NBTTagList adjList = nbt.getTagList(LINK_NBT_ADJACENCY_TAG, 10);
            for (int i = 0; i < adjList.tagCount(); i++) {
                NBTTagCompound c = adjList.getCompoundTagAt(i);
                try {
                    UUID parent = UUID.fromString(c.getString("Parent"));
                    List<UUID> children = new ArrayList<>();
                    NBTTagList childList = c.getTagList("Children", 8);
                    for (int j = 0; j < childList.tagCount(); j++) {
                        try {
                            children.add(UUID.fromString(childList.getStringTagAt(j)));
                        } catch (IllegalArgumentException ignored) {
                        }
                    }
                    adjacency.put(parent, children);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (nbt.hasKey(LINK_NBT_UPSTREAM_TAG, 9)) {
            NBTTagList upList = nbt.getTagList(LINK_NBT_UPSTREAM_TAG, 10);
            for (int i = 0; i < upList.tagCount(); i++) {
                NBTTagCompound c = upList.getCompoundTagAt(i);
                try {
                    UUID child = UUID.fromString(c.getString("Child"));
                    UUID parent = UUID.fromString(c.getString("Parent"));
                    upstreamOf.put(child, parent);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        System.out.println("[EOHB] Loaded OrundumLink network. Nodes=" + nodes.size());
        networkDirty = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList nodeList = new NBTTagList();
        for (LinkNodeEntry e : nodes.values()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Id", e.nodeId.toString());
            c.setString("Type", e.type == null ? LinkNodeEntry.NodeType.REPEATER.name() : e.type.name());
            c.setString("Team", e.teamId == null ? "" : e.teamId.toString());
            c.setInteger("Dim", e.dimId);
            c.setInteger("X", e.x);
            c.setInteger("Y", e.y);
            c.setInteger("Z", e.z);
            c.setBoolean("PhysicalOnline", e.physicalOnline);
            c.setBoolean("NetworkActive", e.networkActive);
            nodeList.appendTag(c);
        }
        nbt.setTag(LINK_NBT_NODES_TAG, nodeList);

        NBTTagList adjList = new NBTTagList();
        for (Map.Entry<UUID, List<UUID>> entry : adjacency.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Parent", entry.getKey().toString());
            NBTTagList childList = new NBTTagList();
            for (UUID child : entry.getValue()) {
                childList.appendTag(new NBTTagString(child.toString()));
            }
            c.setTag("Children", childList);
            adjList.appendTag(c);
        }
        nbt.setTag(LINK_NBT_ADJACENCY_TAG, adjList);

        NBTTagList upList = new NBTTagList();
        for (Map.Entry<UUID, UUID> entry : upstreamOf.entrySet()) {
            NBTTagCompound c = new NBTTagCompound();
            c.setString("Child", entry.getKey().toString());
            c.setString("Parent", entry.getValue().toString());
            upList.appendTag(c);
        }
        nbt.setTag(LINK_NBT_UPSTREAM_TAG, upList);

        System.out.println("[EOHB] Saving OrundumLink network. Nodes=" + nodes.size());
    }
}
