package com.EyeOfHarmonyBuffer.common.dyson;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;

import com.EyeOfHarmonyBuffer.space.RegisterDimensions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 戴森球进度存档（服务端），按队伍维护三计数器。
 * <p>
 * 全局唯一，统一绑定塔罗斯 2（{@link RegisterDimensions#ID_TALOS2_DIM}）存档，
 * 无论在哪个维度调用都取同一份数据。天空盒渲染的是“当前领先队伍”的状态，
 * 由本类动态推导，见 {@link #findLeader()}。
 */
public class DysonSphereWorldData extends WorldSavedData {

    private static final String DATA_NAME = "EOHB_DysonSphere";
    private static DysonSphereWorldData INSTANCE;

    private final Map<UUID, DysonTeamProgress> teams = new HashMap<>();

    private boolean completed = false;
    private UUID completedTeamId = null;
    private String completedTeamName = "";

    public DysonSphereWorldData() {
        super(DATA_NAME);
    }

    public DysonSphereWorldData(String name) {
        super(name);
    }

    public static DysonSphereWorldData get(World world) {
        if (world == null || world.isRemote) {
            return INSTANCE;
        }

        // 统一绑定塔罗斯 2：机器/指令无论从哪个维度调用，都读写同一份存档
        World target = world;
        if (world.provider.dimensionId != RegisterDimensions.ID_TALOS2_DIM) {
            WorldServer talos = MinecraftServer.getServer().worldServerForDimension(RegisterDimensions.ID_TALOS2_DIM);
            if (talos == null) {
                return INSTANCE;
            }
            target = talos;
        }

        MapStorage storage = target.mapStorage;
        if (storage == null) {
            return INSTANCE;
        }

        DysonSphereWorldData data =
            (DysonSphereWorldData) storage.loadData(DysonSphereWorldData.class, DATA_NAME);
        if (data == null) {
            data = new DysonSphereWorldData();
            storage.setData(DATA_NAME, data);
            data.markDirty();
        }
        INSTANCE = data;
        return data;
    }

    // region 队伍数据

    public DysonTeamProgress getTeam(UUID teamId) {
        return teamId == null ? null : teams.get(teamId);
    }

    public DysonTeamProgress getOrCreateTeam(UUID teamId, String teamName) {
        if (teamId == null) return null;
        DysonTeamProgress team = teams.get(teamId);
        if (team == null) {
            team = new DysonTeamProgress(teamName);
            teams.put(teamId, team);
        } else if (teamName != null && !teamName.isEmpty()) {
            team.teamName = teamName;
        }
        return team;
    }

    public Collection<DysonTeamProgress> getTeams() {
        return teams.values();
    }

    public List<UUID> getTeamIds() {
        return new ArrayList<>(teams.keySet());
    }

    public void removeTeam(UUID teamId) {
        if (teamId == null) return;
        if (teams.remove(teamId) != null) {
            markDirty();
        }
    }

    public void clearAllTeams() {
        if (teams.isEmpty()) return;
        teams.clear();
        markDirty();
    }

    // endregion

    // region 完工状态

    public boolean isCompleted() {
        return completed;
    }

    public UUID getCompletedTeamId() {
        return completedTeamId;
    }

    public String getCompletedTeamName() {
        return completedTeamName;
    }

    public void markCompleted(UUID teamId, String teamName) {
        this.completed = true;
        this.completedTeamId = teamId;
        this.completedTeamName = teamName == null ? "" : teamName;
        markDirty();
    }

    public void clearCompletion() {
        this.completed = false;
        this.completedTeamId = null;
        this.completedTeamName = "";
        markDirty();
    }

    // endregion

    // region 领先者与展示状态（渲染用）

    /** 当前领先队伍：贴片进度优先，其次框架数，再其次首次发射时间。 */
    public DysonTeamProgress findLeader() {
        DysonTeamProgress leader = null;
        for (DysonTeamProgress team : teams.values()) {
            if (leader == null || isAhead(team, leader)) {
                leader = team;
            }
        }
        return leader;
    }

    private static boolean isAhead(DysonTeamProgress a, DysonTeamProgress b) {
        if (a.pasteCount != b.pasteCount) {
            return a.pasteCount > b.pasteCount;
        }
        if (a.frameCount != b.frameCount) {
            return a.frameCount > b.frameCount;
        }
        return a.firstLaunchTick < b.firstLaunchTick;
    }

    public int getCloudCount() {
        DysonTeamProgress leader = findLeader();
        return leader == null ? 0 : leader.cloudCount;
    }

    public int getFrameCount() {
        DysonTeamProgress leader = findLeader();
        return leader == null ? 0 : leader.frameCount;
    }

    public int getPasteCount() {
        DysonTeamProgress leader = findLeader();
        return leader == null ? 0 : leader.pasteCount;
    }

    public String getOwnerName() {
        if (completed && !completedTeamName.isEmpty()) {
            return completedTeamName;
        }
        DysonTeamProgress leader = findLeader();
        return leader == null || leader.teamName == null ? "" : leader.teamName;
    }

    public int getStage() {
        return DysonSphereSystem.computeStage(getCloudCount(), getFrameCount(), getPasteCount());
    }

    public float getProgress() {
        return Math.max(
            (float) getCloudCount() / DysonSphereState.CLOUD_CAP,
            Math.max(
                (float) getFrameCount() / DysonSphereState.FRAME_COMPLETE,
                (float) getPasteCount() / DysonSphereState.PASTE_COMPLETE));
    }

    // endregion

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        teams.clear();

        NBTTagList teamList = nbt.getTagList("Teams", 10);
        for (int i = 0; i < teamList.tagCount(); i++) {
            NBTTagCompound c = teamList.getCompoundTagAt(i);
            try {
                UUID teamId = UUID.fromString(c.getString("TeamId"));
                DysonTeamProgress team = new DysonTeamProgress(c.getString("Name"));
                team.cloudCount = c.getInteger("Cloud");
                team.frameCount = c.getInteger("Frame");
                team.pasteCount = c.getInteger("Paste");
                team.cloudComponents = c.getLong("CloudComponents");
                team.frameComponents = c.getLong("FrameComponents");
                team.firstLaunchTick = c.getLong("FirstLaunchTick");
                teams.put(teamId, team);
            } catch (IllegalArgumentException ignored) {
            }
        }

        completed = nbt.getBoolean("Completed");
        String completedId = nbt.getString("CompletedTeamId");
        if (completed && !completedId.isEmpty()) {
            try {
                completedTeamId = UUID.fromString(completedId);
            } catch (IllegalArgumentException e) {
                completedTeamId = null;
            }
        } else {
            completedTeamId = null;
        }
        completedTeamName = nbt.getString("CompletedTeamName");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        NBTTagList teamList = new NBTTagList();
        for (Map.Entry<UUID, DysonTeamProgress> entry : teams.entrySet()) {
            DysonTeamProgress team = entry.getValue();
            NBTTagCompound c = new NBTTagCompound();
            c.setString("TeamId", entry.getKey().toString());
            c.setInteger("Cloud", team.cloudCount);
            c.setInteger("Frame", team.frameCount);
            c.setInteger("Paste", team.pasteCount);
            c.setLong("CloudComponents", team.cloudComponents);
            c.setLong("FrameComponents", team.frameComponents);
            c.setLong("FirstLaunchTick", team.firstLaunchTick);
            c.setString("Name", team.teamName == null ? "" : team.teamName);
            teamList.appendTag(c);
        }
        nbt.setTag("Teams", teamList);

        nbt.setBoolean("Completed", completed);
        nbt.setString("CompletedTeamId", completedTeamId == null ? "" : completedTeamId.toString());
        nbt.setString("CompletedTeamName", completedTeamName == null ? "" : completedTeamName);
    }
}
