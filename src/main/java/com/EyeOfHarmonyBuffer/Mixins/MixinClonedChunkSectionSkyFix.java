package com.EyeOfHarmonyBuffer.Mixins;

import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;
import com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复 Angelica 的「地下全空气 section 渲染成全亮」bug。
 *
 * 背景：巨型地下空腔（如洞厅）会把某个 16×16×16 section 整层挖成纯空气。
 * 服务端对这些空 section 不发送方块与光照数据（storageArrays=null）；
 * Angelica 客户端 {@code ClonedChunkSection.init} 对 null section 用
 * {@code EMPTY_SECTION}（hasSky=false），{@code getLightArray(Sky)} 返回 null，
 * 而 {@code WorldSlice.getLightLevel} 对 null 光照数组一律回退
 * {@code defaultLightValues[Sky]}=15（全亮），导致地下洞厅整块像被太阳照到，
 * 而 vanilla 对 null section 会按 canBlockSeeTheSky 返回 15/0。
 *
 * 修复：在克隆空 section 时，构造一个带天空光数组的 ExtendedBlockStorage，
 * 并按每列 canBlockSeeTheSky 填入 15（露天）/ 0（地下）——与 vanilla 语义一致。
 * init 在主线程（createRebuildTask / WorldSlice.prepare）运行，可安全访问 chunk。
 */
@Mixin(value = ClonedChunkSection.class, remap = false)
public abstract class MixinClonedChunkSectionSkyFix {

    @Redirect(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/world/cloned/ClonedChunkSection;getChunkSection(Lnet/minecraft/world/chunk/Chunk;Lcom/gtnewhorizons/angelica/compat/mojang/ChunkSectionPos;)Lnet/minecraft/world/chunk/storage/ExtendedBlockStorage;"
        )
    )
    private static ExtendedBlockStorage eyeofharmony$getChunkSectionWithSkyFix(
            Chunk chunk, ChunkSectionPos pos) {
        ExtendedBlockStorage section;
        if (!ClonedChunkSection.isOutOfBuildLimitVertically(
            ChunkSectionPos.getBlockCoord(pos.y))) {
            section = chunk.getBlockStorageArray()[pos.y];
        } else {
            section = null;
        }
        if (section == null) {
            // 全空气 section：构造带正确天空光数组的 EBS（vanilla 语义 15/0）
            section = new ExtendedBlockStorage(
                ChunkSectionPos.getBlockCoord(pos.y), true);
            fillSkyLight(section, chunk, pos);
        }
        return section;
    }

    /** 按每列能否见天填入 skylight：露天 15，地下 0（与 Chunk.getSavedLightValue 一致）。 */
    private static void fillSkyLight(ExtendedBlockStorage section,
                                     Chunk chunk, ChunkSectionPos pos) {
        final int yBase = ChunkSectionPos.getBlockCoord(pos.y);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                for (int ly = 0; ly < 16; ly++) {
                    boolean sky = chunk.canBlockSeeTheSky(lx, yBase + ly, lz);
                    section.setExtSkylightValue(lx, ly, lz, sky ? 15 : 0);
                }
            }
        }
    }
}
