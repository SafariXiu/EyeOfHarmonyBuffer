package com.EyeOfHarmonyBuffer.client.model;
// Made with Blockbench 5.1.4
// Exported for Minecraft version 1.7 - 1.12
// Paste this class into your mod and generate all required imports


import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import org.lwjgl.opengl.GL11;

public class ForgeOfTheSky_OutermostCircle extends ModelBase {
	private final ModelRenderer MuKuai2_up;
	private final ModelRenderer ZhuTi7;
	private final ModelRenderer BiaoZhi15;
	private final ModelRenderer BiaoZhi16;
	private final ModelRenderer BiaoZhi17;
	private final ModelRenderer JiaoBiao25;
	private final ModelRenderer JiaoBiao26;
	private final ModelRenderer JiaoBiao27;
	private final ModelRenderer JiaoBiao28;
	private final ModelRenderer MuKuai3_down;
	private final ModelRenderer ZhuTi3;
	private final ModelRenderer BiaoZhi6;
	private final ModelRenderer BiaoZhi9;
	private final ModelRenderer BiaoZhi10;
	private final ModelRenderer JiaoBiao9;
	private final ModelRenderer JiaoBiao10;
	private final ModelRenderer JiaoBiao11;
	private final ModelRenderer JiaoBiao12;
	private final ModelRenderer MuKuai_doube;
	private final ModelRenderer ZhuTi;
	private final ModelRenderer BiaoZhi;
	private final ModelRenderer BiaoZhi2;
	private final ModelRenderer JiaoBiao;
	private final ModelRenderer JiaoBiao2;
	private final ModelRenderer JiaoBiao3;
	private final ModelRenderer JiaoBiao4;

