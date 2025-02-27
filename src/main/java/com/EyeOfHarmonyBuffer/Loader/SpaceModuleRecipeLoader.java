package com.EyeOfHarmonyBuffer.Loader;

import com.EyeOfHarmonyBuffer.Recipe.EOHBSpacePumpingRecipes;

public class SpaceModuleRecipeLoader implements Runnable{

    @Override
    public void run() {
        EOHBSpacePumpingRecipes.addPumpingRecipes();
    }
}
