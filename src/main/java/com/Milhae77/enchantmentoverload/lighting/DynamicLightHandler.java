package com.Milhae77.enchantmentoverload.lighting;

import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

public class DynamicLightHandler {
    @SubscribeEvent
    public void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
    }

    public static void updatePlayerLight(Player player, int enchantLevel, boolean shouldLight) {
        if (player.level().isClientSide) return; // Only run on server side
        
        BlockPos pos = player.blockPosition();
        Level level = player.level();

        // Remove old light if exists
        if (level.getBlockState(pos).getBlock() == Blocks.LIGHT) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        // Add new light if should be active
        if (shouldLight) {
            int lightLevel = Math.min(15, 7 + (enchantLevel * 3)); // Level 1: 10, Level 2: 13, Level 3: 15
            BlockState lightBlock = Blocks.LIGHT.defaultBlockState();
            level.setBlock(pos, lightBlock, 3);
        }
    }
}
