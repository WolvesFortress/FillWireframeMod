package com.example.fillwireframe;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.BlockStateArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class FillWireframeCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, CommandRegistryAccess registryAccess, CommandManager.RegistrationEnvironment environment) {
        dispatcher.register(
                CommandManager.literal("fillwireframe")
                        .then(CommandManager.argument("from", BlockPosArgumentType.blockPos())
                                .then(CommandManager.argument("to", BlockPosArgumentType.blockPos())
                                        .then(CommandManager.argument("targetBlock", BlockStateArgumentType.blockState(registryAccess))
                                                .executes(ctx -> {
                                                    BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                                                    BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");
                                                    Block targetBlock = BlockStateArgumentType.getBlockState(ctx, "targetBlock").getBlockState().getBlock();
                                                    BlockState fillBlock = net.minecraft.block.Blocks.STONE.getDefaultState();

                                                    ServerWorld world = ctx.getSource().getWorld();
                                                    FillWireframeExecutor.start(world, from, to, targetBlock, fillBlock);

                                                    ctx.getSource().sendFeedback(() -> Text.literal("Started FillWireframe (default stone)"), true);
                                                    return 1;
                                                })
                                                .then(CommandManager.argument("fillBlock", BlockStateArgumentType.blockState(registryAccess))
                                                        .executes(ctx -> {
                                                            BlockPos from = BlockPosArgumentType.getBlockPos(ctx, "from");
                                                            BlockPos to = BlockPosArgumentType.getBlockPos(ctx, "to");
                                                            Block targetBlock = BlockStateArgumentType.getBlockState(ctx, "targetBlock").getBlockState().getBlock();
                                                            BlockState fillBlock = BlockStateArgumentType.getBlockState(ctx, "fillBlock").getBlockState();

                                                            ServerWorld world = ctx.getSource().getWorld();
                                                            FillWireframeExecutor.start(world, from, to, targetBlock, fillBlock);

                                                            ctx.getSource().sendFeedback(() -> Text.literal("Started FillWireframe"), true);
                                                            return 1;
                                                        })))))
        );
    }
}
