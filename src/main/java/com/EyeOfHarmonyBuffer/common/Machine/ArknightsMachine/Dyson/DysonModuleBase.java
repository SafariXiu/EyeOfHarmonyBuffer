package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.Utils.mergeArray;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson.upgrade.DysonUpgrade;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereSystem;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.recipe.check.SimpleCheckRecipeResult;
import gregtech.api.util.shutdown.ShutDownReason;
import gregtech.api.util.shutdown.SimpleShutDownReason;

/**
 * 戴森模块基类：由核心控制连接状态，能量只走无线 EU / Orundum 两本账，
 * 消耗统一走 Orundum 账本；接收模块产出按 GUI 配置的 Orundum 占比拆分两本账。
 */
public abstract class DysonModuleBase<T extends DysonModuleBase<T>>
    extends OrundumWirelessMultiMachineBase<T> {

    /** 完工锁：非胜利队伍全部锁定；胜利队伍的制造/发射锁定（接收模块保留）。 */
    public static final ShutDownReason DYSON_COMPLETED_REASON =
        SimpleShutDownReason.ofNormal("dyson_completed");

    protected boolean connected = false;
    protected long lastConnectTick = Long.MIN_VALUE;
    protected BigInteger pendingCost = BigInteger.ZERO;
    protected BigInteger pendingGain = BigInteger.ZERO;

    /** 绑定的戴森核心坐标：链接关系的真身持久化在模块侧，核心侧的链接列表不存档。 */
    private int controllerX = 0;
    private int controllerY = 0;
    private int controllerZ = 0;
    private boolean controllerSet = false;
    private DysonCore controller = null;

    /** 链接结果（无距离限制；限制为维度/权限/槽位校验）。 */
    private enum LinkResult {
        NO_VALID_CORE,
        PERMISSION_DENIED,
        SLOTS_FULL,
        DIMENSION_RESTRICTED,
        SUCCESS
    }

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

    /** 链接权限：机主本人或同队成员（自动重连时 player 为 null，跳过权限校验）。 */
    public static boolean canPlayerLinkMachine(UUID ownerUUID, EntityPlayer player) {
        if (player == null || ownerUUID == null) {
            return true;
        }
        if (player.getUniqueID().equals(ownerUUID)) {
            return true;
        }
        UUID myTeam = OrundumEnergyService.getTeamIdForUser(ownerUUID);
        UUID playerTeam = OrundumEnergyService.getTeamIdForUser(player.getUniqueID());
        return myTeam != null && myTeam.equals(playerTeam);
    }

    public DysonCore getController() {
        if (controller == null) {
            return null;
        }
        if (controller.getBaseMetaTileEntity() == null) {
            return null;
        }
        return controller;
    }

    public boolean isLinked() {
        return getController() != null;
    }

    /** 核心被拆时调用：清空本模块对核心的全部引用（坐标持久化数据一并清除）。 */
    public void unlinkController() {
        this.controllerSet = false;
        this.controller = null;
        this.controllerX = 0;
        this.controllerY = 0;
        this.controllerZ = 0;
    }

    /** 按坐标绑定核心：无距离限制，只校验“目标确实是戴森核心 + 双方权限 + 核心槽位未满”。 */
    private LinkResult trySetControllerFromCoord(int x, int y, int z, EntityPlayer player) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.getWorld() == null) {
            return LinkResult.NO_VALID_CORE;
        }
        // 链接维度约束：模块与核心必须在塔罗斯-2 或其空间站内（双方同世界，校验模块所在维度即可）
        if (!DysonMachineConfig.isInTalosOrStation(base.getWorld())) {
            return LinkResult.DIMENSION_RESTRICTED;
        }

        TileEntity te = base.getWorld().getTileEntity(x, y, z);
        if (te == null || !(te instanceof IGregTechTileEntity)) {
            return LinkResult.NO_VALID_CORE;
        }
        IMetaTileEntity metaTileEntity = ((IGregTechTileEntity) te).getMetaTileEntity();
        if (!(metaTileEntity instanceof DysonCore)) {
            return LinkResult.NO_VALID_CORE;
        }
        DysonCore core = (DysonCore) metaTileEntity;

        // 权限：模块与核心都必须允许该玩家操作
        if (player != null
            && (!canPlayerLinkMachine(ownerUUID, player)
                || !canPlayerLinkMachine(core.getOwnerUUID(), player))) {
            return LinkResult.PERMISSION_DENIED;
        }

        // 先尝试注册到新核心：槽位已满则保持原链接不变，避免“丢了旧的又没换上新的”
        if (!core.registerLinkedModule(this)) {
            return LinkResult.SLOTS_FULL;
        }
        // 注册成功后再解绑旧核心，避免一个模块同时挂在多个核心上
        DysonCore oldController = getController();
        if (oldController != null && oldController != core) {
            oldController.unregisterLinkedModule(this);
            this.unlinkController();
        }
        controllerX = x;
        controllerY = y;
        controllerZ = z;
        controllerSet = true;
        controller = core;
        return LinkResult.SUCCESS;
    }

    /** 数据棒右键：读取核心坐标并尝试绑定（GT5U 净化水厂同款交互）。 */
    private boolean tryLinkDataStick(EntityPlayer aPlayer) {
        ItemStack dataStick = aPlayer.inventory.getCurrentItem();
        if (!ItemList.Tool_DataStick.isStackEqual(dataStick, false, true)) {
            return false;
        }
        if (dataStick.stackTagCompound == null) {
            return false;
        }
        if (!"EOHBDysonCore".equals(dataStick.stackTagCompound.getString("type"))) {
            return false;
        }
        int x = dataStick.stackTagCompound.getInteger("x");
        int y = dataStick.stackTagCompound.getInteger("y");
        int z = dataStick.stackTagCompound.getInteger("z");
        LinkResult result = trySetControllerFromCoord(x, y, z, aPlayer);
        switch (result) {
            case SUCCESS:
                aPlayer.addChatMessage(new ChatComponentText(Dyson_Link_Success));
                break;
            case NO_VALID_CORE:
                aPlayer.addChatMessage(new ChatComponentText(Dyson_Link_Fail_NoCore));
                break;
            case PERMISSION_DENIED:
                aPlayer.addChatMessage(new ChatComponentText(Dyson_Link_Fail_Permission));
                break;
            case SLOTS_FULL:
                aPlayer.addChatMessage(new ChatComponentText(Dyson_Link_Fail_Slots));
                break;
            case DIMENSION_RESTRICTED:
                aPlayer.addChatMessage(new ChatComponentText(Dyson_Link_Fail_Dimension));
                break;
        }
        return true;
    }

    @Override
    public boolean onRightclick(IGregTechTileEntity aBaseMetaTileEntity, EntityPlayer aPlayer) {
        if (!aBaseMetaTileEntity.getWorld().isRemote && tryLinkDataStick(aPlayer)) {
            return true;
        }
        return super.onRightclick(aBaseMetaTileEntity, aPlayer);
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
        if (!DysonMachineConfig.isInTalosOrStation(world)) {
            return false;
        }
        // 心跳：核心区块卸载/停机后，模块在窗口内自动停止工作
        return world.getTotalWorldTime() - lastConnectTick <= DysonMachineConfig.CORE_HEARTBEAT_TICKS;
    }

    @Override
    protected CheckRecipeResult wirelessPreCheck() {
        CheckRecipeResult result = super.wirelessPreCheck();
        if (!result.wasSuccessful()) {
            return result;
        }

        // 算力必须在任何副作用（吞原料 / 发射组件）之前校验：
        // 之前放在 wirelessPostProcess 里，制造模块会先被配方检查吞掉原料、发射模块会先打上天，
        // 然后才因为算力不足失败，造成原料丢失或“免费发射”。
        if (actsAsComputeConsumer() && ownerUUID != null) {
            BigInteger demand = getRequiredComputeForCurrentRecipe();
            if (demand != null && demand.signum() > 0) {
                WirelessComputeHelper.updateConsumer(this);
                if (!WirelessComputeHelper.isConsumerSatisfiedInGroup(this)) {
                    return SimpleCheckRecipeResult.ofFailure("InsufficientCompute");
                }
            }
        }

        return result;
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
            scheduleRecipeCheckImmediate();
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
            connected
                ? EnumChatFormatting.AQUA + Dyson_Info_ModuleConnected
                : EnumChatFormatting.AQUA + Dyson_Info_ModuleDisconnected);
        lines.add(
            isLinked()
                ? EnumChatFormatting.AQUA
                    + StatCollector.translateToLocalFormatted(
                        "Dyson_Info_Linked", controllerX, controllerY, controllerZ)
                : EnumChatFormatting.RED + Dyson_Info_NotLinked);

        BigInteger demand = getRequiredCompute();
        if (demand.signum() > 0) {
            lines.add(EnumChatFormatting.AQUA + Dyson_Info_ComputeRequirement + EnumChatFormatting.GOLD + demand);
        }

        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide() && ownerUUID != null) {
            long cloudComponents = DysonSphereSystem.getPlayerCloudComponents(base.getWorld(), ownerUUID);
            long frameComponents = DysonSphereSystem.getPlayerFrameComponents(base.getWorld(), ownerUUID);
            lines.add(
                EnumChatFormatting.AQUA + Dyson_Info_CloudComponentStock
                    + EnumChatFormatting.GOLD
                    + cloudComponents
                    + EnumChatFormatting.AQUA
                    + " / "
                    + Dyson_Info_FrameComponentStock
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

    /** 模块产出全部入 Orundum 账本（接收模块覆写 creditGain 按百分比拆分）。 */
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

        // 重载/区块重载后自动重连核心（核心侧链接列表不存档，靠模块侧持久化的坐标恢复）
        if (aTick % 100 == 5 && controllerSet && getController() == null) {
            trySetControllerFromCoord(controllerX, controllerY, controllerZ, null);
        }

        // 完工锁：非胜利队伍的全部模块禁止开机；胜利队伍的制造/发射模块禁止开机（接收模块除外）
        World world = aBaseMetaTileEntity.getWorld();
        if (world != null) {
            DysonSphereWorldData data = DysonSphereWorldData.get(world);
            if (data != null && data.isCompleted()) {
                boolean winner = getTeamId() != null
                    && getTeamId().equals(data.getCompletedTeamId());
                boolean allowed = winner && getModuleType() == ModuleType.RECEIVER;
                if (!allowed) {
                    disableWorking();
                    stopMachine(DYSON_COMPLETED_REASON);
                    return;
                }
            }
        }

        BigInteger demand = getRequiredCompute();
        if (ownerUUID != null && connected && demand.signum() > 0) {
            WirelessComputeHelper.updateConsumer(this);
        } else if (demand.signum() > 0) {
            WirelessComputeHelper.unregisterConsumer(this);
        }
    }

    @Override
    public void onBlockDestroyed() {
        DysonCore controller = getController();
        if (controller != null) {
            controller.unregisterLinkedModule(this);
        }
        super.onBlockDestroyed();
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        if (controllerSet) {
            NBTTagCompound controllerNBT = new NBTTagCompound();
            controllerNBT.setInteger("x", controllerX);
            controllerNBT.setInteger("y", controllerY);
            controllerNBT.setInteger("z", controllerZ);
            aNBT.setTag("controller", controllerNBT);
        }
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("controller")) {
            NBTTagCompound controllerNBT = aNBT.getCompoundTag("controller");
            controllerX = controllerNBT.getInteger("x");
            controllerY = controllerNBT.getInteger("y");
            controllerZ = controllerNBT.getInteger("z");
            controllerSet = true;
        }
    }

    // ---- Waila：戴森模块不接能量仓，只显示连接状态 / 算力 / 个人组件库存 ----

    @Override
    protected boolean shouldShowEuWirelessHud() {
        return false;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);
        NBTTagCompound tag = accessor.getNBTData();
        if (tag.getBoolean("dysonCompletedShutdown")) {
            // 戴森球已成型：被锁死的模块不再显示“未连接”，而是明确告知已关闭
            currentTip.add(EnumChatFormatting.RED + Dyson_Info_CompletedShutdown);
        } else {
            if (tag.getBoolean("dysonLinked")) {
                currentTip.add(
                    EnumChatFormatting.AQUA
                        + StatCollector.translateToLocalFormatted(
                            "Dyson_Info_Linked",
                            tag.getInteger("dysonLinkX"),
                            tag.getInteger("dysonLinkY"),
                            tag.getInteger("dysonLinkZ")));
            } else {
                currentTip.add(EnumChatFormatting.RED + Dyson_Info_NotLinked);
            }
            currentTip.add(
                tag.getBoolean("dysonConnected")
                    ? EnumChatFormatting.AQUA + Dyson_Info_ModuleConnected
                    : EnumChatFormatting.AQUA + Dyson_Info_ModuleDisconnected);
        }
        if (tag.hasKey("dysonCompute")) {
            currentTip.add(
                EnumChatFormatting.AQUA + Dyson_Info_ComputeRequirement
                    + EnumChatFormatting.GOLD + tag.getString("dysonCompute"));
        }
        if (tag.hasKey("dysonCloud") && tag.hasKey("dysonFrame")) {
            currentTip.add(
                EnumChatFormatting.AQUA + Dyson_Info_CloudComponentStock
                    + EnumChatFormatting.GOLD + tag.getLong("dysonCloud")
                    + EnumChatFormatting.AQUA + " / " + Dyson_Info_FrameComponentStock
                    + EnumChatFormatting.GOLD + tag.getLong("dysonFrame"));
        }
        appendWailaRoundStats(tag, currentTip);
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world,
                                int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);
        tag.setBoolean("dysonLinked", isLinked());
        if (isLinked()) {
            tag.setInteger("dysonLinkX", controllerX);
            tag.setInteger("dysonLinkY", controllerY);
            tag.setInteger("dysonLinkZ", controllerZ);
        }
        tag.setBoolean("dysonConnected", connected);
        BigInteger demand = getRequiredCompute();
        if (demand.signum() > 0) {
            tag.setString("dysonCompute", demand.toString());
        }
        if (ownerUUID != null) {
            tag.setLong("dysonCloud", DysonSphereSystem.getPlayerCloudComponents(world, ownerUUID));
            tag.setLong("dysonFrame", DysonSphereSystem.getPlayerFrameComponents(world, ownerUUID));
        }
        // 完工锁死：非胜利队伍的模块、胜利队伍的制造/发射模块都会被关闭
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        boolean winnerReceiver = data != null
            && data.isCompleted()
            && getTeamId() != null
            && getTeamId().equals(data.getCompletedTeamId())
            && getModuleType() == ModuleType.RECEIVER;
        tag.setBoolean("dysonCompletedShutdown", data != null && data.isCompleted() && !winnerReceiver);
        writeWailaRoundStats(tag, world);
    }

    /** 子类可写入本轮统计（发射/制造数量、产出拆分等）供 Waila 显示，默认无。 */
    protected void writeWailaRoundStats(NBTTagCompound tag, World world) {}

    /** 子类可追加本轮统计行到 Waila，默认无。 */
    protected void appendWailaRoundStats(NBTTagCompound tag, List<String> currentTip) {}
}
