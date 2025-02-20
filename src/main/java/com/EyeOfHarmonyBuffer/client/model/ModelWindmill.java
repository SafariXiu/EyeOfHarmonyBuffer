package com.EyeOfHarmonyBuffer.client.model;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

public class ModelWindmill extends ModelBase {

    private final ModelRenderer blade;

    public ModelWindmill() {
        textureWidth = 64;
        textureHeight = 64;

        blade = new ModelRenderer(this);
        blade.addBox(-8F, -1F, -8F, 16, 2, 16);
    }

    public void render(Entity entity, float f, float f1, float f2, float f3, float f4, float scale) {
        blade.render(scale);
    }
}
