package com.Milhae77.enchantmentoverload.lighting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import java.util.*;

public class DynamicLightHandler {
    private static final Map<UUID, BlockPos> playerLights = new HashMap<>();

    public static void updatePlayerLight(Player player, int enchantLevel, boolean shouldLight) {
        if (player.level().isClientSide) return;
        
        UUID playerId = player.getUUID();
        Level level = player.level();
        BlockPos currentPos = player.blockPosition().above();

        // Remove old light
        BlockPos oldPos = playerLights.get(playerId);
        if (oldPos != null) {
            if (level.getBlockState(oldPos).getBlock() == Blocks.LIGHT) {
                level.removeBlock(oldPos, false);
            }
            playerLights.remove(playerId);
        }

        // Add new light only if the target block is air
        if (shouldLight && level.getBlockState(currentPos).getBlock() == Blocks.AIR) {
            int lightLevel = Math.min(15, 7 + (enchantLevel * 3));
            BlockState lightBlock = Blocks.LIGHT.defaultBlockState();
            level.setBlock(currentPos, lightBlock, 3);
            playerLights.put(playerId, currentPos);
        }
    }
}
