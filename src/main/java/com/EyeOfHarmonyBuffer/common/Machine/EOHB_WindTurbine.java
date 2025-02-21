package com.EyeOfHarmonyBuffer.common.Machine;

import com.EyeOfHarmonyBuffer.client.TileEntityWindmillRenderer;
import com.EyeOfHarmonyBuffer.common.Block.BlockRegister;
import com.EyeOfHarmonyBuffer.utils.TextLocalization;
import com.gtnewhorizon.structurelib.alignment.constructable.IConstructable;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import goodgenerator.blocks.tileEntity.base.MTETooltipMultiBlockBaseEM;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEHatch;
import gregtech.api.objects.GTRenderedTexture;
import gregtech.api.recipe.check.CheckRecipeResult;
import gregtech.api.recipe.check.CheckRecipeResultRegistry;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;
import gregtech.api.util.MultiblockTooltipBuilder;
import gregtech.api.util.shutdown.ShutDownReason;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.common.util.ForgeDirection;
import org.jetbrains.annotations.NotNull;
import tectech.thing.casing.TTCasingsContainer;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

import static com.EyeOfHarmonyBuffer.utils.TextLocalization.*;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.*;
import static gregtech.api.GregTechAPI.sBlockCasings3;
import static gregtech.api.GregTechAPI.sBlockReinforced;
import static gregtech.api.enums.Mods.GTPlusPlus;
import static gregtech.api.enums.Mods.IndustrialCraft2;
import static gregtech.api.enums.Textures.BlockIcons.BLOCK_PLASCRETE;
import static gregtech.api.util.GTModHandler.getModItem;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

public class EOHB_WindTurbine extends MTETooltipMultiBlockBaseEM implements IConstructable, ISurvivalConstructable {

    private IStructureDefinition<EOHB_WindTurbine> multiDefinition = null;
    protected long leftEnergy = 0;
    private ItemStack target;
    private static final Map<ItemStack, Integer> ROTOR_VALUES = new HashMap<>();
    private static final long BASE_POWER = 2097152;
    private long trueOutput = 0;
    private double lastWindSpeed = 1.0;
    private long lastUpdateTick = 0;
    private boolean modelCreated = false;

    private final int MODEL_OFFSET_X = -9;
    private final int MODEL_OFFSET_Y = 56;
    private final int MODEL_OFFSET_Z = 0;

    static {
        ROTOR_VALUES.put(getModItem(IndustrialCraft2.ID, "itemwoodrotor", 1), 1);
        ROTOR_VALUES.put(getModItem(IndustrialCraft2.ID, "itemironrotor", 1), 4);
        ROTOR_VALUES.put(getModItem(IndustrialCraft2.ID, "itemsteelrotor", 1), 8);
        ROTOR_VALUES.put(getModItem(IndustrialCraft2.ID, "itemwcarbonrotor", 1), 16);
        ROTOR_VALUES.put(getModItem(GTPlusPlus.ID, "itemwoodrotor", 1), 32);
        ROTOR_VALUES.put(getModItem(GTPlusPlus.ID, "itemironrotor", 1), 64);
        ROTOR_VALUES.put(getModItem(GTPlusPlus.ID, "itemsteelrotor", 1), 128);
        ROTOR_VALUES.put(getModItem(GTPlusPlus.ID, "itemwcarbonrotor", 1), 256);
    }

    public EOHB_WindTurbine(int aID, String aName, String aNameRegional) {
        super(aID, aName, aNameRegional);
    }