	public ForgeOfTheSky_OutermostCircle() {
		textureWidth = 64;
		textureHeight = 64;

		MuKuai2_up = new ModelRenderer(this);
		//MuKuai2_up.setRotationPoint(-75.0F, 18.0F, 32.0F);
        MuKuai2_up.setRotationPoint(0.0F, 0.0F, 0.0F);


		ZhuTi7 = new ModelRenderer(this);
		ZhuTi7.setRotationPoint(0.0F, 0.0F, 0.0F);
		MuKuai2_up.addChild(ZhuTi7);
		ZhuTi7.cubeList.add(new ModelBox(ZhuTi7, 0, 0, -14.0F, -29.0F, -1.0F, 14, 29, 3, 0.0F));
		ZhuTi7.cubeList.add(new ModelBox(ZhuTi7, 0, 0, -20.0F, -35.0F, 7.0F, 26, 41, 2, 0.0F));
		ZhuTi7.cubeList.add(new ModelBox(ZhuTi7, 0, 0, -18.0F, -33.0F, 5.0F, 22, 37, 2, 0.0F));
		ZhuTi7.cubeList.add(new ModelBox(ZhuTi7, 0, 0, -16.0F, -31.0F, 2.0F, 18, 33, 3, 0.0F));

		BiaoZhi15 = new ModelRenderer(this);
		BiaoZhi15.setRotationPoint(2.0F, -6.0F, 10.0F);
		MuKuai2_up.addChild(BiaoZhi15);
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 12, 61, -19.0F, -1.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 12, 61, -19.0F, -2.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 12, 61, -19.0F, -3.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 20, 61, -18.0F, -4.0F, -1.0F, 18, 1, 2, 0.0F));
		BiaoZhi15.cubeList.add(new ModelBox(BiaoZhi15, 28, 61, -17.0F, -5.0F, -1.0F, 16, 1, 2, 0.0F));

		BiaoZhi16 = new ModelRenderer(this);
		BiaoZhi16.setRotationPoint(2.0F, -18.0F, 10.0F);
		MuKuai2_up.addChild(BiaoZhi16);
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 24, 61, -7.0F, -1.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 24, 61, -7.0F, -2.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 24, 61, -7.0F, -3.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 32, 61, -6.0F, -4.0F, -1.0F, 6, 1, 2, 0.0F));
		BiaoZhi16.cubeList.add(new ModelBox(BiaoZhi16, 40, 61, -5.0F, -5.0F, -1.0F, 4, 1, 2, 0.0F));

		BiaoZhi17 = new ModelRenderer(this);
		BiaoZhi17.setRotationPoint(-10.0F, -18.0F, 10.0F);
		MuKuai2_up.addChild(BiaoZhi17);
		BiaoZhi17.cubeList.add(new ModelBox(BiaoZhi17, 24, 61, -7.0F, -1.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi17.cubeList.add(new ModelBox(BiaoZhi17, 24, 61, -7.0F, -2.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi17.cubeList.add(new ModelBox(BiaoZhi17, 24, 61, -7.0F, -3.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi17.cubeList.add(new ModelBox(BiaoZhi17, 32, 61, -6.0F, -4.0F, -1.0F, 6, 1, 2, 0.0F));
		BiaoZhi17.cubeList.add(new ModelBox(BiaoZhi17, 40, 61, -5.0F, -5.0F, -1.0F, 4, 1, 2, 0.0F));

		JiaoBiao25 = new ModelRenderer(this);
		JiaoBiao25.setRotationPoint(6.0F, -35.0F, 11.0F);
		MuKuai2_up.addChild(JiaoBiao25);
		JiaoBiao25.cubeList.add(new ModelBox(JiaoBiao25, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao25.cubeList.add(new ModelBox(JiaoBiao25, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao26 = new ModelRenderer(this);
		JiaoBiao26.setRotationPoint(-21.0F, -35.0F, 11.0F);
		MuKuai2_up.addChild(JiaoBiao26);
		JiaoBiao26.cubeList.add(new ModelBox(JiaoBiao26, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao26.cubeList.add(new ModelBox(JiaoBiao26, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao27 = new ModelRenderer(this);
		JiaoBiao27.setRotationPoint(-21.0F, 7.0F, 11.0F);
		MuKuai2_up.addChild(JiaoBiao27);
		JiaoBiao27.cubeList.add(new ModelBox(JiaoBiao27, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao27.cubeList.add(new ModelBox(JiaoBiao27, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao28 = new ModelRenderer(this);
		JiaoBiao28.setRotationPoint(6.0F, 7.0F, 11.0F);
		MuKuai2_up.addChild(JiaoBiao28);
		JiaoBiao28.cubeList.add(new ModelBox(JiaoBiao28, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao28.cubeList.add(new ModelBox(JiaoBiao28, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		MuKuai3_down = new ModelRenderer(this);
		//MuKuai3_down.setRotationPoint(-60.0F, 20.0F, 41.0F);
        MuKuai3_down.setRotationPoint(0.0F, 0.0F, 0.0F);


		ZhuTi3 = new ModelRenderer(this);
		ZhuTi3.setRotationPoint(20.0F, -2.0F, -9.0F);
		MuKuai3_down.addChild(ZhuTi3);
		ZhuTi3.cubeList.add(new ModelBox(ZhuTi3, 0, 0, -14.0F, -29.0F, -1.0F, 14, 29, 3, 0.0F));
		ZhuTi3.cubeList.add(new ModelBox(ZhuTi3, 0, 0, -20.0F, -35.0F, 7.0F, 26, 41, 2, 0.0F));
		ZhuTi3.cubeList.add(new ModelBox(ZhuTi3, 0, 0, -18.0F, -33.0F, 5.0F, 22, 37, 2, 0.0F));
		ZhuTi3.cubeList.add(new ModelBox(ZhuTi3, 0, 0, -16.0F, -31.0F, 2.0F, 18, 33, 3, 0.0F));

		BiaoZhi6 = new ModelRenderer(this);
		BiaoZhi6.setRotationPoint(22.0F, -20.0F, 1.0F);
		MuKuai3_down.addChild(BiaoZhi6);
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 12, 61, -19.0F, -1.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 12, 61, -19.0F, -2.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 12, 61, -19.0F, -3.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 20, 61, -18.0F, -4.0F, -1.0F, 18, 1, 2, 0.0F));
		BiaoZhi6.cubeList.add(new ModelBox(BiaoZhi6, 28, 61, -17.0F, -5.0F, -1.0F, 16, 1, 2, 0.0F));

		BiaoZhi9 = new ModelRenderer(this);
		BiaoZhi9.setRotationPoint(22.0F, -8.0F, 1.0F);
		MuKuai3_down.addChild(BiaoZhi9);
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 24, 61, -7.0F, -1.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 24, 61, -7.0F, -2.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 24, 61, -7.0F, -3.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 32, 61, -6.0F, -4.0F, -1.0F, 6, 1, 2, 0.0F));
		BiaoZhi9.cubeList.add(new ModelBox(BiaoZhi9, 40, 61, -5.0F, -5.0F, -1.0F, 4, 1, 2, 0.0F));

		BiaoZhi10 = new ModelRenderer(this);
		BiaoZhi10.setRotationPoint(10.0F, -8.0F, 1.0F);
		MuKuai3_down.addChild(BiaoZhi10);
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 24, 61, -7.0F, -1.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 24, 61, -7.0F, -2.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 24, 61, -7.0F, -3.0F, -1.0F, 8, 1, 2, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 32, 61, -6.0F, -4.0F, -1.0F, 6, 1, 2, 0.0F));
		BiaoZhi10.cubeList.add(new ModelBox(BiaoZhi10, 40, 61, -5.0F, -5.0F, -1.0F, 4, 1, 2, 0.0F));

		JiaoBiao9 = new ModelRenderer(this);
		JiaoBiao9.setRotationPoint(26.0F, -37.0F, 2.0F);
		MuKuai3_down.addChild(JiaoBiao9);
		JiaoBiao9.cubeList.add(new ModelBox(JiaoBiao9, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao9.cubeList.add(new ModelBox(JiaoBiao9, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao10 = new ModelRenderer(this);
		JiaoBiao10.setRotationPoint(-1.0F, -37.0F, 2.0F);
		MuKuai3_down.addChild(JiaoBiao10);
		JiaoBiao10.cubeList.add(new ModelBox(JiaoBiao10, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao10.cubeList.add(new ModelBox(JiaoBiao10, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao11 = new ModelRenderer(this);
		JiaoBiao11.setRotationPoint(-1.0F, 5.0F, 2.0F);
		MuKuai3_down.addChild(JiaoBiao11);
		JiaoBiao11.cubeList.add(new ModelBox(JiaoBiao11, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao11.cubeList.add(new ModelBox(JiaoBiao11, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao12 = new ModelRenderer(this);
		JiaoBiao12.setRotationPoint(26.0F, 5.0F, 2.0F);
		MuKuai3_down.addChild(JiaoBiao12);
		JiaoBiao12.cubeList.add(new ModelBox(JiaoBiao12, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao12.cubeList.add(new ModelBox(JiaoBiao12, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		MuKuai_doube = new ModelRenderer(this);
		//MuKuai_doube.setRotationPoint(-5.0F, 18.0F, 32.0F);
        MuKuai_doube.setRotationPoint(0.0F, 0.0F, 0.0F);


		ZhuTi = new ModelRenderer(this);
		ZhuTi.setRotationPoint(0.0F, 0.0F, 0.0F);
		MuKuai_doube.addChild(ZhuTi);
		ZhuTi.cubeList.add(new ModelBox(ZhuTi, 0, 0, -14.0F, -29.0F, -1.0F, 14, 29, 3, 0.0F));
		ZhuTi.cubeList.add(new ModelBox(ZhuTi, 0, 0, -20.0F, -35.0F, 7.0F, 26, 41, 2, 0.0F));
		ZhuTi.cubeList.add(new ModelBox(ZhuTi, 0, 0, -18.0F, -33.0F, 5.0F, 22, 37, 2, 0.0F));
		ZhuTi.cubeList.add(new ModelBox(ZhuTi, 0, 0, -16.0F, -31.0F, 2.0F, 18, 33, 3, 0.0F));

		BiaoZhi = new ModelRenderer(this);
		BiaoZhi.setRotationPoint(2.0F, -6.0F, 10.0F);
		MuKuai_doube.addChild(BiaoZhi);
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 12, 61, -19.0F, -1.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 12, 61, -19.0F, -2.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 12, 61, -19.0F, -3.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 20, 61, -18.0F, -4.0F, -1.0F, 18, 1, 2, 0.0F));
		BiaoZhi.cubeList.add(new ModelBox(BiaoZhi, 28, 61, -17.0F, -5.0F, -1.0F, 16, 1, 2, 0.0F));

		BiaoZhi2 = new ModelRenderer(this);
		BiaoZhi2.setRotationPoint(2.0F, -18.0F, 10.0F);
		MuKuai_doube.addChild(BiaoZhi2);
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 12, 61, -19.0F, -1.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 12, 61, -19.0F, -2.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 12, 61, -19.0F, -3.0F, -1.0F, 20, 1, 2, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 20, 61, -18.0F, -4.0F, -1.0F, 18, 1, 2, 0.0F));
		BiaoZhi2.cubeList.add(new ModelBox(BiaoZhi2, 28, 61, -17.0F, -5.0F, -1.0F, 16, 1, 2, 0.0F));

		JiaoBiao = new ModelRenderer(this);
		JiaoBiao.setRotationPoint(6.0F, -35.0F, 11.0F);
		MuKuai_doube.addChild(JiaoBiao);
		JiaoBiao.cubeList.add(new ModelBox(JiaoBiao, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao.cubeList.add(new ModelBox(JiaoBiao, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao2 = new ModelRenderer(this);
		JiaoBiao2.setRotationPoint(-21.0F, -35.0F, 11.0F);
		MuKuai_doube.addChild(JiaoBiao2);
		JiaoBiao2.cubeList.add(new ModelBox(JiaoBiao2, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao2.cubeList.add(new ModelBox(JiaoBiao2, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao3 = new ModelRenderer(this);
		JiaoBiao3.setRotationPoint(-21.0F, 7.0F, 11.0F);
		MuKuai_doube.addChild(JiaoBiao3);
		JiaoBiao3.cubeList.add(new ModelBox(JiaoBiao3, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao3.cubeList.add(new ModelBox(JiaoBiao3, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));

		JiaoBiao4 = new ModelRenderer(this);
		JiaoBiao4.setRotationPoint(6.0F, 7.0F, 11.0F);
		MuKuai_doube.addChild(JiaoBiao4);
		JiaoBiao4.cubeList.add(new ModelBox(JiaoBiao4, 0, 54, 0.0F, -1.0F, -1.0F, 1, 1, 1, 0.0F));
		JiaoBiao4.cubeList.add(new ModelBox(JiaoBiao4, 0, 44, -1.0F, -2.0F, -5.0F, 3, 3, 4, 0.0F));
	}

    @Override
    public void render(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                       float netHeadYaw, float headPitch, float scale) {

        GL11.glPushMatrix();

        int total = 8;
        float radius = 80.0F * scale;

        int i = 0;

        // doube up doube down doube up doube down
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai2_up,   i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai3_down, i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai2_up,   i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, 0.0F);
        renderOnCircle(MuKuai3_down, i++, total, radius, scale, 0.0F);

        GL11.glPopMatrix();
    }

    private void renderOnCircle(ModelRenderer part,
                                int index,
                                int total,
                                float radius,
                                float scale,
                                float globalAngleOffset) {
        GL11.glPushMatrix();

        float anglePerSlot = 360.0F / (float) total;
        float angle = anglePerSlot * index + globalAngleOffset;

        GL11.glRotatef(angle, 0.0F, 1.0F, 0.0F);
        GL11.glTranslatef(0.0F, 0.0F, radius);

        part.render(scale);

        GL11.glPopMatrix();
    }

    public void renderAnimated(float scale,
                               float globalAngleOffset,
                               float radiusOffset) {

        GL11.glPushMatrix();

        int total = 8;
        float baseRadius = 100.0F * scale;
        float radius = baseRadius + radiusOffset;

        int i = 0;

        renderOnCircle(MuKuai_doube, i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai2_up,   i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai3_down, i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai2_up,   i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai_doube, i++, total, radius, scale, globalAngleOffset);
        renderOnCircle(MuKuai3_down, i++, total, radius, scale, globalAngleOffset);

        GL11.glPopMatrix();
    }

	public void setRotationAngle(ModelRenderer modelRenderer, float x, float y, float z) {
		modelRenderer.rotateAngleX = x;
		modelRenderer.rotateAngleY = y;
		modelRenderer.rotateAngleZ = z;
	}
}
