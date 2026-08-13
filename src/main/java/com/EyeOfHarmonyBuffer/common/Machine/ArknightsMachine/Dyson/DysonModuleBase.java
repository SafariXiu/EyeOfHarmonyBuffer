package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.Utils.mergeArray;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;

/**
 * 戴森模块基类：由核心控制连接状态，能量只走无线 EU / Orundum 两本账，
 * 消耗与产出统一走 Orundum 账本（接收模块产出结算后续单独设计）。
 */
public abstract class DysonModuleBase<T extends DysonModuleBase<T>>
    extends OrundumWirelessMultiMachineBase<T> {

    protected boolean connected = false;
    protected long lastConnectTick = Long.MIN_VALUE;
    protected BigInteger pendingCost = BigInteger.ZERO;
    protected BigInteger pendingGain = BigInteger.ZERO;

    public enum ModuleType {
        MANUFACTURING,
        LAUNCH,
        RECEIVER,
        FUNCTIONAL
    }

    public DysonModuleBase(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public DysonModuleBase(String aName) {
        super(aName);
    }

    public abstract ModuleType getModuleType();

    /** 启用所需贴片数（当前三类均为 0，留给未来功能性模块设门槛）。 */
    public int getRequiredPaste() {
        return 0;
    }

    /** 本模块固定算力需求。 */
    public BigInteger getRequiredCompute() {
        return BigInteger.ZERO;
    }

    /** 核心在线时每 tick 刷新心跳；超过心跳窗口即视为核心离线。 */
    public void connect(long worldTime) {
        connected = true;
        lastConnectTick = worldTime;
    }

    public void disconnect() {
        connected = false;
    }

    public boolean isConnected() {
        return connected;
    }

    public boolean isFormed() {
        return mMachine;
    }

    @Override
    protected BigInteger getRequiredComputeForCurrentRecipe() {
        return getRequiredCompute();
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0.0F;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @Override
    protected boolean usesOrundumCost() {
        return false;
    }

    protected boolean canOperate() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!connected || !mMachine || base == null || base.isDead() || !base.isAllowedToWork()) {
            return false;
        }
        World world = base.getWorld();
        if (!DysonMachineConfig.isInTalos(world)) {
            return false;
        }
        // 心跳：核心区块卸载/停机后，模块在窗口内自动停止工作
        return world.getTotalWorldTime() - lastConnectTick <= DysonMachineConfig.CORE_HEARTBEAT_TICKS;
    }

    /** 队伍语义：每次实时解析（SpaceProject 队长），无队伍时退回 owner；被踢后模块会跟随到个人。 */
    protected UUID getTeamId() {
        UUID resolved = OrundumEnergyService.getTeamIdForUser(ownerUUID);
        return resolved != null ? resolved : ownerUUID;
    }

    protected DysonTeamProgress getTeamProgress(World world) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        if (data == null) {
            return null;
        }
        return data.getTeam(getTeamId());
    }

    /** 队伍级升级树查询：本队是否已解锁指定节点。 */
    protected boolean isUpgradeActive(DysonUpgrade upgrade) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null || upgrade == null) {
            return false;
        }
        return DysonSphereSystem.isTeamUpgradeActive(base.getWorld(), getTeamId(), upgrade);
    }

    protected boolean isTeamCompleted(World world) {
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        return data != null && data.isCompleted() && getTeamId().equals(data.getCompletedTeamId());
    }

    /** 本周期支出（EU 等价），制造模块覆写为配方成本，发射模块用 pendingCost。 */
    protected BigInteger getWirelessCost() {
        return pendingCost;
    }

    /** 本周期收入（EU 等价），接收模块用 pendingGain。 */
    protected BigInteger getWirelessGain() {
        return pendingGain;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        if (!canOperate()) {
            this.lastUsedParallel = 0;
            this.mOutputItems = null;
            this.mOutputFluids = null;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }
        return super.doWirelessBusinessOnce();
    }

    @Override
    protected CheckRecipeResult wirelessPostProcess(CheckRecipeResult opResult) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.isDead()) {
            endRecipeProcessing();
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        // 算力校验
        if (actsAsComputeConsumer() && ownerUUID != null) {
            BigInteger demand = getRequiredComputeForCurrentRecipe();
            if (demand != null && demand.signum() > 0) {
                WirelessComputeHelper.updateConsumer(this);
                if (!WirelessComputeHelper.isConsumerSatisfiedInGroup(this)) {
                    endRecipeProcessing();
                    return SimpleCheckRecipeResult.ofFailure("InsufficientCompute");
                }
            }
        }

        // 支出
        BigInteger cost = getWirelessCost();
        if (cost != null && cost.signum() > 0) {
            if (!payOrundumCost(cost)) {
                endRecipeProcessing();
                return CheckRecipeResultRegistry.insufficientPower(safeToLong(cost));
            }
            this.costingEU = this.costingEU.add(cost);
        }

        // 收入
        BigInteger gain = getWirelessGain();
        if (gain != null && gain.signum() > 0) {
            creditGain(gain);
            this.costingEU = this.costingEU.add(gain);
        }
        this.costingEUText = NumberFormatUtil.formatNumber(this.costingEU);

        collectWirelessOutputs();

        endRecipeProcessing();
        return opResult;
    }

    /** 默认把配方产物合并到机器输出；制造模块覆写为“产物折成队伍组件库存”。 */
    protected void collectWirelessOutputs() {
        if (processingLogic != null) {
            mOutputItems = mergeArray(mOutputItems, processingLogic.getOutputItems());
            mOutputFluids = mergeArray(mOutputFluids, processingLogic.getOutputFluids());
        }
    }

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(origin));
        lines.add(
            EnumChatFormatting.AQUA + "模块状态: "
                + (connected
                    ? EnumChatFormatting.GREEN + "已连接"
                    : EnumChatFormatting.RED + "未连接（需核心在线）"));

        BigInteger demand = getRequiredCompute();
        if (demand.signum() > 0) {
            lines.add(EnumChatFormatting.AQUA + "算力需求: " + EnumChatFormatting.GOLD + demand);
        }

        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide() && ownerUUID != null) {
            long cloudComponents = DysonSphereSystem.getPlayerCloudComponents(base.getWorld(), ownerUUID);
            long frameComponents = DysonSphereSystem.getPlayerFrameComponents(base.getWorld(), ownerUUID);
            lines.add(
                EnumChatFormatting.AQUA + "个人云组件库存: "
                    + EnumChatFormatting.GOLD
                    + cloudComponents
                    + EnumChatFormatting.AQUA
                    + " / 框架组件库存: "
                    + EnumChatFormatting.GOLD
                    + frameComponents);
        }
        return lines.toArray(new String[0]);
    }

    /** 模块消耗全部走 Orundum 账本（1 EU 成本基准 ≡ 1 Orundum）；余额不足时不扣。 */
    protected boolean payOrundumCost(BigInteger total) {
        if (total == null || total.signum() <= 0 || ownerUUID == null) {
            return true;
        }
        return consumeOrundumForOwner(ownerUUID, total);
    }

    /** 模块产出全部入 Orundum 账本（接收模块专属逻辑后续单独设计）。 */
    protected void creditOrundumPower(BigInteger total) {
        if (total == null || total.signum() <= 0 || ownerUUID == null) {
            return;
        }
        produceOrundumForOwner(ownerUUID, total);
    }

    /** 产出入账钩子：默认全走 Orundum，接收模块覆写为按百分比拆分 EU/Orundum。 */
    protected void creditGain(BigInteger total) {
        creditOrundumPower(total);
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) {
            return;
        }
        BigInteger demand = getRequiredCompute();
        if (ownerUUID != null && connected && demand.signum() > 0) {
            WirelessComputeHelper.updateConsumer(this);
        } else if (demand.signum() > 0) {
            WirelessComputeHelper.unregisterConsumer(this);
        }
    }
}
