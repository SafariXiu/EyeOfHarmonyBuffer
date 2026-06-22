package com.EyeOfHarmonyBuffer.common.item.itemadders.Arknights.FuelRod;

import java.util.ArrayList;

import gregtech.api.enums.GTValues;
import gregtech.api.recipe.RecipeMaps;
import ic2.api.reactor.IReactor;
import ic2.api.reactor.IReactorComponent;
import ic2.core.init.MainConfig;
import ic2.core.util.ConfigUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.List;

import static com.EyeOfHarmonyBuffer.client.EOHBCreativeTabs.tabMetaItem01;
import static com.EyeOfHarmonyBuffer.utils.TextLocalization.EOHB_FuelRob_NO_Outgrowth;

public class ItemReactorFuelCell extends Item implements IReactorComponent {

    public final int numberOfCells;
    public final float energyPerPulse;
    public final int radiation;
    public final float heatMultiplier;
    public final ItemStack depleted;
    public final boolean isMox;
    public final float moxHeatBonus;
    private final int maxDamageEx;

    public ItemReactorFuelCell(String unloc,
                               int cellCount,
                               int maxDamage,
                               float energy,
                               int radiation,
                               float heatMultiplier,
                               ItemStack depleted,
                               boolean isMox,
                               float moxHeatBonus) {

        this.numberOfCells = cellCount;
        this.energyPerPulse = energy;
        this.radiation = radiation;
        this.heatMultiplier = heatMultiplier;
        this.depleted = depleted;
        this.isMox = isMox;
        this.moxHeatBonus = moxHeatBonus;
        this.maxDamageEx = maxDamage;

        setUnlocalizedName(unloc);
        setMaxStackSize(64);
        setMaxDamage(maxDamage);
        setCreativeTab(tabMetaItem01);

        if (this.energyPerPulse > 0 && this.heatMultiplier > 0) {
            int pulses = this.numberOfCells / 2 + 1;

            float nukePowerMult = 5.0f * ConfigUtil.getFloat(
                MainConfig.get(),
                "balance/energy/generator/nuclear"
            );

            String modelKey = this.isMox
                ? "GT5U.nei.nuclear.model.mox"
                : "GT5U.nei.nuclear.model.uranium";

            String modelText = StatCollector.translateToLocal(modelKey);

            String heatText;
            if (this.numberOfCells == 1) {
                heatText = StatCollector.translateToLocalFormatted(
                    "GT5U.nei.nuclear.heat.0",
                    this.heatMultiplier / 2.0F
                );
            } else {
                heatText = StatCollector.translateToLocalFormatted(
                    "GT5U.nei.nuclear.heat.1",
                    this.heatMultiplier * this.numberOfCells / 2.0F,
                    pulses,
                    pulses + 1
                );
            }

            float totalEu = this.energyPerPulse * this.numberOfCells * pulses * nukePowerMult;
            float euPerPulse = this.energyPerPulse * nukePowerMult;
            String energyText = StatCollector.translateToLocalFormatted(
                "GT5U.nei.nuclear.energy",
                totalEu,
                euPerPulse
            );

            ItemStack neiOut;
            if (this.depleted != null) {
                neiOut = this.depleted;
            } else {
                neiOut = new ItemStack(this);
                neiOut.setStackDisplayName(EOHB_FuelRob_NO_Outgrowth);
            }

            GTValues.RA.stdBuilder()
                .itemInputs(new ItemStack(this))
                .itemOutputs(neiOut)
                .setNEIDesc(
                    modelText,
                    StatCollector.translateToLocalFormatted(
                        "GT5U.nei.nuclear.neutron_pulse",
                        this.numberOfCells
                    ),
                    heatText,
                    energyText
                )
                .duration(0)
                .eut(0)
                .addTo(RecipeMaps.ic2NuclearFakeRecipes);
        }
    }

    private static int triangularNumber(int n) {
        return n * (n + 1) / 2;
    }

    private static int checkPulseable(IReactor reactor, int x, int y,
                                      ItemStack me, int mex, int mey, boolean heatrun) {
        ItemStack other = reactor.getItemAt(x, y);
        if (other != null && other.getItem() instanceof IReactorComponent comp) {
            if (comp.acceptUraniumPulse(reactor, other, me, x, y, mex, mey, heatrun)) {
                return 1;
            }
        }
        return 0;
    }

    private static void checkHeatAcceptor(IReactor reactor, int x, int y,
                                          List<ItemStackCoord> heatAcceptors) {
        ItemStack thing = reactor.getItemAt(x, y);
        if (thing != null && thing.getItem() instanceof IReactorComponent comp
            && comp.canStoreHeat(reactor, thing, x, y)) {
            heatAcceptors.add(new ItemStackCoord(thing, x, y));
        }
    }

