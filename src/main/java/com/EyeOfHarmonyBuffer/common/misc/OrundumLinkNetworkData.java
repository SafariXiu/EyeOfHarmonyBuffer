package com.EyeOfHarmonyBuffer.common.misc;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.event.world.WorldEvent;

import java.io.*;
import java.util.*;

public class OrundumLinkNetworkData extends WorldSavedData {

    public static OrundumLinkNetworkData INSTANCE;

    private static final String DATA_NAME = "EOHB_OrundumLinkNetwork";
    private static final String LINK_NBT_TAG = "EOHB_OrundumLink_MapNBTTag";

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
            return INSTANCE;
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

        INSTANCE = data;
        return data;
    }

    private static void loadInstance(World world) {
        MapStorage storage = world.mapStorage;
        INSTANCE = (OrundumLinkNetworkData) storage.loadData(OrundumLinkNetworkData.class, DATA_NAME);
        if (INSTANCE == null) {
            INSTANCE = new OrundumLinkNetworkData();
            storage.setData(DATA_NAME, INSTANCE);
        }
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
    @SuppressWarnings("unchecked")
    public void readFromNBT(NBTTagCompound nbt) {
        nodes.clear();
        adjacency.clear();
        upstreamOf.clear();

        if (!nbt.hasKey(LINK_NBT_TAG)) {
            System.out.println("[EOHB] No OrundumLink NBT tag found, starting empty link network.");
            return;
        }

        try {
            byte[] ba = nbt.getByteArray(LINK_NBT_TAG);
            InputStream bais = new ByteArrayInputStream(ba);
            ObjectInputStream ois = new ObjectInputStream(bais);
            Object data = ois.readObject();

            if (data instanceof OrundumLinkNetworkPersist persist) {
                nodes.putAll(persist.nodes);
                adjacency.putAll(persist.adjacency);
                upstreamOf.putAll(persist.upstreamOf);
                System.out.println("[EOHB] Loaded OrundumLink network. Nodes=" + nodes.size());
            } else {
                System.out.println("[EOHB] Unexpected data type for OrundumLink: " + data.getClass());
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(LINK_NBT_TAG + " LOAD FAILED");
            e.printStackTrace();
        }

        networkDirty = true;
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        try {
            OrundumLinkNetworkPersist persist = new OrundumLinkNetworkPersist();
            persist.nodes.putAll(this.nodes);
            persist.adjacency.putAll(this.adjacency);
            persist.upstreamOf.putAll(this.upstreamOf);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos);
            oos.writeObject(persist);
            oos.flush();

            byte[] data = bos.toByteArray();
            nbt.setByteArray(LINK_NBT_TAG, data);

            System.out.println("[EOHB] Saving OrundumLink network. Nodes=" + nodes.size());
        } catch (IOException e) {
            System.out.println(LINK_NBT_TAG + " SAVE FAILED");
            e.printStackTrace();
        }
    }

    public static class OrundumLinkNetworkPersist implements Serializable {
        private static final long serialVersionUID = 1L;

        public final HashMap<UUID, LinkNodeEntry> nodes = new HashMap<>();
        public final HashMap<UUID, List<UUID>> adjacency = new HashMap<>();
        public final HashMap<UUID, UUID> upstreamOf = new HashMap<>();
    }
}