    @Override
    public IStructureDefinition<EOHB_WindTurbine> getStructure_EM() {
        if(multiDefinition == null) {
            multiDefinition = StructureDefinition.<EOHB_WindTurbine>builder()
                .addShape(
                    mName,
                    transpose(
                        new String[][]{
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","         DDDDD         ","                       ","         DDDDD         ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        DDDDDDD        ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","          AAA          ","         DDDDD         ","          BBB          ","         D   D         ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        DAADAAD        ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","          AAA          ","         DDDDD         ","          B B          ","         D   D         ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        DAADAAD        ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","          AAA          ","         DDDDD         ","          BBB          ","         D   D         ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        DAADAAD        ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","         DDDDD         ","                       ","         DDDDD         ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        DDDDDDD        ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         DDDDD         ","         DDDDD         ","         DD DD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","         DDDDD         ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","          DDD          ","          D D          ","          DDD          ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CCCCC         ","         CDDDC         ","         CD DC         ","         CDDDC         ","         CCCCC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         DDDDD         ","         DD DD         ","         DDDDD         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","         CDDDC         ","         D   D         ","         D   D         ","         D   D         ","         CDDDC         ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CCCCCCC        ","        CDDDDDC        ","        CD   DC        ","        CD   DC        ","        CD   DC        ","        CDDDDDC        ","        CCCCCCC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        D     D        ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CCCCCCC        ","       CDDDDDDDC       ","       CD     DC       ","       CD     DC       ","       CD     DC       ","       CD     DC       ","       CD     DC       ","       CDDDDDDDC       ","        CCCCCCC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDD DDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","                       ","        CDD DDC        ","       CD     DC       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       D       D       ","       CD     DC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","                       ","                       ","                       ","                       ","        CDDDDDC        ","       CDDDDDDDC       ","      CDDDDDDDDDC      ","      DDDDDDDDDDD      ","      DDDDDDDDDDD      ","      DDDDDDDDDDD      ","      DDDDDDDDDDD      ","      DDDDDDDDDDD      ","      CDDDDDDDDDC      ","       CDDDDDDDC       ","        CDDDDDC        ","                       ","                       ","                       ","                       ","                       ","                       "},
                            {"                       ","                       ","        DDDDDDD        ","      DDDDDDDDDDD      ","     DDDDDDDDDDDDD     ","    DDDDDDDDDDDDDDD    ","   DDDDDDDDDDDDDDDDD   ","   DDDDDDDDDDDDDDDDD   ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","   DDDDDDDDDDDDDDDDD   ","   DDDDDDDDDDDDDDDDD   ","    DDDDDDDDDDDDDDD    ","     DDDDDDDDDDDDD     ","      DDDDDDDDDDD      ","        DDDDDDD        ","                       ","                       "},
                            {"        DDD~DDD        ","      DDDDDDDDDDD      ","    DDDDDDDDDDDDDDD    ","   DDDDDDDDDDDDDDDDD   ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  "," DDDDDDDDDDDDDDDDDDDDD "," DDDDDDDDDDDDDDDDDDDDD ","DDDDDDDDDDDDDDDDDDDDDDD","DDDDDDDDDDDDDDDDDDDDDDD","DDDDDDDDDDDDDDDDDDDDDDD","DDDDDDDDDDDDDDDDDDDDDDD","DDDDDDDDDDDDDDDDDDDDDDD","DDDDDDDDDDDDDDDDDDDDDDD","DDDDDDDDDDDDDDDDDDDDDDD"," DDDDDDDDDDDDDDDDDDDDD "," DDDDDDDDDDDDDDDDDDDDD ","  DDDDDDDDDDDDDDDDDDD  ","  DDDDDDDDDDDDDDDDDDD  ","   DDDDDDDDDDDDDDDDD   ","    DDDDDDDDDDDDDDD    ","      DDDDDDDDDDD      ","        DDDDDDD        "}
                        }
                    )
                )
                .addElement('A',ofBlock(sBlockCasings3,11))
                .addElement('D',
                    ofChain(
                        buildHatchAdder(EOHB_WindTurbine.class)
                            .atLeast(
                                tectech.thing.metaTileEntity.multi.base.TTMultiblockBase.HatchElement.DynamoMulti
                                    .or(gregtech.api.enums.HatchElement.Dynamo),
                                gregtech.api.enums.HatchElement.Maintenance
                            )
                            .casingIndex(210)
                            .dot(1)
                            .build(),
                        ofBlock(sBlockReinforced,2)
                    ))
                .addElement('B',ofBlock(TTCasingsContainer.sBlockCasingsBA0, 7))
                .addElement('C',ofBlock(GregTechAPI.sBlockFrames,305))
                .build();
        }
        return multiDefinition;
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        final MultiblockTooltipBuilder tt = new MultiblockTooltipBuilder();
        tt.addMachineType(Tooltip_WindTurbine_MachineType)
            .addInfo(Tooltip_WindTurbine_Controller)
            .addInfo(Tooltip_WindTurbine_00)
            .addInfo(Tooltip_WindTurbine_01)
            .addInfo(Tooltip_WindTurbine_02)
            .addInfo(Tooltip_WindTurbine_03)
            .addSeparator()
            .addStructureInfo(StructureInfo_WindTurbine_00)
            .addStructureInfo(EOHB_Text_SeparatingLine)
            .addStructureInfo(StructureInfo_WindTurbine_01)
            .addStructureInfo(StructureInfo_WindTurbine_02)
            .addStructureInfo(StructureInfo_WindTurbine_03)
            .addStructureInfo(StructureInfo_WindTurbine_04)
            .addStructureInfo(StructureInfo_WindTurbine_05)
            .addStructureInfo(StructureInfo_WindTurbine_06)
            .addStructureInfo(StructureInfo_WindTurbine_07)
            .addStructureInfo(StructureInfo_WindTurbine_08)
            .addStructureInfo(EOHB_Text_SeparatingLine)
            .addStructureInfo(StructureInfo_WindTurbine_09)
            .addInfo(TextLocalization.StructureTooComplex)
            .addInfo(TextLocalization.BLUE_PRINT_INFO)
            .toolTipFinisher(TextLocalization.ModName);
        return tt;
    }

