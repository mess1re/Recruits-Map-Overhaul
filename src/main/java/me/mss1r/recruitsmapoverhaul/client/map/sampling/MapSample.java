package me.mss1r.recruitsmapoverhaul.client.map.sampling;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

record MapSample(BlockPos pos, BlockState state, int height) {
}
