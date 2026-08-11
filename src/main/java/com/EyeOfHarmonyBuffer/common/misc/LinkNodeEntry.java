package com.EyeOfHarmonyBuffer.common.misc;

import java.util.UUID;

/**
 * 单个链路节点（ProtocolCore / 中继器 / 供电站）
 */

public class LinkNodeEntry {

    public enum NodeType {
        PROTOCOL_CORE,
        REPEATER,
        SUBSTATION
    }

    public UUID nodeId;
    public NodeType type;
    public UUID teamId;
    public int dimId;
    public int x;
    public int y;
    public int z;

    public boolean physicalOnline;
    public boolean networkActive;
}
