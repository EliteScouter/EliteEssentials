package com.eliteessentials.util;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.BlockMaterial;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.fluid.Fluid;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.accessor.SectionReader;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * Block and chunk lookups for Hytale 0.7.0+.
 *
 * 0.7.0 moved world storage to chunk sections: World no longer exposes getChunk/getBlockType,
 * and WorldChunk no longer holds block data. Reads now go through a SectionReader over the
 * world's ChunkStore. All methods must be called on the world thread.
 */
public final class BlockUtil {

    private BlockUtil() {}

    /**
     * Returns true if the chunk column containing the given block is loaded.
     */
    public static boolean isChunkLoaded(World world, int blockX, int blockZ) {
        Ref<ChunkStore> ref = world.getChunkStore().getChunkReference(ChunkUtil.indexChunkFromBlock(blockX, blockZ));
        return ref != null && ref.isValid();
    }

    /**
     * Loads (or generates) the chunk column containing the given block.
     * Completes with true if the chunk is available. Completes off the world thread.
     */
    public static CompletableFuture<Boolean> loadChunkAsync(World world, int blockX, int blockZ) {
        long chunkIndex = ChunkUtil.indexChunkFromBlock(blockX, blockZ);
        return world.getChunkStore().getChunkReferenceAsync(chunkIndex)
                .thenApply(ref -> ref != null && ref.isValid());
    }

    /**
     * Returns the block type at a position, or null if the section is not loaded.
     */
    @Nullable
    public static BlockType getBlockType(World world, int x, int y, int z) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        if (!reader.hasStorageInMemory(x, y, z)) return null;
        return BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
    }

    /**
     * Scans downward from fromY to minY and returns the Y of the highest solid block,
     * or null if none was found or the column is not loaded.
     */
    @Nullable
    public static Integer findHighestSolidY(World world, int x, int z, int fromY, int minY) {
        SectionReader reader = new SectionReader(world.getChunkStore());
        for (int y = fromY; y >= minY; y--) {
            if (!reader.hasStorageInMemory(x, y, z)) continue;
            BlockType blockType = BlockType.getAssetMap().getAsset(reader.getBlock(x, y, z));
            if (blockType != null && blockType.getMaterial() == BlockMaterial.Solid) {
                return y;
            }
        }
        return null;
    }

    /**
     * Returns true if the fluid at a position is water or lava.
     * Matches by asset name since fluid IDs are assigned at asset load and are not stable.
     */
    public static boolean isHazardousFluid(SectionReader reader, int x, int y, int z) {
        int fluidId = reader.getFluidId(x, y, z);
        if (fluidId == Fluid.EMPTY_ID) return false;
        Fluid fluid = Fluid.getAssetMap().getAsset(fluidId);
        if (fluid == null || fluid.getId() == null) return false;
        String name = fluid.getId().toLowerCase(Locale.ROOT);
        return name.contains("water") || name.contains("lava");
    }
}
