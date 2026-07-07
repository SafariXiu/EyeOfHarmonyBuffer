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
import static gregtech.api.enums.HatchElement.OutputBus;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;
import static gregtech.api.util.GTStructureUtility.*;
import static gregtech.api.util.GTStructureUtility.ofCoil;

public class EOHB_RefiningFurnace extends OrundumWirelessMultiMachineBase<EOHB_RefiningFurnace>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_RefiningFurnace> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainRefiningFurnace";
    private static final int OffsetsX = 3;
    private static final int OffsetsY = 7;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;
    private HeatingCoilLevel mCoilLevel;
    private int glassTier = -1;

    public EOHB_RefiningFurnace(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_RefiningFurnace(String aName) {
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
        return RecipeMaps.RefiningFurnace;
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
        {"       "," EEEE  ","CEAAEE ","CEAAEE "," EEEE  ","       ","       "},
        {"       "," EAAE  ","CEDDEE ","CEDDEE "," EEEE  ","       ","       "},
        {"       "," EAAE  ","CEDDEE ","CE  EE "," EEEE  ","       ","       "},
        {"       "," EAAE  ","CEDDEE ","CE  EE "," EEEE  ","       ","       "},
        {"       "," EAAE  ","CEDDEE ","CE  EE "," EEEE  ","       ","       "},
        {"       ","FEAAEFF","FEDDEEF","FE  EEF","FEEEE F","FFFFFFF","       "},
        {"     CC","FEEEECC","CEEEEEC","CEEEEEC","CEEEE C","FCCCCCF","       "},
        {"BBB~BBB","BBBBBBB","BBBBBBB","BBBBBBB","BBBBBBB","BBBBBBB","BBBBBBB"}
    };

    @Override
    public IStructureDefinition<EOHB_RefiningFurnace> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_RefiningFurnace>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    chainAllGlasses(-1, (te, t) -> te.glassTier = t, te -> te.glassTier)
                )
                .addElement(
                    'B',
                    buildHatchAdder(EOHB_RefiningFurnace.class)
                        .atLeast(InputBus,InputHatch,OutputHatch,OutputBus)
                        .casingIndex(CASING_INDEX)
                        .hint(1)
                        .buildAndChain(
                            ofBlock(sBlockCasings2,0)
                        )
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'D',
                    GTStructureChannels.HEATING_COIL
                        .use(activeCoils(ofCoil(EOHB_RefiningFurnace::setCoilLevel, EOHB_RefiningFurnace::getCoilLevel)))
                )
                .addElement(
                    'E',
                    ofBlock(
                        sBlockCasings8, 7
                    )
                )
                .addElement(
                    'F',
                    ofBlock(
                        sBlockFrames, 305
                    )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    public void setCoilLevel(HeatingCoilLevel aCoilLevel) {
        this.mCoilLevel = aCoilLevel;
    }

    public HeatingCoilLevel getCoilLevel() {
        return this.mCoilLevel;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_RefiningFurnace_MachineType)
            .addInfo(Tooltip_RefiningFurnace_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_RefiningFurnace_00)
            .addInfo(Tooltip_RefiningFurnace_01)
            .addInfo(Tooltip_RefiningFurnace_02)
            .addInfo(Tooltip_RefiningFurnace_03)
            .addInfo(Tooltip_RefiningFurnace_04)
            .addInfo(Tooltip_RefiningFurnace_05)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addInputHatch("1+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_1)
            .addOutputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag,
                                World world, int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        tag.setInteger("RefiningFurnaceGlassTier", this.glassTier);
        tag.setInteger("RefiningFurnaceParallel", getMaxParallelRecipes());

        int wirelessTime = getWirelessModeProcessingTime();
        tag.setInteger("RefiningFurnaceWirelessTime", wirelessTime);

        final int planterBaseTime = 200;
        float reductionPct = Math.max(0.0f,
            (planterBaseTime - wirelessTime) * 100.0f / planterBaseTime);
        tag.setInteger("RefiningFurnaceBaseTime", planterBaseTime);
        tag.setFloat("RefiningFurnaceTimeReductionPct", reductionPct);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);

        NBTTagCompound tag = accessor.getNBTData();
        if (tag == null) return;

        int glassTierShown = tag.getInteger("RefiningFurnaceGlassTier");
        int parallelShown = tag.getInteger("RefiningFurnaceParallel");
        int wirelessTimeShown = tag.getInteger("RefiningFurnaceWirelessTime");
        float reductionPct = tag.getFloat("RefiningFurnaceTimeReductionPct");
        int baseTime = tag.getInteger("RefiningFurnaceBaseTime");

        currentTip.add(EnumChatFormatting.DARK_AQUA + "【精炼炉状态】");
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
        return new EOHB_RefiningFurnace(this.mName);
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
                                 int aColorIndex, boolean aActive, boolean aRedstone) {
        if (side == facing) {
            if (aActive) return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE)
                    .extFacing()
                    .build(),
                TextureFactory.builder()
                    .addIcon(OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW)
                    .extFacing()
                    .glow()
                    .build() };
            return new ITexture[] { Textures.BlockIcons.getCasingTextureForId(CASING_INDEX), TextureFactory.builder()
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
