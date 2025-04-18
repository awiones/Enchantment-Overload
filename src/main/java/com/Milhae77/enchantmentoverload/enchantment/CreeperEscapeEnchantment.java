package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.Milhae77.enchantmentoverload.EnchantmentOverload;

import java.util.*;

public class CreeperEscapeEnchantment extends Enchantment {
    private static final int SAFE_TICKS = 60; // Time before allowing another escape
    private static final Map<UUID, Integer> creeperTracker = new HashMap<>();
    private static final Map<UUID, Integer> playerCooldowns = new HashMap<>();
    // Track players who have been launched to prevent fall damage
    private static final Map<UUID, Integer> launchedPlayers = new HashMap<>();
    private static final int FALL_PROTECTION_DURATION = 100; // 5 seconds of fall protection
    private static final boolean DEBUG_MODE = false; // Disabled debug messages

    public CreeperEscapeEnchantment() {
        super(Rarity.UNCOMMON, EnchantmentCategory.ARMOR_CHEST, new EquipmentSlot[]{
            EquipmentSlot.CHEST
        });
    }

    @Override
    public int getMinLevel() { return 1; }

    @Override
    public int getMaxLevel() { return 3; }

    @Override
    public boolean isTradeable() {
        return true;  // Can be obtained from villager trading
    }

    @Override
    public boolean isDiscoverable() {
        return true;  // Can be found in enchanting table
    }

    @Override
    public int getMinCost(int level) {
        return 15 + (level - 1) * 10;  // Base enchanting cost
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;  // Cost range
    }

    // Static event handler class to ensure proper registration
    @Mod.EventBusSubscriber(modid = EnchantmentOverload.MOD_ID)
    public static class EventHandler {
        @SubscribeEvent
        public static void onLivingTick(LivingEvent.LivingTickEvent event) {
            if (!(event.getEntity() instanceof Player player)) return;
            if (player.level().isClientSide) return;

            UUID playerId = player.getUUID();
            
            // Update fall protection timer
            if (launchedPlayers.containsKey(playerId)) {
                int remainingTicks = launchedPlayers.get(playerId) - 1;
                if (remainingTicks <= 0) {
                    launchedPlayers.remove(playerId);
                } else {
                    launchedPlayers.put(playerId, remainingTicks);
                }
            }
            
            // Handle cooldown
            if (playerCooldowns.containsKey(playerId)) {
                int cooldown = playerCooldowns.get(playerId) - 1;
                if (cooldown <= 0) {
                    playerCooldowns.remove(playerId);
                } else {
                    playerCooldowns.put(playerId, cooldown);
                }
                return; // Skip detection while on cooldown
            }

            // Check if player has the enchantment
            int enchLevel = getPlayerEnchantmentLevel(player);
            if (enchLevel <= 0) {
                return;
            }

            // Check for nearby creepers
            double detectionRange = 5.0 + enchLevel;
            List<Creeper> nearbyCreepers = player.level().getEntitiesOfClass(
                Creeper.class, 
                player.getBoundingBox().inflate(detectionRange)
            );

            for (Creeper creeper : nearbyCreepers) {
                UUID creeperId = creeper.getUUID();
                
                // Check if creeper is swelling (about to explode)
                if (creeper.getSwellDir() > 0) {
                    // Increment tracker for this creeper
                    int swellTime = creeperTracker.getOrDefault(creeperId, 0) + 1;
                    creeperTracker.put(creeperId, swellTime);
                    
                    // Launch player if creeper is about to explode
                    // Creeper explosion happens around 30 ticks, so launch earlier
                    if (swellTime >= 15) {
                        if (launchPlayerToSafety(player, creeper, enchLevel)) {
                            break; // Only handle one escape at a time
                        }
                    }
                } else {
                    // Reset tracker if creeper stops swelling
                    if (creeperTracker.containsKey(creeperId)) {
                        creeperTracker.remove(creeperId);
                    }
                }
            }

            // Cleanup old trackers
            creeperTracker.entrySet().removeIf(entry -> 
                !nearbyCreepers.stream().anyMatch(c -> c.getUUID().equals(entry.getKey())));
        }

        @SubscribeEvent
        public static void onLivingFall(LivingFallEvent event) {
            if (!(event.getEntity() instanceof Player player)) return;
            
            UUID playerId = player.getUUID();
            
            // Cancel fall damage if player was recently launched
            if (launchedPlayers.containsKey(playerId)) {
                event.setCanceled(true);
                event.setDistance(0.0F); // Reset fall distance
            }
        }

        private static int getPlayerEnchantmentLevel(Player player) {
            // Get all instances of CreeperEscapeEnchantment from the registry
            for (Enchantment enchantment : net.minecraftforge.registries.ForgeRegistries.ENCHANTMENTS) {
                if (enchantment instanceof CreeperEscapeEnchantment) {
                    int level = EnchantmentHelper.getEnchantmentLevel(enchantment, player);
                    if (level > 0) {
                        return level;
                    }
                }
            }
            return 0;
        }

        private static boolean launchPlayerToSafety(Player player, Creeper creeper, int enchLevel) {
            UUID playerId = player.getUUID();
            
            // Don't launch if player is on cooldown
            if (playerCooldowns.containsKey(playerId)) {
                return false;
            }

            // Calculate escape direction (away from creeper)
            Vec3 playerPos = player.position();
            Vec3 creeperPos = creeper.position();
            Vec3 escapeDir = playerPos.subtract(creeperPos).normalize();

            // Calculate launch power based on enchantment level
            double horizontalPower = 1.5 + (enchLevel * 0.7);
            double verticalPower = 0.8 + (enchLevel * 0.3);

            // Apply velocity
            player.setDeltaMovement(
                escapeDir.x * horizontalPower,
                verticalPower,
                escapeDir.z * horizontalPower
            );
            
            // Force the velocity update
            player.hurtMarked = true;

            // Prevent fall damage temporarily
            player.fallDistance = 0;
            
            // Add player to launched players list for fall protection
            launchedPlayers.put(playerId, FALL_PROTECTION_DURATION);
            
            // Set cooldown
            playerCooldowns.put(playerId, SAFE_TICKS);
            
            // Reset creeper tracker
            creeperTracker.remove(creeper.getUUID());
            
            // Play firework launch sound
            playLaunchSound(player, enchLevel);
            
            // Provide minimal feedback without spam
            player.sendSystemMessage(Component.literal("§a§lLaunched away from creeper!"));
            
            return true;
        }
        
        private static void playLaunchSound(Player player, int enchLevel) {
            // Play firework rocket launch sound
            player.level().playSound(
                null, // No player to exclude from hearing the sound
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH,
                SoundSource.PLAYERS,
                1.0F, // Volume
                0.8F + (0.1F * enchLevel) // Pitch - higher pitch for higher enchantment level
            );
            
            // Add a second sound for more impact at higher levels
            if (enchLevel >= 2) {
                player.level().playSound(
                    null,
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    SoundEvents.FIREWORK_ROCKET_BLAST,
                    SoundSource.PLAYERS,
                    0.7F, // Slightly lower volume
                    0.9F + (0.1F * enchLevel)
                );
            }
        }
    }
}