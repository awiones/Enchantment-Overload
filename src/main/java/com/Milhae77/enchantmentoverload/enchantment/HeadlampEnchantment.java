package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public class HeadlampEnchantment extends Enchantment {
    private static final HashMap<UUID, Boolean> lightActive = new HashMap<>();
    private static final HashMap<UUID, Vec3> lastPosition = new HashMap<>();
    private static final double PARTICLE_SPREAD = 0.3;
    
    public HeadlampEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_HEAD, 
              new EquipmentSlot[]{ EquipmentSlot.HEAD });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 2; // Level 2 provides brighter light and more particles
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Player player = event.player;
        Level level = player.level();
        UUID playerId = player.getUUID();

        // Check if player has the enchantment
        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(this, player);
        if (enchantLevel <= 0) {
            lightActive.remove(playerId);
            return;
        }

        // Check conditions for light activation
        boolean shouldActivate = shouldActivateLight(player, level);
        lightActive.put(playerId, shouldActivate);

        if (shouldActivate && level.isClientSide) {
            spawnLightParticles(player, level, enchantLevel);
        }
    }

    private boolean shouldActivateLight(Player player, Level level) {
        // Check time of day (night time is between 13000 and 23000)
        boolean isNight = level.getDayTime() % 24000 > 13000 && level.getDayTime() % 24000 < 23000;
        
        // Check if player is underground (Y < 60 or in dark area)
        BlockPos pos = player.blockPosition();
        int skyLight = level.getBrightness(LightLayer.SKY, pos);
        int blockLight = level.getBrightness(LightLayer.BLOCK, pos);
        boolean isDark = skyLight < 4 || blockLight < 4;
        
        return isNight || isDark || player.getY() < 60;
    }

    private void spawnLightParticles(Player player, Level level, int enchantLevel) {
        // Update position tracking
        Vec3 currentPos = player.position();
        Vec3 lastPos = lastPosition.getOrDefault(playerId, currentPos);
        lastPosition.put(playerId, currentPos);

        // Calculate movement direction
        Vec3 movement = currentPos.subtract(lastPos);
        double speed = movement.length();

        // Base particle positions (in front of player's head)
        double baseX = player.getX() - Math.sin(player.getYRot() * 0.017453292F) * 0.5;
        double baseZ = player.getZ() + Math.cos(player.getYRot() * 0.017453292F) * 0.5;
        double baseY = player.getY() + player.getEyeHeight() - 0.2;

        // Dynamic particle spread based on movement
        double spread = PARTICLE_SPREAD + (speed * 0.5);
        int particles = 3 + enchantLevel * 2;

        for (int i = 0; i < particles; i++) {
            double offsetX = (level.random.nextDouble() - 0.5) * spread;
            double offsetY = (level.random.nextDouble() - 0.5) * spread;
            double offsetZ = (level.random.nextDouble() - 0.5) * spread;

            level.addParticle(
                ParticleTypes.END_ROD,
                baseX + offsetX,
                baseY + offsetY,
                baseZ + offsetZ,
                0, 0.02, 0
            );
        }

        // Add dynamic light block updates here if using a lighting mod
    }
}
