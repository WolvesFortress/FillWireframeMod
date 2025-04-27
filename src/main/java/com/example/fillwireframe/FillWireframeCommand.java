package com.example.fillwireframe;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class FillWireframeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(
            CommandManager.literal("fillwireframe")
                .then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
                .then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
                .then(CommandManager.argument("targetBlock", BlockStateArgumentType.blockState())
                .executes(ctx -> {
                    BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                    BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");
                    Block targetBlock = BlockStateArgumentType.getBlockState(ctx, "targetBlock").getBlockState().getBlock();
                    BlockState fillBlock = net.minecraft.block.Blocks.STONE.getDefaultState();

                    ServerWorld world = ctx.getSource().getWorld();
                    fillWireframeAsync(world, from, to, targetBlock, fillBlock);

                    ctx.getSource().sendFeedback(() -> Text.literal("Started FillWireframe (default stone)"), true);
                    return 1;
                })
                .then(CommandManager.argument("fillBlock", BlockStateArgumentType.blockState())
                .executes(ctx -> {
                    BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                    BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");
                    Block targetBlock = BlockStateArgumentType.getBlockState(ctx, "targetBlock").getBlockState().getBlock();
                    BlockState fillBlock = BlockStateArgumentType.getBlockState(ctx, "fillBlock").getBlockState();

                    ServerWorld world = ctx.getSource().getWorld();
                    fillWireframeAsync(world, from, to, targetBlock, fillBlock);

                    ctx.getSource().sendFeedback(() -> Text.literal("Started FillWireframe"), true);
                    return 1;
                })))))
        );
    }

    private static void fillWireframeAsync(ServerWorld world, BlockPos from, BlockPos to, Block targetBlock, BlockState fillBlock) {
        MinecraftServer server = world.getServer();

        server.execute(() -> {
            int minX = Math.min(from.getX(), to.getX());
            int maxX = Math.max(from.getX(), to.getX());
            int minY = Math.min(from.getY(), to.getY());
            int maxY = Math.max(from.getY(), to.getY());
            int minZ = Math.min(from.getZ(), to.getZ());
            int maxZ = Math.max(from.getZ(), to.getZ());

            Set<BlockPos> wireframe = new HashSet<>();
            BlockPos.Mutable mutable = new BlockPos.Mutable();

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

            Set<BlockPos> toFill = fastFloodFill(world, wireframe, from, to);

            int batchSize = 1000;
            List<BlockPos> positions = new ArrayList<>(toFill);
            int total = positions.size();
            final int[] index = {0};

            server.getTickScheduler().schedule(() -> {
                int end = Math.min(index[0] + batchSize, total);
                for (int i = index[0]; i < end; i++) {
                    world.setBlockState(positions.get(i), fillBlock, Block.NOTIFY_ALL);
                }
                index[0] = end;
                if (index[0] < total) {
                    server.getTickScheduler().schedule(this);
                } else {
                    System.out.println("[FillWireframe] Completed: " + total + " blocks.");
                }
            });
        });
    }

    private static Set<BlockPos> fastFloodFill(World world, Set<BlockPos> wireframe, BlockPos from, BlockPos to) {
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
