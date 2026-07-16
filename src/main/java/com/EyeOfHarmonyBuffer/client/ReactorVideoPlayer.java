package com.EyeOfHarmonyBuffer.client;

import com.EyeOfHarmonyBuffer.EyeOfHarmonyBuffer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

public class ReactorVideoPlayer {

    private static final int TICKS_PER_FRAME = 1;

    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 180;

    private ReactorVideoState currentState = ReactorVideoState.POWER_0;
    private int currentFrameIndex = 0;
    private int tickCounter = 0;
    private boolean playing = false;

    public static final ReactorVideoPlayer INSTANCE = new ReactorVideoPlayer();

    private ReactorVideoPlayer() {}

    public void play(ReactorVideoState state) {
        this.currentState = state;
        this.currentFrameIndex = 0;
        this.tickCounter = 0;
        this.playing = true;

        playMainSoundForState(state);
    }

    public void stop() {
        this.playing = false;
    }

    public void onClientTick() {
        if (!playing) return;

        tickCounter++;
        if (tickCounter >= TICKS_PER_FRAME) {
            tickCounter = 0;
            currentFrameIndex++;

            if (currentFrameIndex > currentState.maxFrameIndex) {
                currentFrameIndex = currentState.maxFrameIndex;
                playing = false;
            }
        }
    }

    public void renderOnHud(Minecraft mc) {
        if (!playing) return;

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        ResourceLocation tex = getCurrentFrameTexture();
        mc.getTextureManager().bindTexture(tex);

        int x = (sw - FRAME_WIDTH) / 2;
        int y = (sh - FRAME_HEIGHT) / 2;

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1F, 1F, 1F, 1F);

        Tessellator tes = Tessellator.instance;
        tes.startDrawingQuads();
        tes.addVertexWithUV(x, y + FRAME_HEIGHT, 0, 0, 1);
        tes.addVertexWithUV(x + FRAME_WIDTH, y + FRAME_HEIGHT, 0, 1, 1);
        tes.addVertexWithUV(x + FRAME_WIDTH, y, 0, 1, 0);
        tes.addVertexWithUV(x, y, 0, 0, 0);
        tes.draw();

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private ResourceLocation getCurrentFrameTexture() {
        String frameStr = String.format("%02d", currentFrameIndex);

        String path = String.format(
            "textures/gui/reactor/%s/%s%s.png",
            currentState.folder,
            currentState.folder,
            frameStr
        );

        return new ResourceLocation(EyeOfHarmonyBuffer.MODID, path);
    }

    private void playMainSoundForState(ReactorVideoState state) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) return;
        if (state.soundId == null || state.soundId.isEmpty()) return;

        ResourceLocation loc = new ResourceLocation("eyeofharmonybuffer", state.soundId);

        ISound sound = PositionedSoundRecord.func_147673_a(loc);

        mc.getSoundHandler().playSound(sound);
    }
}
