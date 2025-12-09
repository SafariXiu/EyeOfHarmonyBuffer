package com.EyeOfHarmonyBuffer.utils;

import gregtech.api.util.GTRecipe;
import net.minecraft.item.ItemStack;
import gregtech.api.recipe.RecipeMapBackend;
import net.minecraftforge.fluids.FluidStack;

import java.util.*;

public class RecipeRemover {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private RecipeMapBackend backend;
        private final List<ItemStack> itemInputs = new ArrayList<>();
        private final List<FluidStack> fluidInputs = new ArrayList<>();
        private final List<ItemStack> outputs = new ArrayList<>();
        private final List<FluidStack> fluidOutputs = new ArrayList<>();

        public Builder backend(RecipeMapBackend backend) {
            this.backend = backend;
            return this;
        }

        public Builder itemInputs(ItemStack... items) {
            if (items != null) Collections.addAll(itemInputs, items);
            return this;
        }

        public Builder fluidInputs(FluidStack... fluids) {
            if (fluids != null) Collections.addAll(fluidInputs, fluids);
            return this;
        }

        public Builder itemOutputs(ItemStack... items) {
            if (items != null) Collections.addAll(outputs, items);
            return this;
        }

        public Builder fluidOutputs(FluidStack... fluids) {
            if (fluids != null) Collections.addAll(fluidOutputs, fluids);
            return this;
        }

        public void remove() {
            if (backend == null) {
                System.out.println("[EOHB Recipe Remover] Backend not specified!");
                return;
            }

            Set<GTRecipe> toRemove = new HashSet<>();

            for (GTRecipe recipe : backend.getAllRecipes()) {
                if (recipe == null) continue;

                boolean matchItems  = RecipeMatchers.matchesItems(recipe, itemInputs.toArray(new ItemStack[0]));
                boolean matchFluids = RecipeMatchers.matchesFluids(recipe, fluidInputs.toArray(new FluidStack[0]));
                boolean matchOutputs =
                    (outputs.isEmpty() && fluidOutputs.isEmpty())
                        || (RecipeMatchers.matchesOutputs(recipe, outputs.toArray(new ItemStack[0]))
                        && RecipeMatchers.matchesFluidOutputs(recipe, fluidOutputs.toArray(new FluidStack[0])));

                if (matchItems && matchFluids && matchOutputs) {
                    toRemove.add(recipe);
                    RecipeMatchers.printRecipe(recipe);
                }
            }

            if (!toRemove.isEmpty()) {
                backend.removeRecipes(toRemove);
                backend.reInit();
                System.out.println("[EOHB Recipe Remover] Removed " + toRemove.size() + " matching recipe(s).");
            } else {
                System.out.println("[EOHB Recipe Remover] No matching recipe found to remove.");
            }
        }
    }

    private static class RecipeMatchers {

        static boolean matchesItems(GTRecipe recipe, ItemStack... targets) {
            if (targets == null || targets.length == 0) return true;
            for (ItemStack target : targets) {
                boolean found = false;
                for (ItemStack in : recipe.mInputs) {
                    if (in != null && target != null && in.isItemEqual(target)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        static boolean matchesOutputs(GTRecipe recipe, ItemStack... targets) {
            if (targets == null || targets.length == 0) return true;
            for (ItemStack target : targets) {
                boolean found = false;
                for (ItemStack out : recipe.mOutputs) {
                    if (out != null && target != null && out.isItemEqual(target)) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        static boolean matchesFluidOutputs(GTRecipe recipe, FluidStack... targets) {
            if (targets == null || targets.length == 0) return true;
            for (FluidStack target : targets) {
                boolean found = false;
                for (FluidStack out : recipe.mFluidOutputs) {
                    if (out != null && target != null &&
                        out.getFluid().getName().equals(target.getFluid().getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        static boolean matchesFluids(GTRecipe recipe, FluidStack... targets) {
            if (targets == null || targets.length == 0) return true;
            for (FluidStack target : targets) {
                boolean found = false;
                for (FluidStack in : recipe.mFluidInputs) {
                    if (in != null && target != null &&
                        target.getFluid().getName().equals(in.getFluid().getName())) {
                        found = true;
                        break;
                    }
                }
                if (!found) return false;
            }
            return true;
        }

        static void printRecipe(GTRecipe recipe) {
            System.out.print("[EOHB Recipe Remover] Found recipe → Items: ");
            for (ItemStack in : recipe.mInputs) {
                if (in != null) System.out.print(in.getDisplayName() + " x" + in.stackSize + ", ");
            }

            if (recipe.mFluidInputs != null && recipe.mFluidInputs.length > 0) {
                System.out.print(" | Fluids: ");
                for (FluidStack f : recipe.mFluidInputs) {
                    if (f != null) System.out.print(f.getFluid().getLocalizedName(f) + " " + f.amount + "mB, ");
                }
            }

            System.out.print(" | Outputs: ");
            for (ItemStack out : recipe.mOutputs) {
                if (out != null) System.out.print(out.getDisplayName() + " x" + out.stackSize + ", ");
            }
            System.out.println();
        }
    }
}
