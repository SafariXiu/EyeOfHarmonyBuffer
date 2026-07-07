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

public class EOHB_SeedCollectingMachine extends OrundumWirelessMultiMachineBase<EOHB_SeedCollectingMachine>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_SeedCollectingMachine> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainSeedCollectingMachine";
    private static final int OffsetsX = 5;
    private static final int OffsetsY = 7;
    private static final int OffsetsZ = 0;
    private static final int CASING_INDEX = 16;

    private int glassTier = -1;
    private HeatingCoilLevel mCoilLevel = HeatingCoilLevel.None;

    public EOHB_SeedCollectingMachine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_SeedCollectingMachine(String aName) {
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
        return RecipeMaps.SeedCollectingMachine;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {

        this.glassTier = -1;

        checkPiece(STRUCTURE_PIECE_MAIN, OffsetsX, OffsetsY, OffsetsZ, errors);
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
        {"           ","           ","           ","           ","           "," EEEEEEEEE "," EEEEEEEEE ","           "},
        {"           ","           ","           ","           "," EEEEEEEEE ","EDDDDDDDDDE","EDDDDDDDDDE"," EEEEEEEEE "},
        {"           ","           ","           ","           "," EEEEEEEEE ","EDDDDDDDDDE","EDDDDDDDDDE"," EEEEEEEEE "},
        {"           ","           ","           ","BAAAAAAAAAB","BAAAAAAAAAB","BEEEEEEEEEB","BEEEEEEEEEB","           "},
        {"           ","           ","BAAAAAAAAAB","B         B","BCCCCCCCCCB","B         B","BBBBBBBBBBB","           "},
        {"           ","BAAAAAAAAAB","B         B","BCCCCCCCCCB","B         B","B         B","BCCCCCCCCCB","           "},
        {"BAAAAAAAAAB","B         B","BCCCCCCCCCB","B         B","B         B","B         B","BCCCCCCCCCB","           "},
        {"BBBBB~BBBBB","BEEEEEEEEEB","BEEEEEEEEEB","BEEEEEEEEEB","BEEEEEEEEEB","BEEEEEEEEEB","BBBBBBBBBBB","           "}
    };

    @Override
    public IStructureDefinition<EOHB_SeedCollectingMachine> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_SeedCollectingMachine>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement('A', chainAllGlasses(-1, (te, t) -> te.glassTier = t, te -> te.glassTier))
                .addElement('B',
                    buildHatchAdder(EOHB_SeedCollectingMachine.class)
                    .atLeast(InputBus, OutputBus)
                    .casingIndex(CASING_INDEX)
                    .hint(1)
                    .buildAndChain(
                        sBlockCasings2, 0
                    ))
                .addElement('C', ofBlock(sBlockCasings2, 13))
                .addElement('D', GTStructureChannels.HEATING_COIL
                    .use(activeCoils(ofCoil(
                        EOHB_SeedCollectingMachine::setCoilLevel, EOHB_SeedCollectingMachine::getCoilLevel))))
                .addElement('E', ofBlock(sBlockCasings8, 7))
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
        tt.addMachineType(Tooltip_SeedCollectingMachine_MachineType)
            .addInfo(Tooltip_SeedCollectingMachine_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_SeedCollectingMachine_00)
            .addInfo(Tooltip_SeedCollectingMachine_01)
            .addInfo(Tooltip_SeedCollectingMachine_02)
            .addInfo(Tooltip_SeedCollectingMachine_03)
            .addInfo(Tooltip_SeedCollectingMachine_04)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_1)
            .addOutputBus("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag,
                                World world, int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        tag.setInteger("SeedCollectingGlassTier", this.glassTier);
        tag.setInteger("SeedCollectingParallel", getMaxParallelRecipes());

        int wirelessTime = getWirelessModeProcessingTime();
        tag.setInteger("SeedCollectingWirelessTime", wirelessTime);

        final int planterBaseTime = 200;
        float reductionPct = Math.max(0.0f,
            (planterBaseTime - wirelessTime) * 100.0f / planterBaseTime);
        tag.setInteger("SeedCollectingBaseTime", planterBaseTime);
        tag.setFloat("SeedCollectingTimeReductionPct", reductionPct);
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip, IWailaDataAccessor accessor,
                             IWailaConfigHandler config) {
        super.getWailaBody(itemStack, currentTip, accessor, config);

        NBTTagCompound tag = accessor.getNBTData();
        if (tag == null) return;

        int glassTierShown = tag.getInteger("SeedCollectingGlassTier");
        int parallelShown = tag.getInteger("SeedCollectingParallel");
        int wirelessTimeShown = tag.getInteger("SeedCollectingWirelessTime");
        float reductionPct = tag.getFloat("SeedCollectingTimeReductionPct");
        int baseTime = tag.getInteger("SeedCollectingBaseTime");

        currentTip.add(EnumChatFormatting.DARK_AQUA + "【采种机状态】");
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
        return new EOHB_SeedCollectingMachine(this.mName);
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
