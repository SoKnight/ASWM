package com.grinderwolf.swm.api.world;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.grinderwolf.swm.api.util.NibbleArray;

/**
 * In-memory representation of a SRF chunk section.
 */
public interface SlimeChunkSection {

    /**
     * Returns all the blocks of the chunk section, or <code>null</code>
     * in case it's a post 1.13 world.
     *
     * @return A <code>byte[]</code> with all the blocks of a chunk section.
     */
    byte[] blocks();

    /**
     * Returns the data of all the blocks of the chunk section, or
     * <code>null</code> if it's a post 1.13 world.
     *
     * @return A {@link NibbleArray} containing all the blocks of a chunk section.
     */
    NibbleArray data();

    /**
     * Returns the block palette of the chunk section, or
     * <code>null</code> if it's a pre 1.13 world.
     *
     * @return The block palette, contained inside a {@link ListTag}
     */
    ListTag<CompoundTag> palette();

    /**
     * Returns all the states of the blocks of the chunk section, or
     * <code>null</code> in case it's a pre 1.13 world.
     *
     * @return A <code>long[]</code> with every block state.
     */
    long[] blockStates();

    /**
     * Returns the block light data.
     *
     * @return A {@link NibbleArray} with the block light data.
     */
    NibbleArray blockLight();

    /**
     * Returns the sky light data.
     *
     * @return A {@link NibbleArray} containing the sky light data.
     */
    NibbleArray skyLight();

}
