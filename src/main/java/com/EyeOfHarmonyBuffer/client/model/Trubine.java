package com.EyeOfHarmonyBuffer.client.model;
// Made with Blockbench 4.12.2
// Exported for Minecraft version 1.7 - 1.12
// Paste this class into your mod and generate all required imports


import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

public class Trubine extends ModelBase {
	private final ModelRenderer ALL3;
	private final ModelRenderer bone7;
	private final ModelRenderer bone8;
	private final ModelRenderer bone9;
	private final ModelRenderer ALL2;
	private final ModelRenderer bone4;
	private final ModelRenderer bone5;
	private final ModelRenderer bone6;
	private final ModelRenderer ALL;
	private final ModelRenderer bone;
	private final ModelRenderer bone2;
	private final ModelRenderer bone3;

	public Trubine() {
		textureWidth = 128;
		textureHeight = 128;

		ALL3 = new ModelRenderer(this);
		ALL3.setRotationPoint(0.0F, 0.0F, 0.0F);
		setRotationAngle(ALL3, 0.0F, 0.0F, -0.5236F);


		bone7 = new ModelRenderer(this);
		bone7.setRotationPoint(-24.0F, 1.5F, -0.75F);
		ALL3.addChild(bone7);
		bone7.cubeList.add(new ModelBox(bone7, 15, 18, -4.0F, -3.0F, 1.0F, 1, 3, 1, 0.0F));
		bone7.cubeList.add(new ModelBox(bone7, 9, 18, -3.0F, -3.0F, 0.75F, 1, 3, 1, 0.0F));
		bone7.cubeList.add(new ModelBox(bone7, 17, 12, -2.0F, -3.0F, 0.5F, 1, 3, 2, 0.0F));
		bone7.cubeList.add(new ModelBox(bone7, 9, 12, -1.0F, -3.0F, 0.25F, 1, 3, 2, 0.0F));
		bone7.cubeList.add(new ModelBox(bone7, 1, 12, 0.0F, -3.0F, 0.0F, 1, 3, 2, 0.0F));

		bone8 = new ModelRenderer(this);
		bone8.setRotationPoint(-33.0F, 1.25F, 3.0F);
		ALL3.addChild(bone8);
		bone8.cubeList.add(new ModelBox(bone8, 1, 1, 1.0F, -2.75F, -2.0F, 31, 3, 1, 0.0F));
		bone8.cubeList.add(new ModelBox(bone8, 20, 17, -1.0F, -2.25F, -2.0F, 2, 2, 1, 0.0F));

		bone9 = new ModelRenderer(this);
		bone9.setRotationPoint(-2.0F, 1.5F, 2.0F);
		ALL3.addChild(bone9);
		bone9.cubeList.add(new ModelBox(bone9, 0, 17, -2.0F, -3.0F, -2.0F, 3, 3, 1, 0.0F));
		bone9.cubeList.add(new ModelBox(bone9, 1, 17, -2.0F, -3.0F, -3.0F, 2, 3, 1, 0.0F));
		bone9.cubeList.add(new ModelBox(bone9, 1, 6, -21.0F, -3.0F, -3.0F, 19, 3, 2, 0.0F));

		ALL2 = new ModelRenderer(this);
		ALL2.setRotationPoint(0.0F, 0.0F, 0.0F);
		setRotationAngle(ALL2, 0.0F, 0.0F, -2.618F);


		bone4 = new ModelRenderer(this);
		bone4.setRotationPoint(-24.0F, 1.5F, -0.75F);
		ALL2.addChild(bone4);
		bone4.cubeList.add(new ModelBox(bone4, 15, 18, -4.0F, -3.0F, 1.0F, 1, 3, 1, 0.0F));
		bone4.cubeList.add(new ModelBox(bone4, 9, 18, -3.0F, -3.0F, 0.75F, 1, 3, 1, 0.0F));
		bone4.cubeList.add(new ModelBox(bone4, 17, 12, -2.0F, -3.0F, 0.5F, 1, 3, 2, 0.0F));
		bone4.cubeList.add(new ModelBox(bone4, 9, 12, -1.0F, -3.0F, 0.25F, 1, 3, 2, 0.0F));
		bone4.cubeList.add(new ModelBox(bone4, 1, 12, 0.0F, -3.0F, 0.0F, 1, 3, 2, 0.0F));

		bone5 = new ModelRenderer(this);
		bone5.setRotationPoint(-33.0F, 1.25F, 3.0F);
		ALL2.addChild(bone5);
		bone5.cubeList.add(new ModelBox(bone5, 1, 1, 1.0F, -2.75F, -2.0F, 31, 3, 1, 0.0F));
		bone5.cubeList.add(new ModelBox(bone5, 20, 17, -1.0F, -2.25F, -2.0F, 2, 2, 1, 0.0F));

		bone6 = new ModelRenderer(this);
		bone6.setRotationPoint(-2.0F, 1.5F, 2.0F);
		ALL2.addChild(bone6);
		bone6.cubeList.add(new ModelBox(bone6, 0, 17, -2.0F, -3.0F, -2.0F, 3, 3, 1, 0.0F));
		bone6.cubeList.add(new ModelBox(bone6, 1, 17, -2.0F, -3.0F, -3.0F, 2, 3, 1, 0.0F));
		bone6.cubeList.add(new ModelBox(bone6, 1, 6, -21.0F, -3.0F, -3.0F, 19, 3, 2, 0.0F));

		ALL = new ModelRenderer(this);
		ALL.setRotationPoint(0.0F, 0.0F, 0.0F);
		setRotationAngle(ALL, 0.0F, 0.0F, 1.5708F);


		bone = new ModelRenderer(this);
		bone.setRotationPoint(-24.0F, 1.5F, -0.75F);
		ALL.addChild(bone);
		bone.cubeList.add(new ModelBox(bone, 15, 18, -4.0F, -3.0F, 1.0F, 1, 3, 1, 0.0F));
		bone.cubeList.add(new ModelBox(bone, 9, 18, -3.0F, -3.0F, 0.75F, 1, 3, 1, 0.0F));
		bone.cubeList.add(new ModelBox(bone, 17, 12, -2.0F, -3.0F, 0.5F, 1, 3, 2, 0.0F));
		bone.cubeList.add(new ModelBox(bone, 9, 12, -1.0F, -3.0F, 0.25F, 1, 3, 2, 0.0F));
		bone.cubeList.add(new ModelBox(bone, 1, 12, 0.0F, -3.0F, 0.0F, 1, 3, 2, 0.0F));

		bone2 = new ModelRenderer(this);
		bone2.setRotationPoint(-33.0F, 1.25F, 3.0F);
		ALL.addChild(bone2);
		bone2.cubeList.add(new ModelBox(bone2, 1, 1, 1.0F, -2.75F, -2.0F, 31, 3, 1, 0.0F));
		bone2.cubeList.add(new ModelBox(bone2, 20, 17, -1.0F, -2.25F, -2.0F, 2, 2, 1, 0.0F));

		bone3 = new ModelRenderer(this);
		bone3.setRotationPoint(-2.0F, 1.5F, 2.0F);
		ALL.addChild(bone3);
		bone3.cubeList.add(new ModelBox(bone3, 0, 17, -2.0F, -3.0F, -2.0F, 3, 3, 1, 0.0F));
		bone3.cubeList.add(new ModelBox(bone3, 1, 17, -2.0F, -3.0F, -3.0F, 2, 3, 1, 0.0F));
		bone3.cubeList.add(new ModelBox(bone3, 1, 6, -21.0F, -3.0F, -3.0F, 19, 3, 2, 0.0F));
	}

	@Override
	public void render(Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
		ALL3.render(f5);
		ALL2.render(f5);
		ALL.render(f5);
	}

	public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
		modelRenderer.rotateAngleX = x;
		modelRenderer.rotateAngleY = y;
		modelRenderer.rotateAngleZ = z;
	}
}
