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

    private static final int FRAME_WIDTH = 320;
    private static final int FRAME_HEIGHT = 180;

    private ReactorVideoState currentState = null;
    private int currentFrameIndex = 0;
    private boolean playing = false;

    private long startMillis = 0L;
    private long elapsedMs = 0L;

    public static final ReactorVideoPlayer INSTANCE = new ReactorVideoPlayer();

    private ReactorVideoPlayer() {}

    public void play(ReactorVideoState state) {
        if (state == null) return;

        this.currentState = state;
        this.currentFrameIndex = 0;
        this.playing = true;

        this.startMillis = System.currentTimeMillis();
        this.elapsedMs = 0L;

        if (state.soundId != null && !state.soundId.isEmpty()) {
            playSoundById(state.soundId);
        }
    }

    public void stop() {
        this.playing = false;
        this.currentState = null;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void onClientTick() {
        if (!playing || currentState == null) return;

        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.thePlayer == null) {
            stop();
            return;
        }

        this.elapsedMs = System.currentTimeMillis() - this.startMillis;

        if (elapsedMs >= currentState.totalDurationMs) {
            stop();
            return;
        }

        long frameDurationMs = 1000L / currentState.fps;
        int frame = (int)(elapsedMs / frameDurationMs);

        if (frame < 0) frame = 0;
        if (frame > currentState.maxFrameIndex) {
            frame = currentState.maxFrameIndex;
        }

        this.currentFrameIndex = frame;
    }

    public void renderOnHud(Minecraft mc) {
        if (!playing || currentState == null) return;

        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int sw = res.getScaledWidth();
        int sh = res.getScaledHeight();

        ResourceLocation tex = getCurrentFrameTexture();
        if (tex == null) return;

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
        if (currentState == null) return null;

        int digits;
        if (currentState.maxFrameIndex >= 100) {
            digits = 3;
        } else if (currentState.maxFrameIndex >= 10) {
            digits = 2;
        } else {
            digits = 1;
        }

        String frameStr = String.format("%0" + digits + "d", currentFrameIndex);

        String path = String.format(
            "textures/gui/reactor/%s/%s%s.png",
            currentState.folder,
            currentState.folder,
            frameStr
        );

        return new ResourceLocation(EyeOfHarmonyBuffer.MODID, path);
    }

    private void playSoundById(String soundId) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc == null) return;
        if (soundId == null || soundId.isEmpty()) return;
        if (mc.theWorld == null || mc.thePlayer == null) return;

        try {
            ResourceLocation loc = new ResourceLocation("eyeofharmonybuffer", soundId);
            ISound sound = PositionedSoundRecord.func_147673_a(loc);
            mc.getSoundHandler().playSound(sound);
        } catch (java.util.ConcurrentModificationException e) {
            e.printStackTrace();
        }
    }
}
