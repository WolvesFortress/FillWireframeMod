package com.example.fillwireframe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class FillWireframeExecutor {
    private static final int BLOCKS_PER_TICK = 1000;
    private static Queue<BlockPos> queue = null;
    private static ServerWorld world = null;
    private static BlockState fillBlock = null;

    public static void start(ServerWorld targetWorld, BlockPos from, BlockPos to, Block targetBlock, BlockState insideFillBlock) {
        world = targetWorld;
        fillBlock = insideFillBlock;

        Set<BlockPos> wireframe = new HashSet<>();
        BlockPos.Mutable mutable = new BlockPos.Mutable();

        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    BlockState state = world.getBlockState(mutable);
                    if (state.getBlock() == targetBlock) {
                        wireframe.add(mutable.toImmutable());
                    }
                }
            }
        }

        Set<BlockPos> fillPositions = fastFloodFill(world, wireframe, from, to);

        queue = new ArrayDeque<>(fillPositions);

        ServerTickEvents.END_SERVER_TICK.register(FillWireframeExecutor::tick);
    }

    private static void tick(net.minecraft.server.MinecraftServer server) {
        if (queue == null || world == null) {
            return; // Nothing to do
        }

        for (int i = 0; i < BLOCKS_PER_TICK && !queue.isEmpty(); i++) {
            BlockPos pos = queue.poll();
            if (pos != null) {
                world.setBlockState(pos, fillBlock, Block.NOTIFY_ALL);
            }
        }

        if (queue.isEmpty()) {
            System.out.println("[FillWireframe] Finished filling.");
            queue = null;
            world = null;
            fillBlock = null;
            // No need to unregister from ServerTickEvents
        }
    }

    private static Set<BlockPos> fastFloodFill(ServerWorld world, Set<BlockPos> wireframe, BlockPos from, BlockPos to) {
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int minZ = Math.min(from.getZ(), to.getZ());
        int maxZ = Math.max(from.getZ(), to.getZ());

        Set<BlockPos> visited = new HashSet<>();
        Queue<BlockPos.Mutable> queue = new ArrayDeque<>();
        Set<BlockPos> toFill = new HashSet<>();

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                queue.add(new BlockPos.Mutable(x, minY, z));
                queue.add(new BlockPos.Mutable(x, maxY, z));
            }
        }

        while (!queue.isEmpty()) {
            BlockPos.Mutable pos = queue.poll();
            if (!visited.add(pos.toImmutable())) continue;
            if (wireframe.contains(pos)) continue;
            if (!isInBounds(pos, minX, maxX, minY, maxY, minZ, maxZ)) continue;

            BlockState state = world.getBlockState(pos);
            if (!state.isAir()) continue;

            queue.add(new BlockPos.Mutable(pos.getX() + 1, pos.getY(), pos.getZ()));
            queue.add(new BlockPos.Mutable(pos.getX() - 1, pos.getY(), pos.getZ()));
            queue.add(new BlockPos.Mutable(pos.getX(), pos.getY() + 1, pos.getZ()));
            queue.add(new BlockPos.Mutable(pos.getX(), pos.getY() - 1, pos.getZ()));
            queue.add(new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ() + 1));
            queue.add(new BlockPos.Mutable(pos.getX(), pos.getY(), pos.getZ() - 1));
        }

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    mutable.set(x, y, z);
                    if (!visited.contains(mutable) && !wireframe.contains(mutable)) {
                        BlockState state = world.getBlockState(mutable);
                        if (state.isAir()) {
                            toFill.add(mutable.toImmutable());
                        }
                    }
                }
            }
        }

        return toFill;
    }

    private static boolean isInBounds(BlockPos pos, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        return pos.getX() >= minX && pos.getX() <= maxX &&
                pos.getY() >= minY && pos.getY() <= maxY &&
                pos.getZ() >= minZ && pos.getZ() <= maxZ;
    }
}
