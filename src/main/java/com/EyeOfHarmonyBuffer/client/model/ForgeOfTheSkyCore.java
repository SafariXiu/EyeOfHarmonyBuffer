package com.EyeOfHarmonyBuffer.client.model;
// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.7 - 1.12
// Paste this class into your mod and generate all required imports


import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;

public class ForgeOfTheSkyCore extends ModelBase {
	private final ModelRenderer Core;
	private final ModelRenderer BiaoZhiZu;
	private final ModelRenderer BiaoZhi4;
	private final ModelRenderer BiaoZhi;
	private final ModelRenderer BiaoZhi2;
	private final ModelRenderer BiaoZhi3;
	private final ModelRenderer BiaoZhiZu2;
	private final ModelRenderer BiaoZhi5;
	private final ModelRenderer BiaoZhi6;
	private final ModelRenderer BiaoZhi7;
	private final ModelRenderer BiaoZhi8;
	private final ModelRenderer Core2;
	private final ModelRenderer BiaoZhiZu3;
	private final ModelRenderer BiaoZhi9;
	private final ModelRenderer BiaoZhi10;
	private final ModelRenderer BiaoZhi11;
	private final ModelRenderer BiaoZhi12;
	private final ModelRenderer BiaoZhiZu4;
	private final ModelRenderer BiaoZhi13;
	private final ModelRenderer BiaoZhi14;
	private final ModelRenderer BiaoZhi15;
	private final ModelRenderer BiaoZhi16;

