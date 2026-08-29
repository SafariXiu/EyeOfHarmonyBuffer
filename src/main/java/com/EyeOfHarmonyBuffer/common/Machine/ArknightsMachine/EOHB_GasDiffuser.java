package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.material.EOHBMaterialPool;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.GasEnvironmentType;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.Gas.IGasEnvironmentProvider;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_GasDiffuser extends OrundumWirelessMultiMachineBase<EOHB_GasDiffuser>
    implements IConstructable, ISurvivalConstructable, IGasEnvironmentProvider {

    private static IStructureDefinition<EOHB_GasDiffuser> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainGasDiffuser";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 20;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    private static final int ENV_FLUID_PER_RUN = 6000;
    private int envTicksRemaining = 0;

    private GasEnvironmentType currentEnvironmentType = GasEnvironmentType.NONE;
    private GasEnvironmentType lastReportedEnv = GasEnvironmentType.NONE;
    private boolean pendingEnvChanged = false;

    private static final Map<Fluid, GasEnvironmentType> FLUID_TO_ENV = new Reference2ObjectOpenHashMap<>();

    static {
        FLUID_TO_ENV.put(
            EOHBMaterialPool.Acridgen.getFluidOrGas(1).getFluid(),
            GasEnvironmentType.ACRID
        );
        FLUID_TO_ENV.put(
            EOHBMaterialPool.Aquagen.getFluidOrGas(1).getFluid(),
            GasEnvironmentType.HUMID
        );
        FLUID_TO_ENV.put(
            EOHBMaterialPool.Inergen.getFluidOrGas(1).getFluid(),
            GasEnvironmentType.STABLE
        );
        FLUID_TO_ENV.put(
            EOHBMaterialPool.Xiragen.getFluidOrGas(1).getFluid(),
            GasEnvironmentType.XRANITE
        );
    }

    public EOHB_GasDiffuser(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_GasDiffuser(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 1200;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0;
    }

    @Override
    public int getMaxParallelRecipes() {
        return 1;
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.GasDiffuser;
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
    protected boolean actsAsComputeConsumer() {
        return false;
    }

    @Override
    protected CheckRecipeResult doWirelessBusinessOnce() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.isDead()) {
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        GasEnvironmentType envType = tryConsumeEnvironmentFluidForRun();
        if (envType == GasEnvironmentType.NONE) {
            this.lastUsedParallel = 0;
            return CheckRecipeResultRegistry.NO_FUEL_FOUND;
        }

        this.lastUsedParallel = 1;

        GasEnvironmentType oldEnv = currentEnvironmentType;
        currentEnvironmentType = envType;
        envTicksRemaining = getWirelessModeProcessingTime();

        if (currentEnvironmentType != oldEnv) {
            pendingEnvChanged = true;
        }

        mProgresstime = 0;
        mMaxProgresstime = getWirelessModeProcessingTime();
        mEUt = 0;
        mEfficiency = 10000;
        mEfficiencyIncrease = 0;

        mOutputItems = null;
        mOutputFluids = null;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        super.onFirstTick(aBaseMetaTileEntity);
        if (aBaseMetaTileEntity.isServerSide()) {
            World w = aBaseMetaTileEntity.getWorld();
            GasEnvironmentHelper.registerProvider(
                this,
                w,
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord()
            );
        }
    }

    @Override
    public void onRemoval() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            World w = base.getWorld();
            GasEnvironmentHelper.unregisterProvider(
                this,
                w,
                base.getXCoord(),
                base.getYCoord(),
                base.getZCoord()
            );
        }
        super.onRemoval();
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (!aBaseMetaTileEntity.isServerSide()) return;

        boolean canProvideNow = mMachine && aBaseMetaTileEntity.isActive();

        if (envTicksRemaining > 0) {
            envTicksRemaining--;
            if (envTicksRemaining == 0 && currentEnvironmentType != GasEnvironmentType.NONE) {
                currentEnvironmentType = GasEnvironmentType.NONE;
                pendingEnvChanged = true;
            }
        }

        if (canProvideNow && pendingEnvChanged) {
            World w = aBaseMetaTileEntity.getWorld();
            GasEnvironmentHelper.onProviderEnvironmentChanged(
                this,
                w,
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord()
            );
            lastReportedEnv = currentEnvironmentType;
            pendingEnvChanged = false;
        }

        // 缓存自愈兜底：机器正常提供环境时周期性重报一次，
        // 防止重进存档/区块重载等场景下缓存长期未同步。
        if (canProvideNow && currentEnvironmentType != GasEnvironmentType.NONE && aTick % 100 == 0) {
            World w = aBaseMetaTileEntity.getWorld();
            GasEnvironmentHelper.onProviderEnvironmentChanged(
                this,
                w,
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord()
            );
            lastReportedEnv = currentEnvironmentType;
        }

        // 仅在机器真正停止时清空恢复的环境状态。
        // 重进存档后结构检查有约 100 tick 延迟，期间 mMachine 为 false 但机器仍在运行，
        // 若此时清空会把 NBT 恢复的环境状态误删，导致后续无法重新上报。
        if (!canProvideNow && lastReportedEnv != GasEnvironmentType.NONE && !aBaseMetaTileEntity.isActive()) {
            World w = aBaseMetaTileEntity.getWorld();
            currentEnvironmentType = GasEnvironmentType.NONE;
            envTicksRemaining = 0;
            pendingEnvChanged = false;

            GasEnvironmentHelper.onProviderEnvironmentChanged(
                this,
                w,
                aBaseMetaTileEntity.getXCoord(),
                aBaseMetaTileEntity.getYCoord(),
                aBaseMetaTileEntity.getZCoord()
            );
            lastReportedEnv = GasEnvironmentType.NONE;
        }
    }

    private GasEnvironmentType tryConsumeEnvironmentFluidForRun() {
        List<FluidStack> stored = getStoredFluidsForColor(Optional.empty());
        if (stored == null || stored.isEmpty()) {
            return GasEnvironmentType.NONE;
        }

        Fluid selectedFluid = null;
        GasEnvironmentType selectedType = GasEnvironmentType.NONE;

        for (FluidStack fs : stored) {
            if (fs == null || fs.getFluid() == null) continue;
            GasEnvironmentType type = FLUID_TO_ENV.get(fs.getFluid());
            if (type != null && type != GasEnvironmentType.NONE) {
                selectedFluid = fs.getFluid();
                selectedType = type;
                break;
            }
        }

        if (selectedFluid == null || selectedType == GasEnvironmentType.NONE) {
            return GasEnvironmentType.NONE;
        }

        FluidStack req = new FluidStack(selectedFluid, ENV_FLUID_PER_RUN);
        if (!depleteInput(req, true)) {
            return GasEnvironmentType.NONE;
        }

        if (!depleteInput(req, false)) {
            return GasEnvironmentType.NONE;
        }

        return selectedType;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {
        boolean ok = checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);

        if (!ok) return;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(STRUCTURE_PIECE_MAIN, stackSize, hintsOnly, OffsetsX, OffsetsY, OffsetsZ);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(STRUCTURE_PIECE_MAIN, stackSize, OffsetsX, OffsetsY, OffsetsZ, elementBudget, env, false, true);
    }

    private static final String[][] shapeMain = new String[][]{
        {"         ","         ","   CCC   ","  CCCCC  ","  CCBCC  ","  CCCCC  ","   CCC   ","         ","         "},
        {"         ","         ","   BBB   ","  B   B  ","  B   B  ","  B   B  ","   BBB   ","         ","         "},
        {"         ","         ","   AAA   ","  A   A  ","  A   A  ","  A   A  ","   AAA   ","         ","         "},
        {"   CCC   ","  C   C  "," C     C ","C  AAA  C","CD AAA DC","C  AAA  C"," C     C ","  C   C  ","   CCC   "},
        {"         ","         ","         ","   AAA   ","AD A A DA","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   ","AD A A DA","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   ","A DA AD A","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   AAA   ","A DA AD A","   AAA   ","         ","         ","         "},
        {"         ","         ","         ","   BBB   ","A DBBBD A","   BBB   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","A  C C  A","   CCC   ","         ","         ","         "},
        {"   EEE   ","   AAA   ","   AAA   ","  ACCCA  ","AAAC CAAA","  ACCCA  ","   AAA   ","   BBB   ","         "},
        {"   EEE   ","   AAA   ","   AAA   ","   CCC   ","ABBC CBBA","   CCC   ","   AAA   ","   BBB   ","         "},
        {"         ","         ","   CCC   ","  C   C  ","ABC   CBA","  C   C  ","   CCC   ","         ","         "},
        {"         ","  CCCCC  "," CC   CC "," C     C ","AC     CA"," C     C "," CC   CC ","  CCCCC  ","         "},
        {"         ","  CCCCC  "," CC   CC "," C     C ","AC     CA"," C     C "," CC   CC ","  CCCCC  ","         "},
        {"         ","         ","   CCC   ","  C   C  ","A C   C A","  C   C  ","   CCC   ","         ","         "},
        {"         ","         ","         ","   CCC   ","A  CCC  A","   CCC   ","         ","         ","         "},
        {"         ","  CCCCC  "," CCCCCCC "," CCCCCCC ","ACCCCCCCA"," CCCCCCC "," CCCCCCC ","  CCCCC  ","         "},
        {"         ","  AAAAA  "," AAAAAAA "," AAAAAAA ","AAAAAAAAA"," AAAAAAA "," AAAAAAA ","  AAAAA  ","         "},
        {"BAAAAAAAB","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","BAAAAAAAB"},
        {"BAAA~AAAB","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","AAAAAAAAA","BAAAAAAAB"}
    };

    @Override
    public IStructureDefinition<EOHB_GasDiffuser> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_GasDiffuser>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', ofBlock(sBlockCasings2, 0))
                .addElement('B', ofBlock(sBlockCasings2, 13))
                .addElement('C', ofBlock(sBlockCasings8, 7))
                .addElement('D', ofBlock(sBlockFrames, 305))
                .addElement(
                    'E',
                    buildHatchAdder(EOHB_GasDiffuser.class)
                        .atLeast(InputHatch)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings2, 0
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_GasDiffuser_MachineType)
            .addInfo(Tooltip_GasDiffuser_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_GasDiffuser_00)
            .addInfo(Tooltip_GasDiffuser_01)
            .addInfo(Tooltip_GasDiffuser_02)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        super.saveNBTData(aNBT);
        aNBT.setString("EOHB_CurrentGasEnv",
            currentEnvironmentType == null ? GasEnvironmentType.NONE.name() : currentEnvironmentType.name());
        aNBT.setString("EOHB_LastReportedEnv",
            lastReportedEnv == null ? GasEnvironmentType.NONE.name() : lastReportedEnv.name());
        aNBT.setInteger("EOHB_EnvTicksRemaining", envTicksRemaining);
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        super.loadNBTData(aNBT);
        if (aNBT.hasKey("EOHB_CurrentGasEnv")) {
            try {
                currentEnvironmentType = GasEnvironmentType
                    .valueOf(aNBT.getString("EOHB_CurrentGasEnv"));
            } catch (IllegalArgumentException ignored) {
                currentEnvironmentType = GasEnvironmentType.NONE;
            }
        } else {
            currentEnvironmentType = GasEnvironmentType.NONE;
        }

        if (aNBT.hasKey("EOHB_LastReportedEnv")) {
            try {
                lastReportedEnv = GasEnvironmentType
                    .valueOf(aNBT.getString("EOHB_LastReportedEnv"));
            } catch (IllegalArgumentException ignored) {
                lastReportedEnv = GasEnvironmentType.NONE;
            }
        } else {
            lastReportedEnv = GasEnvironmentType.NONE;
        }

        envTicksRemaining = aNBT.getInteger("EOHB_EnvTicksRemaining");
        // 重进存档后强制重新上报一次：
        // onFirstTick 注册提供者时结构检查/激活状态尚未恢复，当时的缓存重算结果不可靠。
        pendingEnvChanged = currentEnvironmentType != GasEnvironmentType.NONE
            || lastReportedEnv != GasEnvironmentType.NONE;
    }

    @Override
    public void onUnload() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base != null && base.isServerSide()) {
            World w = base.getWorld();
            if (w != null) {
                GasEnvironmentHelper.unregisterProvider(
                    this,
                    w,
                    base.getXCoord(),
                    base.getYCoord(),
                    base.getZCoord()
                );
            }
        }
        super.onUnload();
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player,
                                TileEntity tile,
                                NBTTagCompound tag,
                                World world,
                                int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        String inputFluidName = "";
        String producedEnvName = GasEnvironmentType.NONE.name();

        List<FluidStack> stored = getStoredFluidsForColor(Optional.empty());
        if (stored != null) {
            for (FluidStack fs : stored) {
                if (fs == null || fs.getFluid() == null) continue;
                GasEnvironmentType type = FLUID_TO_ENV.get(fs.getFluid());
                if (type != null && type != GasEnvironmentType.NONE) {
                    try {
                        inputFluidName = fs.getLocalizedName();
                    } catch (Throwable t) {
                        inputFluidName = fs.getFluid().getName();
                    }
                    producedEnvName = type.name();
                    break;
                }
            }
        }

        tag.setString("EOHB_InputFluidName", inputFluidName);
        tag.setString("EOHB_ProducedEnvName", producedEnvName);

        GasEnvironmentType cur = currentEnvironmentType == null
            ? GasEnvironmentType.NONE
            : currentEnvironmentType;
        tag.setString("EOHB_CurrentEnvName", cur.name());

        tag.setInteger("EOHB_EnvRadiusChunks", 3);
    }

    @Override
    public void getWailaBody(ItemStack itemStack,
                             List<String> currentTip,
                             IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);

        final NBTTagCompound tag = accessor.getNBTData();

        currentTip.add(EnumChatFormatting.LIGHT_PURPLE + NameGasDiffuser);

        String inputFluidName = tag.getString("EOHB_InputFluidName");
        if (inputFluidName == null || inputFluidName.isEmpty()) {
            inputFluidName = EOHB_Waila_None;
        }

        String inputLabel = StatCollector.translateToLocal("EOHB_Waila_InputFluid");
        currentTip.add(
            EnumChatFormatting.AQUA + inputLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + inputFluidName
        );

        String producedEnvKey = tag.getString("EOHB_ProducedEnvName");
        GasEnvironmentType producedEnv;
        try {
            producedEnv = GasEnvironmentType.valueOf(producedEnvKey);
        } catch (IllegalArgumentException e) {
            producedEnv = GasEnvironmentType.NONE;
        }

        String producedEnvName = getLocalizedEnvName(producedEnv);
        String producedLabel = StatCollector.translateToLocal("EOHB_Waila_GasDiffuser_ProducedEnv");
        currentTip.add(
            EnumChatFormatting.AQUA + producedLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + producedEnvName
        );

        String currentEnvKey = tag.getString("EOHB_CurrentEnvName");
        GasEnvironmentType currentEnv;
        try {
            currentEnv = GasEnvironmentType.valueOf(currentEnvKey);
        } catch (IllegalArgumentException e) {
            currentEnv = GasEnvironmentType.NONE;
        }

        String currentEnvName = getLocalizedEnvName(currentEnv);
        String currentLabel = StatCollector.translateToLocal("EOHB_Waila_GasDiffuser_CurrentEnv");
        currentTip.add(
            EnumChatFormatting.AQUA + currentLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + currentEnvName
        );

        int radiusChunks = tag.getInteger("EOHB_EnvRadiusChunks");
        if (radiusChunks <= 0) radiusChunks = 1;
        int diameterChunks = radiusChunks * 2 + 1;
        int diameterBlocks = diameterChunks * 16;

        String rangeLabel = StatCollector.translateToLocal("EOHB_Waila_GasDiffuser_range");
        currentTip.add(
            EnumChatFormatting.AQUA + rangeLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD
                + diameterChunks + "×" + diameterChunks + " 区块"
                + EnumChatFormatting.RESET + " (" + diameterBlocks + "×" + diameterBlocks + " 方块)"
        );
    }

    @Override
    protected boolean shouldShowWirelessWaila(NBTTagCompound tag) {
        return false;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_GasDiffuser(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive)
                return new ITexture[]{
                    Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                        .extFacing()
                        .build(),
                    TextureFactory.builder()
                        .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                        .extFacing()
                        .glow()
                        .build()
                };
            return new ITexture[]{
                Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_GLOW)
                    .extFacing()
                    .glow()
                    .build()
            };
        }
        return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_INDEX)};
    }

    private String getLocalizedEnvName(GasEnvironmentType type) {
        if (type == null) return "NONE";
        switch (type) {
            case ACRID:
                return "Acrid（酸性环境）";
            case HUMID:
                return "Humid（潮湿环境）";
            case STABLE:
                return "Stable（稳定环境）";
            case XRANITE:
                return "Xranite（息壤环境）";
            case NONE:
            default:
                return "无";
        }
    }

    @Override
    public GasEnvironmentType getProvidedEnvironmentType() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null || base.isDead() || !mMachine || !base.isActive()) {
            return GasEnvironmentType.NONE;
        }
        return currentEnvironmentType == null ? GasEnvironmentType.NONE : currentEnvironmentType;
    }
}
