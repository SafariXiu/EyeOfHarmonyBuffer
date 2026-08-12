package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine.Dyson;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.sBlockCasings8;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.EyeOfHarmonyBuffer.common.dyson.DysonSphereWorldData;
import com.EyeOfHarmonyBuffer.common.dyson.DysonTeamProgress;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.WirelessComputeNetwork.WirelessComputeHelper;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;

import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.IGTHatchAdder;
import gregtech.api.util.MultiblockTooltipBuilder;

/**
 * 戴森核心：每队一台的模块化巨构枢纽。
 * <p>
 * 占位结构 11×9×9，含 32 个模块位；按本队贴片数激活槽位（8/12/16/20/32），
 * 完工后解锁后 12 槽；接收模块每核心至多 1 台；核心算力 100 万，不足则全部模块断开。
 */
public class DysonCore extends OrundumWirelessMultiMachineBase<DysonCore>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<DysonCore> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainDysonCore";
    private static final int OffsetsX = 16;
    private static final int OffsetsY = 2;
    private static final int OffsetsZ = 1;
    private static final int CASING_INDEX = 183;

    public final ArrayList<DysonModuleBase<?>> moduleHatches = new ArrayList<>();

    public DysonCore(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public DysonCore(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 20;
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
    protected boolean shouldRequireOrundumField() {
        return false;
    }

    @Override
    protected boolean usesOrundumCost() {
        return false;
    }

    @Override
    protected BigInteger getRequiredComputeForCurrentRecipe() {
        return BigInteger.valueOf(DysonMachineConfig.coreCompute);
    }

    protected UUID getTeamId() {
        UUID resolved = OrundumEnergyService.getTeamIdForUser(ownerUUID);
        return resolved != null ? resolved : ownerUUID;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null || !base.isAllowedToWork()
            || !DysonMachineConfig.isInTalos(base.getWorld())) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        mEfficiency = 10000;
        mEfficiencyIncrease = 0;
        mMaxProgresstime = getWirelessModeProcessingTime();
        mProgresstime = 0;
        mEUt = 0;
        mOutputItems = null;
        mOutputFluids = null;

        this.lastOrundumCost = BigInteger.ZERO;
        this.lastUsedParallel = 1;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);
        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) {
            return;
        }

        // 维度强约束：只能在塔罗斯 2 运行，否则全部模块断开
        if (!DysonMachineConfig.isInTalos(aBaseMetaTileEntity.getWorld())) {
            disconnectAll();
            return;
        }

        // 核心算力门控：不足则全部模块断开
        boolean computeOk = false;
        if (ownerUUID != null) {
            WirelessComputeHelper.updateConsumer(this);
            computeOk = WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
        }
        // 核心不在线（未成型 / 算力不足 / 被停机）时全部模块断开
        if (!mMachine || !computeOk || !aBaseMetaTileEntity.isActive()) {
            disconnectAll();
            return;
        }

        World world = aBaseMetaTileEntity.getWorld();
        long worldTime = world.getTotalWorldTime();
        DysonSphereWorldData data = DysonSphereWorldData.get(world);
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        int paste = team == null ? 0 : team.pasteCount;
        int activeSlots = DysonMachineConfig.activeSlotsForPaste(paste);

        int connectedCount = 0;
        int receivers = 0;
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module == null) {
                continue;
            }
            if (!module.isFormed() || connectedCount >= activeSlots || module.getRequiredPaste() > paste) {
                module.disconnect();
                continue;
            }
            // 接收模块每核心至多 1 台
            if (module.getModuleType() == DysonModuleBase.ModuleType.RECEIVER) {
                if (receivers > 0) {
                    module.disconnect();
                    continue;
                }
                receivers++;
            }
            // 模块自身算力
            BigInteger moduleCompute = module.getRequiredCompute();
            if (moduleCompute.signum() > 0) {
                WirelessComputeHelper.updateConsumer(module);
                if (!WirelessComputeHelper.isConsumerSatisfiedInGroup(module)) {
                    module.disconnect();
                    continue;
                }
            }
            module.connect(worldTime);
            connectedCount++;
        }
    }

    private void disconnectAll() {
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module != null) {
                module.disconnect();
            }
        }
    }

    @Override
    public String[] getInfoData() {
        String[] origin = super.getInfoData();
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(origin));

        int connectedCount = 0;
        for (DysonModuleBase<?> module : moduleHatches) {
            if (module != null && module.isConnected()) {
                connectedCount++;
            }
        }

        IGregTechTileEntity base = getBaseMetaTileEntity();
        DysonSphereWorldData data = base == null ? null : DysonSphereWorldData.get(base.getWorld());
        DysonTeamProgress team = data == null ? null : data.getTeam(getTeamId());
        int paste = team == null ? 0 : team.pasteCount;

        lines.add(
            EnumChatFormatting.AQUA + "已连接模块: "
                + EnumChatFormatting.GOLD
                + connectedCount
                + EnumChatFormatting.AQUA
                + " / 激活槽位: "
                + EnumChatFormatting.GOLD
                + DysonMachineConfig.activeSlotsForPaste(paste));
        lines.add(
            EnumChatFormatting.AQUA + "本队贴片: "
                + EnumChatFormatting.GOLD
                + paste);
        if (team != null) {
            lines.add(
                EnumChatFormatting.AQUA + "组件库存: 云 "
                    + EnumChatFormatting.GOLD
                    + team.cloudComponents
                    + EnumChatFormatting.AQUA
                    + " / 框架 "
                    + EnumChatFormatting.GOLD
                    + team.frameComponents);
        }

        if (base != null && base.isServerSide()) {
            boolean computeOk = ownerUUID != null && WirelessComputeHelper.isConsumerSatisfiedInGroup(this);
            lines.add(
                EnumChatFormatting.AQUA + "核心算力: "
                    + (computeOk
                        ? EnumChatFormatting.GREEN + "满足"
                        : EnumChatFormatting.RED + "不足（需 1,000,000）"));
        }
        return lines.toArray(new String[0]);
    }

    public boolean addModuleTile(IGregTechTileEntity tileEntity) {
        if (tileEntity == null) {
            return false;
        }
        IMetaTileEntity metaTileEntity = tileEntity.getMetaTileEntity();
        if (metaTileEntity instanceof DysonModuleBase) {
            DysonModuleBase<?> module = (DysonModuleBase<?>) metaTileEntity;
            if (!moduleHatches.contains(module)) {
                return moduleHatches.add(module);
            }
            return true;
        }
        return false;
    }

    public enum moduleElement implements IHatchElement<DysonCore> {

        Module((core, tileEntity, index) -> core.addModuleTile(tileEntity), DysonModuleBase.class) {

            @Override
            public long count(DysonCore tileEntity) {
                return tileEntity.moduleHatches.size();
            }
        };

        private final List<Class<? extends IMetaTileEntity>> mteClasses;
        private final IGTHatchAdder<DysonCore> adder;

        @SafeVarargs
        moduleElement(IGTHatchAdder<DysonCore> adder, Class<? extends IMetaTileEntity>... mteClasses) {
            this.mteClasses = Collections.unmodifiableList(Arrays.asList(mteClasses));
            this.adder = adder;
        }

        @Override
        public List<? extends Class<? extends IMetaTileEntity>> mteClasses() {
            return mteClasses;
        }

        @Override
        public IGTHatchAdder<? super DysonCore> adder() {
            return adder;
        }
    }

    private static final String SP = "                                ";
    private static final String FULL_A = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
    private static final String CTRL = "                ~               ";
    private static final String ACOL = "                A               ";
    private static final String CLUSTER_A = "  AAA   AAA   AAA   AAA   AAA   ";
    private static final String CLUSTER_GAP = "  A A   A A   A A   A A   A A   ";

    private static final String[][] shapeMain = new String[][] {
        // y=0 空
        { SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP,
            SP, SP, SP, SP, SP, SP, SP, SP },
        // y=1 空
        { SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP, SP,
            SP, SP, SP, SP, SP, SP, SP, SP },
        // y=2 控制器 + 平台底
        { SP, SP, CTRL, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A,
            SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP,
            SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP },
        // y=3 模块层
        { SP, ACOL, SP, SP, "  ACA   ACA   ACA   ABA   ACA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ABA   ABA   ABA   ACA   ABA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ABA   ACA   ACA   ACA   ABA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ABA   ACA   ACA   ACA   ACA   ", CLUSTER_GAP, CLUSTER_A, SP, SP, SP,
            "  ACA   ACA   ABA   ABA   ACA   ", CLUSTER_GAP, CLUSTER_A, SP },
        // y=4 平台顶
        { SP, SP, ACOL, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A,
            SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP, SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP, SP,
            SP, CLUSTER_A, CLUSTER_A, CLUSTER_A, SP },
        // y=5 全地板
        { FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A,
            FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A,
            FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A, FULL_A }
    };

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack,
                             List<StructureError> errors) {
        moduleHatches.clear();
        checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) {
            return -1;
        }
        return survivalBuildPiece(
            STRUCTURE_PIECE_MAIN,
            stackSize,
            OffsetsX,
            OffsetsY,
            OffsetsZ,
            elementBudget,
            env,
            false,
            true);
    }

    @Override
    public IStructureDefinition<DysonCore> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            IStructureElement<DysonCore> moduleSlot = buildHatchAdder(DysonCore.class)
                .atLeast(moduleElement.Module)
                .casingIndex(CASING_INDEX)
                .hint(2)
                .buildAndChain(ofBlock(sBlockCasings8, 7));
            STRUCTURE_DEFINITION = StructureDefinition.<DysonCore>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings8, 7))
                .addElement('B', moduleSlot)
                .addElement('C', moduleSlot)
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType("戴森核心")
            .addInfo("每队限一台的戴森球巨构枢纽")
            .addInfo("最多挂载 32 个模块（占位结构，贴片数解锁槽位）")
            .addInfo("核心消耗 1,000,000 算力")
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new DysonCore(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side,
                                 ForgeDirection facing, int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) {
                return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                        .extFacing()
                        .glow()
                        .build() };
            }
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
        }
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX) };
    }
}
