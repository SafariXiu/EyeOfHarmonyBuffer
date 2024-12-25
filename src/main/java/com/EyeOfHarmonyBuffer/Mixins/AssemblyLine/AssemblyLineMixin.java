package com.EyeOfHarmonyBuffer.Mixins.AssemblyLine;

import gregtech.api.enums.GTValues;
import gregtech.api.metatileentity.implementations.MTEHatchInputBus;
import gregtech.api.util.GTRecipe;
import gregtech.api.util.GTUtility;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

@Mixin(value = GTRecipe.RecipeAssemblyLine.class, remap = false)
public abstract class AssemblyLineMixin {

    private static ItemStack[] lastRecipeInputs = null;


    /**
     * @author
     * @reason
     */
    @Overwrite
    public static int[] getItemConsumptionAmountArray(ArrayList<MTEHatchInputBus> inputBusses,
                                                      GTRecipe.RecipeAssemblyLine recipe) {
        System.out.println("[EYEBUF] Overwriting getItemConsumptionAmountArray.");

        // 验证配方输入是否有效
        if (recipe == null || recipe.mInputs == null || recipe.mInputs.length == 0) {
            System.out.println("[EYEBUF] Recipe has no valid input items. Returning null.");
            return null;
        }

        for (ItemStack input : recipe.mInputs) {
            if (input == null) {
                System.out.println("[EYEBUF] Invalid recipe input detected: null value in mInputs.");
                return null;
            }
        }

        int itemCount = recipe.mInputs.length;
        int[] tStacks = new int[itemCount];

        for (int i = 0; i < itemCount; i++) {
            ItemStack required = recipe.mInputs[i];
            int amountNeeded = required.stackSize;
            int amountCollected = 0;

            for (MTEHatchInputBus inputBus : inputBusses) {
                if (!inputBus.isValid()) continue;

                int slotCount = inputBus.getSizeInventory();
                for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                    ItemStack slotStack = inputBus.getStackInSlot(slotIndex);
                    if (slotStack == null || slotStack.stackSize == 0) continue;

                    // 检查当前格子中的物品是否匹配
                    if (GTUtility.areStacksEqual(required, slotStack, true)) {
                        int amountToTake = Math.min(slotStack.stackSize, amountNeeded - amountCollected);
                        amountCollected += amountToTake;

                        System.out.println("[DEBUG] Detected " + amountToTake + " of " + required + " from slot " + slotIndex + ", total detected: " + amountCollected);

                        // 如果已经满足需求，跳出循环
                        if (amountCollected >= amountNeeded) break;
                    }
                }
                if (amountCollected >= amountNeeded) break;
            }

            // 如果无法满足配方需求，返回 null
            if (amountCollected < amountNeeded) {
                System.out.println("[DEBUG] Missing required item for recipe: " + required + ", needed: " + amountNeeded + ", detected: " + amountCollected);
                return null;
            }

            // 记录物品需求量
            tStacks[i] = amountNeeded;
        }

        System.out.println("[EYEBUF] Item detection array: " + Arrays.toString(tStacks));
        return tStacks;
    }

    /**
     * @author
     * @reason
     */
    @Overwrite
    public static double maxParallelCalculatedByInputItems(ArrayList<MTEHatchInputBus> inputBusses,
                                                           int maxParallel,
                                                           int[] itemConsumptions,
                                                           Map<GTUtility.ItemId, ItemStack> inputsFromME) {
        if (GTValues.D1) {
            System.out.println("[EYEBUF] Overwriting maxParallelCalculatedByInputItems.");
        }

        // Validate inputs
        if (inputBusses == null || itemConsumptions == null || itemConsumptions.length == 0) {
            if (GTValues.D1) {
                System.out.println("[EYEBUF] Invalid input busses or item consumptions. Returning 0.");
            }
            return 0.0;
        }

        // Check for cached recipe inputs
        if (lastRecipeInputs == null) {
            if (GTValues.D1) {
                System.out.println("[EYEBUF] No cached recipe inputs found. Returning 0.");
            }
            return 0.0;
        }

        double currentParallel = maxParallel;
        Map<ItemStack, Integer> totalItemCounts = new HashMap<>();

        // Aggregate items from all input busses
        for (MTEHatchInputBus inputBus : inputBusses) {
            if (!inputBus.isValid()) continue;

            int slotCount = inputBus.getSizeInventory();
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                ItemStack slotStack = inputBus.getStackInSlot(slotIndex);
                if (slotStack == null || slotStack.stackSize == 0) continue;

                // Accumulate item counts
                totalItemCounts.merge(slotStack, slotStack.stackSize, Integer::sum);

                if (GTValues.D1) {
                    System.out.println("[EYEBUF] Found item in input bus slot " + slotIndex + ": " + slotStack + " x" + slotStack.stackSize);
                }
            }
        }

        // Calculate maximum parallel for each item
        for (int i = 0; i < itemConsumptions.length; i++) {
            int required = itemConsumptions[i];
            if (required <= 0) continue;

            int totalAvailable = 0;
            if (GTValues.D1) {
                System.out.println("[EYEBUF] Checking consumption for item index " + i + ", required amount: " + required);
            }

            // Check total available items that match the recipe input
            for (Map.Entry<ItemStack, Integer> entry : totalItemCounts.entrySet()) {
                ItemStack availableStack = entry.getKey();
                int availableCount = entry.getValue();

                // Compare the available stack with the recipe input
                if (GTUtility.areStacksEqual(lastRecipeInputs[i], availableStack, true)) {
                    totalAvailable += availableCount;
                    if (GTValues.D1) {
                        System.out.println("[EYEBUF] Matched item: " + availableStack + ", available count: " + availableCount);
                    }
                } else {
                    if (GTValues.D1) {
                        System.out.println("[EYEBUF] Item not matched: " + availableStack);
                    }
                }
            }

            // Update current parallel based on available items
            currentParallel = Math.min(currentParallel, (double) totalAvailable / required);
            if (GTValues.D1) {
                System.out.println("[EYEBUF] Total available: " + totalAvailable + ", updated parallel: " + currentParallel);
            }

            // If any item is insufficient, return 0
            if (currentParallel <= 0) {
                if (GTValues.D1) {
                    System.out.println("[EYEBUF] Insufficient items for parallel calculation. Returning 0.");
                }
                return 0.0;
            }
        }

        if (GTValues.D1) {
            System.out.println("[EYEBUF] Calculated max parallel: " + currentParallel);
        }
        return currentParallel;
    }
}
