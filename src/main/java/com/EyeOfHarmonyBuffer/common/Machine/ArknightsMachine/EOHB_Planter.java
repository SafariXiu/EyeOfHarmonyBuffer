package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import gregtech.api.enums.HeatingCoilLevel;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.common.misc.GTStructureChannels;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.*;
import static gregtech.api.util.GTStructureUtility.ofCoil;

public class EOHB_Planter extends OrundumWirelessMultiMachineBase<EOHB_Planter>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_Planter> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainPlanter";
    private static final int OffsetsX = 5;
    private static final int OffsetsY = 6;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX1 = 183;

    private int glassTier = -1;
    private HeatingCoilLevel mCoilLevel = HeatingCoilLevel.None;

    public EOHB_Planter(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_Planter(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        final double MIN_HEAT = 1800.0;
        final double MAX_HEAT = 13500.0;
        final int BASE_TIME = 200;
        final int MIN_TIME = 20;

        HeatingCoilLevel coil = getCoilLevel();
        double heat = coil == null || coil == HeatingCoilLevel.None
            ? MIN_HEAT
            : coil.getHeat();

        double normalized = (heat - MIN_HEAT) / (MAX_HEAT - MIN_HEAT);
        normalized = Math.max(0.0, Math.min(1.0, normalized));

        double eased = normalized * normalized * normalized;
        int time = (int) Math.round(BASE_TIME - (BASE_TIME - MIN_TIME) * eased);

        return Math.max(MIN_TIME, time);
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
        return 1 << Math.max(0, this.glassTier - 3);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.Planter;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {

        this.glassTier = -1;
        this.setCoilLevel(HeatingCoilLevel.None);

        if (!checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors)) {
            return;
        }

        if (this.getCoilLevel() == null || this.getCoilLevel() == HeatingCoilLevel.None) {
            return;
        }
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
        {"           ","           ","           ","EEEEEEEEEEE","           ","           ","           "},
        {"           ","           "," DDDDDDDDD ","EDDDDDDDDDE"," DDDDDDDDD ","           ","           "},
        {"           "," DAAAAAAAD "," DC     CD ","EDC     CDE"," DC     CD "," DAAAAAAAD ","           "},
        {"           "," DAAAAAAAD "," DC     CD ","EBBBBBBBBBE"," DC     CD "," DAAAAAAAD ","           "},
        {"           "," DAAAAAAAD "," DC     CD ","ED       DE"," DC     CD "," DAAAAAAAD ","           "},
        {"           "," DAAAAAAAD "," DC     CD ","ED       DE"," DC     CD "," DAAAAAAAD ","           "},
        {"EEEEE~EEEEE","EDDDDDDDDDE","EDDDDDDDDDE","EDDDDDDDDDE","EDDDDDDDDDE","EDDDDDDDDDE","EEEEEEEEEEE"}
    };

    @Override
    public IStructureDefinition<EOHB_Planter> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_Planter>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('E', ofBlock(sBlockFrames, 305))
                .addElement(
                    'D',
                    buildHatchAdder(EOHB_Planter.class)
                        .atLeast(InputBus, OutputBus, InputHatch)
                        .casingIndex(CASING_INDEX1)
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings8, 7
                        )
                )
                .addElement('A', chainAllGlasses(-1, (te, t) -> te.glassTier = t, te -> te.glassTier))
                .addElement('B', ofBlock(sBlockCasings2, 13))
                .addElement('C', GTStructureChannels.HEATING_COIL
                    .use(activeCoils(ofCoil(
                        EOHB_Planter::setCoilLevel, EOHB_Planter::getCoilLevel))))
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    public void setCoilLevel(HeatingCoilLevel aCoilLevel) {
        this.mCoilLevel = aCoilLevel == null ? HeatingCoilLevel.None : aCoilLevel;
    }

    public HeatingCoilLevel getCoilLevel() {
        return this.mCoilLevel == null ? HeatingCoilLevel.None : this.mCoilLevel;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_Planter_MachineType)
            .addInfo(Tooltip_Planter_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_Planter_00)
            .addInfo(Tooltip_Planter_01)
            .addInfo(Tooltip_Planter_02)
            .addInfo(Tooltip_Planter_03)
            .addInfo(Tooltip_Planter_04)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addMaintenanceHatch(add_MaintenanceHatch)
            .addInputBus(add_InputBus)
            .addInputHatch(add_inputHatch)
            .addOutputBus(add_OutputBus)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag,
                                World world, int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        tag.setInteger("PlanterGlassTier", this.glassTier);
        tag.setInteger("PlanterParallel", getMaxParallelRecipes());

        int wirelessTime = getWirelessModeProcessingTime();
        tag.setInteger("PlanterWirelessTime", wirelessTime);

        final int planterBaseTime = 200;
        float reductionPct = Math.max(0.0f,
            (planterBaseTime - wirelessTime) * 100.0f / planterBaseTime);
        tag.setInteger("PlanterBaseTime", planterBaseTime);
        tag.setFloat("PlanterTimeReductionPct", reductionPct);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);

        NBTTagCompound tag = accessor.getNBTData();
        if (tag == null) return;

        int glassTierShown = tag.getInteger("PlanterGlassTier");
        int parallelShown = tag.getInteger("PlanterParallel");
        int wirelessTimeShown = tag.getInteger("PlanterWirelessTime");
        float reductionPct = tag.getFloat("PlanterTimeReductionPct");
        int baseTime = tag.getInteger("PlanterBaseTime");

        currentTip.add(EnumChatFormatting.DARK_AQUA + "【种植机状态】");
        currentTip.add(EnumChatFormatting.AQUA + "玻璃等级："
            + EnumChatFormatting.GOLD + glassTierShown);
        currentTip.add(EnumChatFormatting.AQUA + "最大并行："
            + EnumChatFormatting.GOLD + parallelShown + " 路");
        currentTip.add(EnumChatFormatting.AQUA + "无线加工时间："
            + EnumChatFormatting.GOLD + wirelessTimeShown + " tick "
            + EnumChatFormatting.GRAY + "(基准 " + baseTime + " tick)");
        currentTip.add(EnumChatFormatting.AQUA + "时间减免："
            + EnumChatFormatting.GREEN + String.format("%.1f%%", reductionPct));
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_Planter(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive)
                return new ITexture[]{
                    Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1),
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
                Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1),
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
        return new ITexture[]{Textures.BlockIcons.getCasingTextureForId(CASING_INDEX1)};
    }
}
