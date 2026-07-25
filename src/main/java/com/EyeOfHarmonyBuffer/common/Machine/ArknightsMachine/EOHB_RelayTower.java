package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import com.EyeOfHarmonyBuffer.common.misc.LinkNodeEntry;
import com.EyeOfHarmonyBuffer.common.misc.OrundumEnergyService;
import com.EyeOfHarmonyBuffer.common.misc.OrundumLinkNetworkData;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumFieldHelper;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.gtnewhorizons.angelica.shadow.javax.annotation.Nonnull;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.MultiblockTooltipBuilder;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import java.util.List;
import java.util.UUID;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.enums.Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW;

public class EOHB_RelayTower extends OrundumWirelessMultiMachineBase<EOHB_RelayTower>
    implements IConstructable, ISurvivalConstructable {

    private static IStructureDefinition<EOHB_RelayTower> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainRelayTower";
    private static final int OffsetsX = 4;
    private static final int OffsetsY = 36;
    private static final int OffsetsZ = 2;
    private static final int CASING_INDEX1 = 183;

    private static final int RELAY_FIELD_RADIUS_CHUNKS = 0;

    public EOHB_RelayTower(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_RelayTower(String aName) {
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
        return 0;
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
    protected boolean shouldShowWirelessWaila(NBTTagCompound tag) {
        return false;
    }

    @Override
    protected LinkNodeEntry.NodeType getOrundumLinkNodeType() {
        return LinkNodeEntry.NodeType.REPEATER;
    }

    @Override
    protected boolean isPhysicalOnlineForOrundumLink() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        return mMachine && base != null && base.isAllowedToWork();
    }

    private void ensureRelayFieldState(boolean shouldHaveField) {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        World world = base.getWorld();
        if (world == null || world.isRemote) return;

        UUID owner = getOwnerUUID();
        if (owner == null) return;

        UUID teamId = OrundumEnergyService.getTeamIdForUser(owner);
        if (teamId == null) {
            teamId = owner;
        }

        int dimId = world.provider.dimensionId;
        int chunkX = base.getXCoord() >> 4;
        int chunkZ = base.getZCoord() >> 4;

        boolean fieldCurrentlyActive = OrundumFieldHelper.isFieldActiveAt(
            dimId, chunkX, chunkZ, teamId
        );

        if (shouldHaveField && !fieldCurrentlyActive) {
            OrundumFieldHelper.activateFieldWithRadius(
                dimId, chunkX, chunkZ, teamId, RELAY_FIELD_RADIUS_CHUNKS
            );
        } else if (!shouldHaveField && fieldCurrentlyActive) {
            OrundumFieldHelper.deactivateFieldWithRadius(
                dimId, chunkX, chunkZ, teamId, RELAY_FIELD_RADIUS_CHUNKS
            );
        }
    }

    @Nonnull
    @Override
    public CheckRecipeResult checkProcessing() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (!mMachine || base == null || !base.isAllowedToWork()) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        mEfficiency = 10000;
        mEfficiencyIncrease = 0;
        mMaxProgresstime = getWirelessModeProcessingTime();
        mProgresstime = 0;

        mEUt = 0;
        mOutputItems = null;
        mOutputFluids = null;

        return CheckRecipeResultRegistry.SUCCESSFUL;
    }

    @Override
    public void onPostTick(IGregTechTileEntity aBaseMetaTileEntity, long aTick) {
        super.onPostTick(aBaseMetaTileEntity, aTick);

        if (aBaseMetaTileEntity == null || !aBaseMetaTileEntity.isServerSide()) {
            return;
        }

        boolean physicalOnline = isPhysicalOnlineForOrundumLink();
        boolean active = false;

        if (linkNetworkNodeId != null) {
            World world = aBaseMetaTileEntity.getWorld();
            if (world != null) {
                OrundumLinkNetworkData data = OrundumLinkNetworkData.get(world);
                if (data != null) {
                    active = data.isNodeNetworkActive(linkNetworkNodeId);
                }
            }
        }

        boolean shouldHaveField = physicalOnline && active;
        ensureRelayFieldState(shouldHaveField);
    }

    private void forceDeactivateRelayField() {
        IGregTechTileEntity base = getBaseMetaTileEntity();
        if (base == null) return;
        if (!base.isServerSide()) return;

        World world = base.getWorld();
        if (world == null) return;

        UUID owner = getOwnerUUID();
        if (owner == null) return;

        UUID teamId = OrundumEnergyService.getTeamIdForUser(owner);
        if (teamId == null) {
            teamId = owner;
        }

        int dimId = world.provider.dimensionId;
        int chunkX = base.getXCoord() >> 4;
        int chunkZ = base.getZCoord() >> 4;

        OrundumFieldHelper.deactivateFieldWithRadius(
            dimId, chunkX, chunkZ, teamId, RELAY_FIELD_RADIUS_CHUNKS
        );
    }

    @Override
    public void onRemoval() {
        forceDeactivateRelayField();
        super.onRemoval();
    }

    @Override
    public void onUnload() {
        forceDeactivateRelayField();
        super.onUnload();
    }

    @Override
    public boolean onRunningTick(ItemStack aStack) {
        return true;
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
        {"         ","         ","         ","   CCC   ","   CCC   ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","   BBB   ","   BBB   ","   BBB   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","   CCC   ","   CCC   ","         ","         ","         "},
        {"         ","         ","         ","   CAC   ","   CAC   ","   CAC   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   C C   ","   C C   ","   C C   ","         ","         ","         "},
        {"         ","         ","         ","   CCC   ","   C C   ","   CCC   ","         ","         ","         "},
        {"         ","         ","   CCC   ","  CCCCC  ","  CC CC  ","  CCCCC  ","   CCC   ","         ","         "},
        {"         ","         ","   CCC   ","  C   C  ","  C   C  ","  C   C  ","   CCC   ","         ","         "},
        {"         ","         ","   CCC   ","  C   C  ","  C   C  ","  C   C  ","   CCC   ","         ","         "},
        {"         ","         ","   CCC   ","  C   C  ","  C   C  ","  C   C  ","   CCC   ","         ","         "},
        {"         ","         ","         ","   AAA   ","   A A   ","   AAA   ","         ","         ","         "},
        {"         ","   CCC   ","  C   C  "," C AAA C "," C A A C "," C AAA C ","  C   C  ","   CCC   ","         "},
        {"         ","         ","         ","   AAA   ","   A A   ","   AAA   ","         ","         ","         "},
        {"         ","         ","  ACCCA  ","  CCCCC  ","  CC CC  ","  CCCCC  ","  ACCCA  ","         ","         "},
        {"         ","         ","  CC CC  ","  CCCCC  ","   C C   ","  CCCCC  ","  CC CC  ","         ","         "},
        {"         ","         ","  CC CC  ","  CCCCC  ","   C C   ","  CCCCC  ","  CC CC  ","         ","         "},
        {"         "," C     C ","   C C   ","  CCCCC  ","   C C   ","  CCCCC  ","   C C   "," C     C ","         "},
        {"         "," C     C ","   C C   ","  CCCCC  ","   C C   ","  CCCCC  ","   C C   "," C     C ","         "},
        {"         "," C     C ","   C C   ","  CCCCC  ","   C C   ","  CCCCC  ","   C C   "," C     C ","         "},
        {"B       B"," C     C ","  CC~CC  ","  CCCCC  ","  CC CC  ","  CCCCC  ","  CCCCC  "," C     C ","B       B"},
        {"BA     AB","AAAAAAAAA"," AAAAAAA "," AAAAAAA "," AAAAAAA "," AAAAAAA "," AAAAAAA ","AAAAAAAAA","BA     AB"},
        {"B       B","   AAA   ","  AAAAA  "," AAAAAAA "," AAAAAAA "," AAAAAAA ","  AAAAA  ","   AAA   ","B       B"}
    };

    @Override
    public IStructureDefinition<EOHB_RelayTower> getStructureDefinition() {
        if (STRUCTURE_DEFINITION == null) {
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_RelayTower>builder()
                .addShape(STRUCTURE_PIECE_MAIN, transpose(shapeMain))
                .addElement(
                    'A',
                    ofBlock(sBlockCasings2, 0)
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings2, 13)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings8, 7)
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_RelayTower_MachineType)
            .addInfo(Tooltip_RelayTower_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_RelayTower_00)
            .addInfo(Tooltip_RelayTower_01)
            .addInfo(Tooltip_RelayTower_02)
            .addInfo(Tooltip_RelayTower_03)
            .addInfo(Tooltip_RelayTower_04)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .toolTipFinisher(ModName);
        return tt;
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_RelayTower(this.mName);
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
