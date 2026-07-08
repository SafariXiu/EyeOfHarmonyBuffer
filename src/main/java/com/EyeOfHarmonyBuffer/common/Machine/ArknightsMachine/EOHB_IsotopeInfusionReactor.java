package com.EyeOfHarmonyBuffer.common.Machine.ArknightsMachine;

import gregtech.api.GregTechAPI;
import gregtech.common.blocks.BlockCasings10;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;
import bartworks.common.loaders.ItemRegistry;
import com.EyeOfHarmonyBuffer.Recipe.RecipeMaps;
import com.EyeOfHarmonyBuffer.common.multiMachineClasses.OrundumWirelessMultiMachineBase;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.*;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.ICasingTextureProvider;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.recipe.RecipeMap;
import gregtech.api.structure.error.StructureError;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gtPlusPlus.core.material.Material;
import gtPlusPlus.core.material.MaterialsElements;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.BLUE_PRINT_INFO;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_Energy;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_Arknights_Project_UpgradeCard;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_1;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_MachineType_2;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.ModName;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.StructureTooComplex;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.GregTechAPI.*;
import static gregtech.api.enums.HatchElement.*;
import static gregtech.api.enums.Textures.BlockIcons.*;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_IsotopeInfusionReactor extends OrundumWirelessMultiMachineBase<EOHB_IsotopeInfusionReactor>
    implements IConstructable, ISurvivalConstructable, ICasingTextureProvider {

    private static IStructureDefinition<EOHB_IsotopeInfusionReactor> STRUCTURE_DEFINITION = null;
    private static final String STRUCTURE_PIECE_MAIN = "mainIsotopeInfusionReactor";
    private static final int OffsetsX = 11;
    private static final int OffsetsY = 8;
    private static final int OffsetsZ = 2;

    private int mUpgradeTier = -1;

    public EOHB_IsotopeInfusionReactor(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
        setWirelessCycleNum(1);
    }

    public EOHB_IsotopeInfusionReactor(String aName) {
        super(aName);
        setWirelessCycleNum(1);
    }

    private static final class UpgradeEntry {
        final Material material;
        final Block block;
        final int meta;
        final int tier;

        UpgradeEntry(Material material) {
            this.material = material;
            this.block = blockOf(material);
            this.meta = metaOf(material);
            this.tier = material.vRadiationLevel;
        }
    }

    private static final UpgradeEntry[] UPGRADE_ENTRIES;

    static {
        MaterialsElements elems = MaterialsElements.getInstance();
        UPGRADE_ENTRIES = new UpgradeEntry[] {
            new UpgradeEntry(elems.PLUTONIUM238),
            new UpgradeEntry(elems.URANIUM233),
            new UpgradeEntry(elems.URANIUM232),
            new UpgradeEntry(elems.FERMIUM),
            new UpgradeEntry(elems.NEPTUNIUM),
            new UpgradeEntry(elems.CURIUM),
            new UpgradeEntry(elems.PROTACTINIUM),
            new UpgradeEntry(elems.RADIUM),
            new UpgradeEntry(elems.POLONIUM),
            new UpgradeEntry(elems.TECHNETIUM)
        };
    }

    @SuppressWarnings("unchecked")
    private static IStructureElement<EOHB_IsotopeInfusionReactor> createUpgradeElement() {

        ITierConverter<Integer> tierConverter = new ITierConverter<Integer>() {
            @Override
            public Integer convert(Block b, int meta) {
                for (UpgradeEntry e : UPGRADE_ENTRIES) {
                    if (e.block == b && e.meta == meta) {
                        return e.tier;
                    }
                }
                return null;
            }
        };

        List<Pair<Block, Integer>> knownTiers = new java.util.ArrayList<Pair<Block, Integer>>();
        for (UpgradeEntry e : UPGRADE_ENTRIES) {
            knownTiers.add(Pair.of(e.block, e.meta));
        }

        return StructureUtility.ofBlocksTiered(
            tierConverter,
            knownTiers,
            -1,
            new BiConsumer<EOHB_IsotopeInfusionReactor, Integer>() {
                @Override
                public void accept(EOHB_IsotopeInfusionReactor t, Integer tier) {
                    t.mUpgradeTier = tier;
                }
            },
            new Function<EOHB_IsotopeInfusionReactor, Integer>() {
                @Override
                public Integer apply(EOHB_IsotopeInfusionReactor t) {
                    return t.mUpgradeTier;
                }
            }
        );
    }

    private int getEffectiveTierCapped() {
        return Math.max(0, mUpgradeTier);
    }

    @Override
    public int getWirelessModeProcessingTime() {
        int tier = getEffectiveTierCapped();

        if (tier <= 0) {
            return 800;
        }
        if (tier >= 5) {
            return 20;
        }

        double factorPerTier = Math.pow(20.0 / 800.0, 1.0 / 5.0);

        double time = 800.0 * Math.pow(factorPerTier, tier);

        return (int) Math.max(20, Math.round(time));
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
        int tier = getEffectiveTierCapped();

        if (tier <= 0) return 4;

        return (int) Math.pow(4, tier + 1);
    }

    @NotNull
    @Override
    public RecipeMap<?> getRecipeMap() {
        return RecipeMaps.IsotopeInfusionReactor;
    }

    @Override
    public void checkMachine(IGregTechTileEntity aBaseMetaTileEntity,
                             ItemStack aStack,
                             List<StructureError> errors) {

        this.mUpgradeTier = -1;

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
        {"       NNNNNNNNN         ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","      NNNNNNNNNNN        ","       NNNNNNNNN         "},
        {"                         ","       K       K         ","                         ","                         ","          HHH            ","          HHH            ","          HHH            ","                         ","                         ","       K       K         ","                         "},
        {"                         ","       K       K KKKKK   ","         BBBBB   K   K   ","        BBBBBBB  K   K   ","        BBBBBBB  K   K   ","        BBBBBBB  K   K   ","        BBBBBBB  K   K   ","        BBBBBBB  KKKKK   ","         BBBBB           ","       K       K         ","                         "},
        {"                         ","       K       K BBBBB   ","        BBAAABB  BBBBB   ","        B     B  BBBBB   ","        B E E B  BOOOB   ","        B     B  BOOOB   ","        B E E B  BOOOB   ","        B     B  BBBBB   ","        BBBBBBB          ","       K       K         ","                         "},
        {"                         ","       K       K         ","        BBAAABB          ","KKKKK   B     B  BBBBB   ","K   K   B E E B  B   B   ","K   K   B     B  B   B   ","K   K   B E E B  B   B   ","KKKKK   B     B  BBBBB   ","        BBBBBBB          ","       K       K         ","                         "},
        {"                         ","       K       K         ","        BBAAABB          ","BBBBB   B     B  BBBBB   ","BBBBB   B E E B  B   B   ","BBBBB   B     B  B   B   ","BBBBB   B E E B  B   B   ","BBBBB   B     B  BBBBB   ","        BBBBBBB          ","       K       K         ","                         "},
        {"                         ","       K       K         ","        BBBBBBB          ","BBBBB   B     B  BAAAB   ","B   B   B E E B  AJJJA   ","B C B   B     B  AJJJA   ","B   B   B E E B  AJJJA   ","BBBBB   B     B  BAAAB   ","        BBBBBBB          ","       K       K         ","                         "},
        {"                KKKKKKKKK","       K       KK       K","         BFFFB  K       K","BBIBB   BBDBDBB KBBBBB   ","B   FLFLFDBBBDGAAG   B   ","I C FLFLFBBBBBGAAG   B   ","B   FLFLFDBBBDGAAG   B   ","BBIBB   BBDBDBB  BBBBB   ","         BFFFB           ","       K       K         ","                         "},
        {"                         ","       K       K BBBBBBB ","          F~F    BBBBBBBK","BBBBB     DKD    BBBBB  K","B C FLFLFDKKKDGAAG   B   ","BCMC    FKKKKKGDDG   B   ","B C FLFLFDKKKDGAAG   B   ","BBBBB     DKD    BBBBB   ","          FFF            ","       K       K         ","                         "},
        {"                         ","       K       K         ","         BFFFB           ","BBIBB   BBDBDBB  BBBBBBBK","B   FLFLFDBBBDGAAG   B  K","I C FLFLFBBBBBGAAG   B   ","B   FLFLFDBBBDGAAG   B   ","BBIBB   BBDBDBB  BBBBB   ","         BFFFB           ","       K       K         ","                         "},
        {"                         ","       K  BBB  K         ","        BBBBBBB          ","BBBBB   B     B  BAAAB   ","B   B   B     B  AJJJABBK","B C B   B     B  AJ JA  K","B   B   B     B  AJJJA   ","BBBBB   B     B  BAAAB   ","        BBBBBBB          ","       K       K         ","                         "},
        {"          BBB            ","       K  BBB  K         ","         BBBBB           ","BBBBB   BBBBBBB  BBBBB   ","BBBBB   BBBBBBB  BBBBB   ","BBBBB   BBBBBBB  BBBBBBBK","BBBBB   BBBBBBB  BBBBB  K","BBBBB   BBBBBBB  BBBBB   ","         BBBBB           ","       K       K         ","                         "}
    };

    @Override
    public IStructureDefinition<EOHB_IsotopeInfusionReactor> getStructureDefinition() {
        if(STRUCTURE_DEFINITION == null){
            STRUCTURE_DEFINITION = StructureDefinition.<EOHB_IsotopeInfusionReactor>builder()
                .addShape(
                    STRUCTURE_PIECE_MAIN,transpose(shapeMain)
                )
                .addElement(
                    'A',
                    ofBlock(ItemRegistry.bw_realglas, 0)
                )
                .addElement(
                    'B',
                    ofBlock(sBlockCasings10, 3)
                )
                .addElement(
                    'C',
                    ofBlock(sBlockCasings13, 1)
                )
                .addElement(
                    'D',
                    ofBlock(sBlockCasings13, 2)
                )
                .addElement(
                    'E',
                    ofBlock(sBlockCasings13, 4)
                )
                .addElement(
                    'F',
                    ofBlock(sBlockCasings2, 6)
                )
                .addElement(
                    'G',
                    ofBlock(sBlockCasings2, 10)
                )
                .addElement(
                    'H',
                    ofBlock(sBlockCasings2, 11)
                )
                .addElement(
                    'I',
                    ofBlock(sBlockCasings3, 2)
                )
                .addElement(
                    'J',
                    ofBlock(sBlockCasings5, 0)
                )
                .addElement(
                    'K',
                    ofBlock(sBlockFrames, 81)
                )
                .addElement(
                    'L',
                    ofBlock(sBlockGlass1, 1)
                )
                .addElement(
                    'M',
                    createUpgradeElement()
                )
                .addElement(
                    'N',
                    buildHatchAdder(EOHB_IsotopeInfusionReactor.class)
                        .atLeast(OutputBus, OutputHatch)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(1)
                        .buildAndChain(
                            sBlockCasings10, 3
                        )
                )
                .addElement(
                    'O',
                    buildHatchAdder(EOHB_IsotopeInfusionReactor.class)
                        .atLeast(InputBus, InputHatch)
                        .casingIndex(((BlockCasings10) GregTechAPI.sBlockCasings10).getTextureIndex(3))
                        .hint(2)
                        .buildAndChain(
                            sBlockCasings10, 3
                        )
                )
                .build();
        }
        return STRUCTURE_DEFINITION;
    }

    private static Block blockOf(Material m) {
        ItemStack stack = m.getBlock(1);
        return Block.getBlockFromItem(stack.getItem());
    }

    private static int metaOf(Material m) {
        return m.getBlock(1).getItemDamage();
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_IsotopeInfusionReactor_MachineType)
            .addInfo(Tooltip_IsotopeInfusionReactor_Controller)
            .addInfo(EOHB_Arknights_Project)
            .addInfo(Tooltip_IsotopeInfusionReactor_00)
            .addInfo(Tooltip_IsotopeInfusionReactor_01)
            .addInfo(Tooltip_IsotopeInfusionReactor_02)
            .addInfo(Tooltip_IsotopeInfusionReactor_03)
            .addInfo(Tooltip_IsotopeInfusionReactor_04)
            .addInfo(Tooltip_IsotopeInfusionReactor_05)
            .addInfo(Tooltip_IsotopeInfusionReactor_06)
            .addInfo(EOHB_Arknights_Project_UpgradeCard)
            .addInfo(EOHB_Arknights_Project_Energy)
            .addSeparator()
            .addInfo(StructureTooComplex)
            .addInfo(BLUE_PRINT_INFO)
            .addInputBus("1+", EOHB_MachineType_2)
            .addInputHatch("1+", EOHB_MachineType_2)
            .addOutputBus("1+", EOHB_MachineType_1)
            .addOutputHatch("1+", EOHB_MachineType_1)
            .toolTipFinisher(ModName);
        return tt;
    }

    private static String getUpgradeMaterialNameForTier(int tier) {
        if (tier <= 0) {
            return EOHB_Waila_None;
        }

        for (UpgradeEntry e : UPGRADE_ENTRIES) {
            if (e.tier == tier) {
                return e.material.getDefaultLocalName();
            }
        }

        return EOHB_Waila_None;
    }

    @Override
    public void getWailaNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag,
                                World world, int x, int y, int z) {
        super.getWailaNBTData(player, tile, tag, world, x, y, z);

        int effectiveTier = getEffectiveTierCapped();
        tag.setInteger("upgradeTier", effectiveTier);

        tag.setInteger("wirelessRunTime", getWirelessModeProcessingTime());
    }

    @Override
    public void getWailaBody(ItemStack itemStack, List<String> currentTip,
                             IWailaDataAccessor accessor, IWailaConfigHandler config) {

        super.getWailaBody(itemStack, currentTip, accessor, config);

        final NBTTagCompound tag = accessor.getNBTData();

        int runTimeTicks = tag.getInteger("wirelessRunTime");
        String runTimeLabel = StatCollector.translateToLocal(EOHB_Waila_CurrentRunTime);
        currentTip.add(
            EnumChatFormatting.AQUA + runTimeLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + runTimeTicks
                + EnumChatFormatting.RESET + " ticks"
        );

        int tier = tag.getInteger("upgradeTier");

        String blockLabel = StatCollector.translateToLocal(EOHB_Waila_UpgradeBlock);
        String levelLabel = StatCollector.translateToLocal(EOHB_Waila_UpgradeLevel);

        String materialName = getUpgradeMaterialNameForTier(tier);
        int levelToShow = tier > 0 ? tier : 0;

        currentTip.add(
            EnumChatFormatting.AQUA + blockLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + materialName
        );

        currentTip.add(
            EnumChatFormatting.AQUA + levelLabel
                + EnumChatFormatting.RESET + ": "
                + EnumChatFormatting.GOLD + levelToShow
        );
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_IsotopeInfusionReactor(this.mName);
    }

    @Override
    public ITexture getCasingTexture() {
        return getCasingTextureForId(GTUtility.getCasingTextureIndex(sBlockCasings10, 3));
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection aFacing,
                                 int colorIndex, boolean aActive, boolean redstoneLevel) {
        return Textures.BlockIcons.createTextureWithCasing(
            this,
            side,
            aFacing,
            aActive,
            OVERLAY_FRONT_MULTI_AUTOCLAVE,
            OVERLAY_FRONT_MULTI_AUTOCLAVE_GLOW,
            OVERLAY_FRONT_MULTI_AUTOCLAVE_ACTIVE,
            OVERLAY_FRONT_MULTI_AUTOCLAVE_ACTIVE_GLOW);
    }
}
