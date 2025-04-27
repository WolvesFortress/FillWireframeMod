package com.example.fillwireframe;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.*;

public class FillWireframeExecutor {
	private static final int BLOCKS_PER_TICK = 1000;

	private static Queue<BlockPos> queue = null;
	private static ServerWorld world = null;
	private static BlockState fillBlock = null;
	private static ServerPlayerEntity player = null;
	private static int totalBlocks = 0;
	private static int placedBlocks = 0;

	public static void start(ServerWorld targetWorld, BlockPos from, BlockPos to, Block targetBlock, BlockState insideFillBlock, ServerPlayerEntity commandSourcePlayer) {
		world = targetWorld;
		fillBlock = insideFillBlock;
		player = commandSourcePlayer;

		int minX = Math.min(from.getX(), to.getX());
		int maxX = Math.max(from.getX(), to.getX());
		int minY = Math.min(from.getY(), to.getY());
		int maxY = Math.max(from.getY(), to.getY());
		int minZ = Math.min(from.getZ(), to.getZ());
		int maxZ = Math.max(from.getZ(), to.getZ());

		// Organize wireframe blocks by Y level
		Map<Integer, List<BlockPos>> wireframeByLevel = new HashMap<>();

		// Initialize the map for all Y levels in range
		for (int y = minY; y <= maxY; y++) {
			wireframeByLevel.put(y, new ArrayList<>());
		}

		// Collect all wireframe blocks
		BlockPos.Mutable mutable = new BlockPos.Mutable();
		for (int y = minY; y <= maxY; y++) {
			for (int x = minX; x <= maxX; x++) {
				for (int z = minZ; z <= maxZ; z++) {
					mutable.set(x, y, z);
					BlockState state = world.getBlockState(mutable);
					if (state.getBlock() == targetBlock) {
						wireframeByLevel.get(y).add(mutable.toImmutable());
					}
				}
			}
		}

		// Process each Y level and fill polygon
		Set<BlockPos> allFillPositions = new HashSet<>();

		for (int y = minY; y <= maxY; y++) {
			List<BlockPos> wireframeAtLevel = wireframeByLevel.get(y);
			if (wireframeAtLevel.isEmpty()) {
				continue;
			}

			Set<BlockPos> fillPositions = fillPolygonAtLevel(world, wireframeAtLevel, y, minX, maxX, minZ, maxZ);
			allFillPositions.addAll(fillPositions);
		}

		queue = new ArrayDeque<>(allFillPositions);
		totalBlocks = allFillPositions.size();
		placedBlocks = 0;

		if (queue.isEmpty()) {
			player.sendMessage(Text.literal("[FillWireframe] No inside space detected to fill."), false);
		} else {
			player.sendMessage(Text.literal("[FillWireframe] Starting fill operation with " + totalBlocks + " blocks."), false);
			ServerTickEvents.END_SERVER_TICK.register(FillWireframeExecutor::tick);
		}
	}

	private static Set<BlockPos> fillPolygonAtLevel(ServerWorld world, List<BlockPos> wireframePoints, int y, int minX, int maxX, int minZ, int maxZ) {
		Set<BlockPos> toFill = new HashSet<>();

		// Scan each point in the bounding box
		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				// Skip points that are already part of the wireframe
				BlockPos pos = new BlockPos(x, y, z);
				if (wireframePoints.contains(pos)) {
					continue;
				}

				// Skip non-air blocks
				if (!world.getBlockState(pos).isAir()) {
					continue;
				}

				// Check if this point is inside the polygon formed by the wireframe
				if (isPointInPolygon(x, z, wireframePoints)) {
					toFill.add(pos);
				}
			}
		}

		return toFill;
	}

	// Implements the ray casting algorithm to determine if a point is inside a polygon
	private static boolean isPointInPolygon(int x, int z, List<BlockPos> polygonPoints) {
		boolean inside = false;
		int j = polygonPoints.size() - 1;

		for (int i = 0; i < polygonPoints.size(); i++) {
			BlockPos pi = polygonPoints.get(i);
			BlockPos pj = polygonPoints.get(j);

			// Check if the point is on an edge
			if (pi.getX() == x && pi.getZ() == z) {
				return true;
			}

			// Ray casting algorithm
			if ((pi.getZ() > z) != (pj.getZ() > z) &&
					(x < (pj.getX() - pi.getX()) * (z - pi.getZ()) / (pj.getZ() - pi.getZ()) + pi.getX())) {
				inside = !inside;
			}

			j = i;
		}

		return inside;
	}

	private static void tick(net.minecraft.server.MinecraftServer server) {
		if (queue == null || world == null) {
			return;
		}

		int placedThisTick = 0;
		while (!queue.isEmpty() && placedThisTick < BLOCKS_PER_TICK) {
			BlockPos pos = queue.poll();
			if (pos != null) {
				world.setBlockState(pos, fillBlock, Block.NOTIFY_ALL);
				placedThisTick++;
				placedBlocks++;
			}
		}

		if (queue.isEmpty()) {
			if (player != null) {
				player.sendMessage(Text.literal("[FillWireframe] Finished! " + placedBlocks + " blocks placed."), false);
			}
			queue = null;
			world = null;
			fillBlock = null;
			player = null;
			totalBlocks = 0;
			placedBlocks = 0;
//			ServerTickEvents.END_SERVER_TICK.unregister(FillWireframeExecutor::tick);
		} else if (placedThisTick > 0 && player != null && placedBlocks % 10000 == 0) {
			// Progress update for large fills
			int percentComplete = (placedBlocks * 100) / totalBlocks;
			player.sendMessage(Text.literal("[FillWireframe] Progress: " + percentComplete + "% (" +
					placedBlocks + "/" + totalBlocks + " blocks)"), false);
		}
	}
}