    @Override
    public void processChamber(IReactor reactor, ItemStack yourStack,
                               int x, int y, boolean heatrun) {
        if (!reactor.produceEnergy()) {
            return;
        }

        for (int iteration = 0; iteration < this.numberOfCells; iteration++) {
            int pulses = 1 + this.numberOfCells / 2;

            if (!heatrun) {
                for (int i = 0; i < pulses; i++) {
                    acceptUraniumPulse(reactor, yourStack, yourStack, x, y, x, y, false);
                }
                checkPulseable(reactor, x - 1, y, yourStack, x, y, false);
                checkPulseable(reactor, x + 1, y, yourStack, x, y, false);
                checkPulseable(reactor, x, y - 1, yourStack, x, y, false);
                checkPulseable(reactor, x, y + 1, yourStack, x, y, false);
            } else {
                pulses += checkPulseable(reactor, x - 1, y, yourStack, x, y, true)
                    + checkPulseable(reactor, x + 1, y, yourStack, x, y, true)
                    + checkPulseable(reactor, x, y - 1, yourStack, x, y, true)
                    + checkPulseable(reactor, x, y + 1, yourStack, x, y, true);

                int heat = triangularNumber(pulses);
                heat = getFinalHeat(reactor, yourStack, x, y, heat);

                List<ItemStackCoord> heatAcceptors = new ArrayList<>();
                checkHeatAcceptor(reactor, x - 1, y, heatAcceptors);
                checkHeatAcceptor(reactor, x + 1, y, heatAcceptors);
                checkHeatAcceptor(reactor, x, y - 1, heatAcceptors);
                checkHeatAcceptor(reactor, x, y + 1, heatAcceptors);

                heat = Math.round(heat * this.heatMultiplier);

                while (!heatAcceptors.isEmpty() && heat > 0) {
                    int dheat = heat / heatAcceptors.size();
                    heat -= dheat;

                    ItemStackCoord target = heatAcceptors.remove(0);
                    IReactorComponent comp = (IReactorComponent) target.stack.getItem();
                    int unused = comp.alterHeat(
                        reactor, target.stack, target.x, target.y, dheat);
                    heat += unused;
                }

                if (heat > 0) {
                    reactor.addHeat(heat);
                }
            }
        }

        if (yourStack.getItemDamage() >= this.maxDamageEx - 1) {
            if (this.depleted != null) {
                reactor.setItemAt(x, y, this.depleted.copy());
            } else {
                reactor.setItemAt(x, y, null);
            }
        } else if (heatrun) {
            yourStack.setItemDamage(yourStack.getItemDamage() + 1);
        }
    }

    protected int getFinalHeat(IReactor reactor, ItemStack stack,
                               int x, int y, int heat) {
        if (this.isMox && reactor.isFluidCooled()) {
            float breeder = (float) reactor.getHeat() / (float) reactor.getMaxHeat();
            if (breeder > 0.5F) {
                heat *= 2;
            }
        }
        return heat;
    }

    @Override
    public boolean acceptUraniumPulse(IReactor reactor, ItemStack yourStack,
                                      ItemStack pulsingStack,
                                      int youX, int youY,
                                      int pulseX, int pulseY,
                                      boolean heatrun) {
        if (!heatrun) {
            if (this.isMox) {
                float breeder = (float) reactor.getHeat() / (float) reactor.getMaxHeat();
                float outputMul = this.moxHeatBonus * breeder + 1.0F;
                reactor.addOutput(outputMul * this.energyPerPulse);
            } else {
                reactor.addOutput(this.energyPerPulse);
            }
        }
        return true;
    }

    @Override
    public boolean canStoreHeat(IReactor reactor, ItemStack yourStack, int x, int y) {
        return false;
    }

    @Override
    public int getMaxHeat(IReactor reactor, ItemStack yourStack, int x, int y) {
        return 0;
    }

    @Override
    public int getCurrentHeat(IReactor reactor, ItemStack yourStack, int x, int y) {
        return 0;
    }

    @Override
    public int alterHeat(IReactor reactor, ItemStack yourStack, int x, int y, int heat) {
        return heat;
    }

    @Override
    public float influenceExplosion(IReactor reactor, ItemStack yourStack) {
        return 2.0F * this.numberOfCells;
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity,
                         int slotIndex, boolean isCurrentItem) {
        if (!world.isRemote && this.radiation > 0 && entity instanceof EntityLivingBase living) {
        }
    }

    private static class ItemStackCoord {
        public final ItemStack stack;
        public final int x;
        public final int y;

        public ItemStackCoord(ItemStack stack, int x, int y) {
            this.stack = stack;
            this.x = x;
            this.y = y;
        }
    }
}
