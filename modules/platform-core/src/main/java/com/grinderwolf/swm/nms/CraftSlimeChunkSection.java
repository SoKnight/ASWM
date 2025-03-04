package com.grinderwolf.swm.nms;

import com.flowpowered.nbt.CompoundTag;
import com.flowpowered.nbt.ListTag;
import com.grinderwolf.swm.api.util.NibbleArray;
import com.grinderwolf.swm.api.world.SlimeChunkSection;

/**
 * @param blocks  Pre 1.13 block data
 * @param palette Post 1.13 block data
 */
public record CraftSlimeChunkSection(
        byte[] blocks,
        NibbleArray data,
        ListTag<CompoundTag> palette,
        long[] blockStates,
        NibbleArray blockLight,
        NibbleArray skyLight
) implements SlimeChunkSection {

}