	public ForgeOfTheSkyCore() {
		textureWidth = 256;
		textureHeight = 256;

		Core = new ModelRenderer(this);
		Core.setRotationPoint(5.0F, 12.0F, -9.0F);
		Core.cubeList.add(new ModelBox(Core, 0, 0, -25.0F, -79.0F, -2.0F, 21, 91, 21, 0.0F));
		Core.cubeList.add(new ModelBox(Core, 92, 0, -25.0F, -89.0F, -2.0F, 21, 10, 21, 0.0F));
		Core.cubeList.add(new ModelBox(Core, 92, 0, -25.0F, 12.0F, -2.0F, 21, 10, 21, 0.0F));

		BiaoZhiZu = new ModelRenderer(this);
		BiaoZhiZu.setRotationPoint(0.0F, 0.0F, 0.0F);
		Core.addChild(BiaoZhiZu);


		BiaoZhi4 = new ModelRenderer(this);
		BiaoZhi4.setRotationPoint(0.0F, 0.0F, 0.0F);
		BiaoZhiZu.addChild(BiaoZhi4);
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi4.cubeList.add(new ModelBox(BiaoZhi4, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi = new ModelRenderer(this);
		BiaoZhi.setRotationPoint(0.0F, -60.0F, 0.0F);
		BiaoZhiZu.addChild(BiaoZhi);
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi2 = new ModelRenderer(this);
		BiaoZhi2.setRotationPoint(0.0F, -40.0F, 0.0F);
		BiaoZhiZu.addChild(BiaoZhi2);
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi3 = new ModelRenderer(this);
		BiaoZhi3.setRotationPoint(0.0F, -20.0F, 0.0F);
		BiaoZhiZu.addChild(BiaoZhi3);
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi3.cubeList.add(new ModelBox(BiaoZhi3, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhiZu2 = new ModelRenderer(this);
		BiaoZhiZu2.setRotationPoint(-26.0F, 0.0F, 0.0F);
		Core.addChild(BiaoZhiZu2);


		BiaoZhi5 = new ModelRenderer(this);
		BiaoZhi5.setRotationPoint(0.0F, 0.0F, 0.0F);
		BiaoZhiZu2.addChild(BiaoZhi5);
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi5.cubeList.add(new ModelBox(BiaoZhi5, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi6 = new ModelRenderer(this);
		BiaoZhi6.setRotationPoint(0.0F, -60.0F, 0.0F);
		BiaoZhiZu2.addChild(BiaoZhi6);
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi7 = new ModelRenderer(this);
		BiaoZhi7.setRotationPoint(0.0F, -40.0F, 0.0F);
		BiaoZhiZu2.addChild(BiaoZhi7);
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi7.cubeList.add(new ModelBox(BiaoZhi7, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi8 = new ModelRenderer(this);
		BiaoZhi8.setRotationPoint(0.0F, -20.0F, 0.0F);
		BiaoZhiZu2.addChild(BiaoZhi8);
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi8.cubeList.add(new ModelBox(BiaoZhi8, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		Core2 = new ModelRenderer(this);
		Core2.setRotationPoint(5.0F, 12.0F, 17.0F);
		Core2.cubeList.add(new ModelBox(Core2, 0, 0, -25.0F, -79.0F, -2.0F, 21, 91, 21, 0.0F));
		Core2.cubeList.add(new ModelBox(Core2, 92, 0, -25.0F, -89.0F, -2.0F, 21, 10, 21, 0.0F));
		Core2.cubeList.add(new ModelBox(Core2, 92, 0, -25.0F, 12.0F, -2.0F, 21, 10, 21, 0.0F));

		BiaoZhiZu3 = new ModelRenderer(this);
		BiaoZhiZu3.setRotationPoint(0.0F, 0.0F, 0.0F);
		Core2.addChild(BiaoZhiZu3);


		BiaoZhi9 = new ModelRenderer(this);
		BiaoZhi9.setRotationPoint(0.0F, 0.0F, 0.0F);
		BiaoZhiZu3.addChild(BiaoZhi9);
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi10 = new ModelRenderer(this);
		BiaoZhi10.setRotationPoint(0.0F, -60.0F, 0.0F);
		BiaoZhiZu3.addChild(BiaoZhi10);
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi11 = new ModelRenderer(this);
		BiaoZhi11.setRotationPoint(0.0F, -40.0F, 0.0F);
		BiaoZhiZu3.addChild(BiaoZhi11);
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi11.cubeList.add(new ModelBox(BiaoZhi11, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi12 = new ModelRenderer(this);
		BiaoZhi12.setRotationPoint(0.0F, -20.0F, 0.0F);
		BiaoZhiZu3.addChild(BiaoZhi12);
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi12.cubeList.add(new ModelBox(BiaoZhi12, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhiZu4 = new ModelRenderer(this);
		BiaoZhiZu4.setRotationPoint(-26.0F, 0.0F, 0.0F);
		Core2.addChild(BiaoZhiZu4);


		BiaoZhi13 = new ModelRenderer(this);
		BiaoZhi13.setRotationPoint(0.0F, 0.0F, 0.0F);
		BiaoZhiZu4.addChild(BiaoZhi13);
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi13.cubeList.add(new ModelBox(BiaoZhi13, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi14 = new ModelRenderer(this);
		BiaoZhi14.setRotationPoint(0.0F, -60.0F, 0.0F);
		BiaoZhiZu4.addChild(BiaoZhi14);
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi14.cubeList.add(new ModelBox(BiaoZhi14, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi15 = new ModelRenderer(this);
		BiaoZhi15.setRotationPoint(0.0F, -40.0F, 0.0F);
		BiaoZhiZu4.addChild(BiaoZhi15);
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));

		BiaoZhi16 = new ModelRenderer(this);
		BiaoZhi16.setRotationPoint(0.0F, -20.0F, 0.0F);
		BiaoZhiZu4.addChild(BiaoZhi16);
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 206, 220, -4.0F, -11.0F, -1.0F, 5, 1, 19, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 208, 222, -4.0F, -10.0F, -1.0F, 5, 1, 18, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 210, 224, -4.0F, -9.0F, -1.0F, 5, 1, 17, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 212, 226, -4.0F, -8.0F, -1.0F, 5, 1, 16, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 214, 228, -4.0F, -7.0F, -1.0F, 5, 1, 15, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 216, 230, -4.0F, -6.0F, -1.0F, 5, 1, 14, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 218, 232, -4.0F, -5.0F, -1.0F, 5, 1, 13, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 220, 234, -4.0F, -4.0F, -1.0F, 5, 1, 12, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 222, 236, -4.0F, -3.0F, -1.0F, 5, 1, 11, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 224, 238, -4.0F, -2.0F, -1.0F, 5, 1, 10, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 226, 240, -4.0F, -1.0F, -1.0F, 5, 1, 9, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 228, 242, -4.0F, 0.0F, -1.0F, 5, 1, 8, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 230, 244, -4.0F, 1.0F, -1.0F, 5, 1, 7, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 232, 246, -4.0F, 2.0F, -1.0F, 5, 1, 6, 0.0F));
	}

	@Override
	public void render(Entity entity, float f, float f1, float f2, float f3, float f4, float f5) {
		Core.render(f5);
		Core2.render(f5);
	}

	public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
		modelRenderer.rotateAngleX = x;
		modelRenderer.rotateAngleY = y;
		modelRenderer.rotateAngleZ = z;
	}
}
