package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.Config.MachineLoaderConfig;
import com.EyeOfHarmonyBuffer.common.GTCMItemList;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.TAE;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.block.ModBlocks;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.enums.HatchElement.InputBus;
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_OrundumDynamo extends OrundumWirelessMultiMachineBase<EOHB_OrundumDynamo> implements IConstructable, ISurvivalConstructable {

    private static final BigInteger ORUNDUM_PER_PURE_ORIGINIUM = BigInteger.valueOf(100_000L);
    private static final int TICKS_PER_CYCLE = 20 * 5;
    private BigInteger pendingOrundum = BigInteger.ZERO;
    private static IStructureDefinition<EOHB_OrundumDynamo> STRUCTURE_DEFINITION = null;
    private int mCasing;

    public EOHB_OrundumDynamo(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    public EOHB_OrundumDynamo(String aName) {
        super(aName);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        return 0;
    }

    @Override
    protected boolean isEnablePerfectOverclock() {
        return false;
    }

    @Override
    protected float getSpeedBonus() {
        return 0;
    }

    @NotNull
    @Override
    public CheckRecipeResult checkProcessing() {
        if (mMaxProgresstime > 0) {
            return CheckRecipeResultRegistry.SUCCESSFUL;
        }

        ItemStack needed = GTCMItemList.YuanShi.get(1);

        if (!depleteInput(needed)) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        mMaxProgresstime     = TICKS_PER_CYCLE;
        mProgresstime        = 0;
        mEUt                 = 0;
        mEfficiency          = 10000;
        mEfficiencyIncrease  = 0;

        pendingOrundum = ORUNDUM_PER_PURE_ORIGINIUM;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void endRecipeProcessing() {
        if (pendingOrundum.signum() > 0 && ownerUUID != null) {
            OrundumEnergyService.changeOrundumForUser(ownerUUID, pendingOrundum);
        }
        pendingOrundum = BigInteger.ZERO;

        super.endRecipeProcessing();
    }

    @Override
    public boolean checkMachine(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        mCasing = 0;
        return checkPiece(mName, 1, 1, 0) && mCasing >= 6;
    }

    @Override
    public void construct(ItemStack stackSize, boolean hintsOnly) {
        repairMachine();
        buildPiece(mName, stackSize, hintsOnly, 1, 1, 0);
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivalBuildPiece(mName, stackSize, 1, 1, 0, elementBudget, env, false, true);
    }

    @Override
    public int getMaxParallelRecipes() {
        return 0;
    }

    @Override
    public IStructureDefinition<EOHB_OrundumDynamo> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_OrundumDynamo>builder()
                .addShape(
                    mName,
                    transpose(
                        new String[][] {
                            { "CCC", "CCC", "CCC" },
                            { "C~C", "C-C", "CCC" },
                            { "CCC", "CCC", "CCC" },
                        }))
                .addElement(
                    'C',
                    buildHatchAdder(EOHB_OrundumDynamo.class)
                        .atLeast(InputBus, OutputBus)
                        .casingIndex(getTextureIndex())
                        .dot(1)
                        .buildAndChain(onElementPass(x -> ++x.mCasing, ofBlock(ModBlocks.blockCasings3Misc, 2))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        if(MachineLoaderConfig.VendingMachines){
            tt.addMachineType(Tooltip_VendingMachines_MachineType)
                .addInfo(Tooltip_VendingMachines_Controller)
                .addInfo(Tooltip_VendingMachines_00)
                .addInfo(Tooltip_VendingMachines_01)
                .addInfo(Tooltip_VendingMachines_02)
                .addInfo(Tooltip_VendingMachines_03)
                .addSeparator()
                .addInputBus(add_InputBus,1)
                .addOutputBus(add_OutputBus,1)
                .addInfo(TextLocalization.StructureTooComplex)
                .addInfo(TextLocalization.BLUE_PRINT_INFO)
                .toolTipFinisher(TextLocalization.ModName);
        }else {
            tt.addMachineType(Tooltip_VendingMachines_MachineType)
                .addInfo(Tooltip_VendingMachines_Controller)
                .addInfo(Disable_loading)
                .addInfo(Tooltip_VendingMachines_00)
                .addInfo(Tooltip_VendingMachines_01)
                .addInfo(Tooltip_VendingMachines_02)
                .addInfo(Tooltip_VendingMachines_03)
                .addSeparator()
                .addInputBus(add_InputBus,1)
                .addOutputBus(add_OutputBus,1)
                .addInfo(TextLocalization.StructureTooComplex)
                .addInfo(TextLocalization.BLUE_PRINT_INFO)
                .toolTipFinisher(TextLocalization.ModName);
        }
        return tt;
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip,
                             IWailaDataAccessor accessor, IWailaConfigHandler config) {
        // 1. 先让父类链完整执行，填好所有基础信息 + 两层无线信息
        super.getWailaBody(itemStack, currentTip, accessor, config);

        final NBTTagCompound tag = accessor.getNBTData();

        // 2. 仅在无线模式下清理多余的 4 行
        if (tag.getBoolean("wirelessMode")) {
            // 为了兼容颜色码，用去色后的字符串来判断前缀
            String wirelessText   = TextLocalization.Waila_WirelessMode;   // “无线模式”
            String euCostText     = TextLocalization.Waila_CurrentEuCost;  // “当前EU消耗”
            String orundumCostKey = "Current Orundum Cost";                 // 父类里写死的英文前缀

            currentTip.removeIf(raw -> {
                String line = EnumChatFormatting.getTextWithoutFormattingCodes(raw);
                if (line == null) line = raw;
                return line.startsWith(wirelessText)
                    || line.startsWith(euCostText)
                    || line.startsWith(orundumCostKey);
            });
        }

        // 3. 追加你要显示的 Orundum 输出信息
        BigInteger orundumPerTick = ORUNDUM_PER_PURE_ORIGINIUM
            .divide(BigInteger.valueOf(TICKS_PER_CYCLE));
        BigInteger orundumPerSecond = orundumPerTick.multiply(BigInteger.valueOf(20L));

        currentTip.add(
            EnumChatFormatting.AQUA + "当前 Orundum 输出"
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + orundumPerTick.toString()
                + EnumChatFormatting.RESET + " Orundum/t"
        );
        currentTip.add(
            EnumChatFormatting.AQUA + "折算"
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + orundumPerSecond.toString()
                + EnumChatFormatting.RESET + " Orundum/s"
        );
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_OrundumDynamo(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        if (side == aFacing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureId()),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureId()),
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
        return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(getCasingTextureId()) };
    }

    protected int getCasingTextureId() {
        return getTextureIndex();
    }

    public int getTextureIndex() {
        return TAE.getIndexFromPage(2, 2);
    }
}