    @Override
    public void construct(ItemStack itemStack, boolean hintsOnly) {
        structureBuild_EM(mName, 11, 59, 0, itemStack, hintsOnly);
    }

    @Override
    public boolean checkMachine_EM(IGregTechTileEntity aBaseMetaTileEntity, ItemStack aStack) {
        return structureCheck_EM(mName, 11, 59, 0)
            && mDynamoHatches.size() + eDynamoMulti.size() == 1;
    }

    @Override
    public int survivalConstruct(ItemStack stackSize, int elementBudget, ISurvivalBuildEnvironment env) {
        if (mMachine) return -1;
        return survivialBuildPiece(mName, stackSize, 11, 59, 0, elementBudget, env, false, true);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        return new EOHB_WindTurbine(this.mName);
    }

    public EOHB_WindTurbine(String name){
        super(name);
    }

    private double getWindSpeedFactor() {
        long currentTick = this.getBaseMetaTileEntity().getWorld().getWorldTime();
        if (currentTick - lastUpdateTick >= 600) {
            lastWindSpeed = 0.5 + Math.random();
            lastUpdateTick = currentTick;
        }
        return lastWindSpeed;
    }

    private int checkRotor(ItemStack inventory) {
        target = this.getControllerSlot();
        if (target == null) return 0;

        for (Map.Entry<ItemStack, Integer> entry : ROTOR_VALUES.entrySet()) {
            if (inventory.getItem() == entry.getKey().getItem()) {
                return entry.getValue();
            }
        }

        return 0;
    }

    @Override
    public @NotNull CheckRecipeResult checkProcessing_EM() {
        int rotorLevel = this.checkRotor(this.getControllerSlot());

        if (rotorLevel == 0) {
            return CheckRecipeResultRegistry.NO_RECIPE;
        }

        this.mMaxProgresstime = 20;

        return CheckRecipeResultRegistry.SUCCESSFUL;

    }

    @Override
    public boolean onRunningTick(ItemStack stack) {
        if (this.getBaseMetaTileEntity().isServerSide()) {
            if (mMaxProgresstime != 0 && mProgresstime % 20 == 0) {
                int rotorLevel = this.checkRotor(this.getControllerSlot());
                if (rotorLevel > 0) {
                    double windSpeedFactor = getWindSpeedFactor();
                    this.mEUt = (int) (BASE_POWER * rotorLevel * windSpeedFactor);
                    this.trueOutput = this.mEUt;
                } else {
                    this.mEUt = 0;
                    this.trueOutput = 0;
                }
            }
            addAutoEnergy();

            if (this.trueOutput > 0) {
                if (!modelCreated) {
                    createRenderBlock();
                }
            } else {
                if (modelCreated) {
                    destroyRenderBlock();
                }
            }
        }
        return true;
    }

    public void addAutoEnergy() {
        long outputPower = this.trueOutput;
        if (!this.mDynamoHatches.isEmpty()) {
            for (MTEHatch tHatch : this.mDynamoHatches) {
                long voltage = tHatch.maxEUOutput();
                long outputAmperes;

                if (outputPower >= voltage) {
                    leftEnergy += outputPower;
                    outputAmperes = leftEnergy / voltage;
                    leftEnergy -= outputAmperes * voltage;
                    addEnergyOutput_EM(voltage, outputAmperes);
                } else {
                    addEnergyOutput_EM(outputPower, 1);
                }
            }
        }
        if(!this.eDynamoMulti.isEmpty()) {
            for (MTEHatch tHatch : this.eDynamoMulti) {
                long voltage = tHatch.maxEUOutput();
                long outputAmperes;

                if (outputPower >= voltage) {
                    leftEnergy += outputPower;
                    outputAmperes = leftEnergy / voltage;
                    leftEnergy -= outputAmperes * voltage;
                    addEnergyOutput_EM(voltage, outputAmperes);
                } else {
                    addEnergyOutput_EM(outputPower, 1);
                }
            }
        }
    }

    @Override
    public String[] getInfoData() {
        String[] info = super.getInfoData();
        info[4] = "Currently generates: " + EnumChatFormatting.RED
            + GTUtility.formatNumbers(Math.abs(this.trueOutput))
            + EnumChatFormatting.RESET
            + " EU/t";
        info[6] = "Problems: " + EnumChatFormatting.RED
            + (this.getIdealStatus() - this.getRepairStatus())
            + EnumChatFormatting.RESET
            + " Coefficient Of Wind Power: "
            + EnumChatFormatting.YELLOW
            + lastWindSpeed
            + EnumChatFormatting.RESET
            + " ";
        return info;
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        this.leftEnergy = aNBT.getLong("mLeftEnergy");
        this.trueOutput = aNBT.getInteger("mbasicOutput");
        super.loadNBTData(aNBT);
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {
        aNBT.setLong("mLeftEnergy", this.leftEnergy);
        aNBT.setLong("mbasicOutput", this.trueOutput);
        super.saveNBTData(aNBT);
    }

    @Override
    public int getMaxEfficiency(ItemStack aStack) { return 0; }

    @Override
    public int getPollutionPerTick(ItemStack aStack) { return 0; }

    @Override
    public int getDamageToComponent(ItemStack aStack) { return 0; }

    @Override
    public boolean explodesOnComponentBreak(ItemStack aStack) { return false; }

    @Override
    public boolean isCorrectMachinePart(ItemStack aStack) {
        return true;
    }

    @Override
    @SuppressWarnings("ALL")
    public ITexture[] getTexture(
        IGregTechTileEntity aBaseMetaTileEntity,
        ForgeDirection side,
        ForgeDirection facing,
        int colorIndex,
        boolean aActive,
        boolean aRedstone
    ){
        if(side == facing){
            if (aActive) return new ITexture[]{
                TextureFactory.of(BLOCK_PLASCRETE),
                new GTRenderedTexture(Textures.BlockIcons.NAQUADAH_REACTOR_SOLID_FRONT_ACTIVE), TextureFactory.builder()
                .addIcon(Textures.BlockIcons.NAQUADAH_REACTOR_SOLID_FRONT_ACTIVE_GLOW)
                .glow()
                .build()
            };
            else return new ITexture[]{
                TextureFactory.of(BLOCK_PLASCRETE),
                new GTRenderedTexture(Textures.BlockIcons.NAQUADAH_REACTOR_SOLID_FRONT)
            };
        }
        return new ITexture[]{TextureFactory.of(BLOCK_PLASCRETE)};
    }

    private int getFacingMeta() {
        ForgeDirection facing = getBaseMetaTileEntity().getFrontFacing();
        switch (facing) {
            case SOUTH: return 0; // 南
            case WEST: return 1;  // 西
            case NORTH: return 2; // 北
            case EAST: return 3;  // 东
            default: return 0;
        }
    }

    private void createRenderBlock() {
        IGregTechTileEntity baseTE = getBaseMetaTileEntity();

        double xOffset = 7 * getExtendedFacing().getRelativeBackInWorld().offsetX;
        double zOffset = 7 * getExtendedFacing().getRelativeBackInWorld().offsetZ;
        int x = baseTE.getXCoord() + (int)xOffset;
        int y = baseTE.getYCoord() + MODEL_OFFSET_Y;
        int z = baseTE.getZCoord() + (int)zOffset;

        int meta = getFacingMeta();

        baseTE.getWorld().setBlock(x, y, z, Blocks.air);
        baseTE.getWorld().setBlock(x, y, z, BlockRegister.TrubineBlock, meta, 2);

        modelCreated = true;
    }

    private void destroyRenderBlock() {
        IGregTechTileEntity baseTE = getBaseMetaTileEntity();

        double xOffset = 7 * getExtendedFacing().getRelativeBackInWorld().offsetX;
        double zOffset = 7 * getExtendedFacing().getRelativeBackInWorld().offsetZ;
        int x = baseTE.getXCoord() + (int)xOffset;
        int y = baseTE.getYCoord() + MODEL_OFFSET_Y;
        int z = baseTE.getZCoord() + (int)zOffset;

        baseTE.getWorld().setBlock(x, y, z, Blocks.air);
        modelCreated = false;
    }

    @Override
    public void stopMachine(@Nonnull ShutDownReason reason) {
        if (modelCreated) {
            destroyRenderBlock();
        }
        super.stopMachine(reason);
    }

    @Override
    public void onBlockDestroyed() {
        destroyRenderBlock();
        super.onBlockDestroyed();
    }
}
