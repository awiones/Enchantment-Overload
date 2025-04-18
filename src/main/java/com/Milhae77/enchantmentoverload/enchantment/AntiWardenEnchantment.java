package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import com.Milhae77.enchantmentoverload.EnchantmentOverload;
import net.minecraftforge.event.TickEvent;
import java.util.HashMap;
import java.util.UUID;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.Milhae77.enchantmentoverload.init.ModSoundEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public class AntiWardenEnchantment extends Enchantment {
    
    // Define sound resource location as a constant for consistency
    private static final ResourceLocation WARDENBANE_SOUND = new ResourceLocation("enchantmentoverload", "wardenbane_achieve");
    
    // Add sound delay tracking
    private static final HashMap<UUID, Integer> SOUND_DELAY = new HashMap<>();
    private static final int DELAY_TICKS = 100; // 5 seconds (20 ticks per second)
    
    // Add particle effect tracking
    private static final HashMap<UUID, ParticleEffectData> PARTICLE_EFFECTS = new HashMap<>();
    private static final int PARTICLE_DURATION = 60; // 3 seconds of particle effects
    private static final Random RANDOM = new Random();
    
    public AntiWardenEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR, 
              new EquipmentSlot[]{ EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 3;
    }

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
        return 35 + (level - 1) * 20;  // Even more expensive base cost
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;  // Increased cost range
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
    }

    // Add method to increase anvil repair cost
    public int getRepairCost() {
        return 12; // Double the default repair cost (was 6)
    }

    // Class to store particle effect data
    private static class ParticleEffectData {
        private int remainingTicks;
        private final int enchantmentLevel;
        
        public ParticleEffectData(int duration, int level) {
            this.remainingTicks = duration;
            this.enchantmentLevel = level;
        }
        
        public boolean update() {
            return --remainingTicks <= 0;
        }
        
        public int getEnchantmentLevel() {
            return enchantmentLevel;
        }
        
        public int getRemainingTicks() {
            return remainingTicks;
        }
    }

    private static class FadingSoundInstance extends AbstractTickableSoundInstance {
        private int fadeInTicks = 20; // 1 second fade in
        private int sustainTicks = 40; // 2 seconds sustain
        private int fadeOutTicks = 20; // 1 second fade out
        private int totalTicks = 0;
        private final float maxVolume;

        public FadingSoundInstance(SoundEvent sound, float volume, double x, double y, double z) {
            super(sound, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.maxVolume = volume;
            this.volume = 0.0F;
            this.looping = false;
            this.delay = 0;
            this.relative = false;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public void tick() {
            totalTicks++;
            if (totalTicks <= fadeInTicks) {
                // Fade in
                volume = (float)totalTicks / fadeInTicks * maxVolume;
            } else if (totalTicks <= fadeInTicks + sustainTicks) {
                // Sustain at full volume
                volume = maxVolume;
            } else if (totalTicks <= fadeInTicks + sustainTicks + fadeOutTicks) {
                // Fade out
                float fadeOutProgress = (float)(totalTicks - fadeInTicks - sustainTicks) / fadeOutTicks;
                volume = maxVolume * (1.0F - fadeOutProgress);
            } else {
                // Sound complete
                stop();
            }
        }
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        
        // Check if the equipment change involved armor
        if (!event.getSlot().isArmor()) return;
        
        // Check if the new equipment has Anti-Warden enchantment
        if (EnchantmentHelper.getEnchantments(event.getTo()).containsKey(this)) {
            // Grant the advancement
            ResourceLocation advancementId = new ResourceLocation("enchantmentoverload", "anti_warden");
            var advancement = player.getServer().getAdvancements().getAdvancement(advancementId);
            if (advancement != null) {
                var progress = player.getAdvancements().getOrStartProgress(advancement);
                if (!progress.isDone()) {
                    for (String criterion : progress.getRemainingCriteria()) {
                        player.getAdvancements().award(advancement, criterion);
                    }
                    
                    // Schedule sound to play after delay
                    SOUND_DELAY.put(player.getUUID(), DELAY_TICKS);
                }
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        // Process delayed sounds
        SOUND_DELAY.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            int remainingTicks = entry.getValue() - 1;
            
            if (remainingTicks <= 0) {
                // Time to play the sound
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    if (player != null) {
                        Level level = player.getCommandSenderWorld();
                        if (level instanceof ServerLevel serverLevel) {
                            // Play the sound
                            serverLevel.playSound(null, 
                                player.getX(), player.getY(), player.getZ(),
                                EnchantmentOverload.WARDENBANE_ACHIEVE.get(),
                                SoundSource.PLAYERS,
                                1.0F,
                                1.0F);
                        }
                    }
                }
                return true; // Remove from map
            } else {
                entry.setValue(remainingTicks);
                return false;
            }
        });
        
        // Process particle effects
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            Iterator<Map.Entry<UUID, ParticleEffectData>> iterator = PARTICLE_EFFECTS.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UUID, ParticleEffectData> entry = iterator.next();
                UUID playerId = entry.getKey();
                ParticleEffectData effectData = entry.getValue();
                
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player != null) {
                    Level level = player.getCommandSenderWorld();
                    if (level instanceof ServerLevel serverLevel) {
                        // Spawn particles around the player
                        spawnAntiWardenParticles(player, serverLevel, effectData);
                        
                        // Update duration and remove if expired
                        if (effectData.update()) {
                            iterator.remove();
                        }
                    }
                } else {
                    // Player not found, remove the effect
                    iterator.remove();
                }
            }
        }
    }
    
    /**
     * Spawns particles around a player to visualize the Anti-Warden effect
     */
    private void spawnAntiWardenParticles(ServerPlayer player, ServerLevel serverLevel, ParticleEffectData effectData) {
        // Get player position
        double x = player.getX();
        double y = player.getY() + player.getEyeHeight() / 2;
        double z = player.getZ();
        
        int level = effectData.getEnchantmentLevel();
        int remainingTicks = effectData.getRemainingTicks();
        
        // Calculate particle density based on enchantment level and remaining time
        int particleCount = 2 + level * 2;
        float intensity = Math.min(1.0f, remainingTicks / 20.0f); // Fade out in the last second
        
        // Warden-themed particles - soul particles and dark blue/teal dust
        for (int i = 0; i < particleCount; i++) {
            // Create a sphere of particles around the player
            double angle = RANDOM.nextDouble() * Math.PI * 2;
            double verticalAngle = RANDOM.nextDouble() * Math.PI * 2;
            double radius = 0.8 + RANDOM.nextDouble() * 0.5; // Vary the radius slightly
            
            double offsetX = radius * Math.sin(verticalAngle) * Math.cos(angle);
            double offsetY = radius * Math.cos(verticalAngle);
            double offsetZ = radius * Math.sin(verticalAngle) * Math.sin(angle);
            
            // Add some randomness to particle velocity
            double speedX = (RANDOM.nextDouble() - 0.5) * 0.05;
            double speedY = (RANDOM.nextDouble() - 0.5) * 0.05;
            double speedZ = (RANDOM.nextDouble() - 0.5) * 0.05;
            
            // Alternate between soul particles and dust particles
            if (i % 2 == 0) {
                // Soul particles
                serverLevel.sendParticles(
                    ParticleTypes.SOUL,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, // count
                    speedX, speedY, speedZ,
                    0.01 * intensity // speed
                );
            } else {
                // Warden-colored dust particles (dark teal/blue)
                float r = 0.0f;
                float g = 0.3f + RANDOM.nextFloat() * 0.1f; // Teal-ish
                float b = 0.5f + RANDOM.nextFloat() * 0.2f; // Blue-ish
                float scale = 0.7f + RANDOM.nextFloat() * 0.5f; // Size variation
                
                DustParticleOptions dustOptions = new DustParticleOptions(
                    new Vector3f(r, g, b), scale * intensity
                );
                
                serverLevel.sendParticles(
                    dustOptions,
                    x + offsetX, y + offsetY, z + offsetZ,
                    1, // count
                    speedX, speedY, speedZ,
                    0.01 // speed
                );
            }
        }
        
        // Add some sonic boom particles if the effect is still strong
        if (remainingTicks > PARTICLE_DURATION / 2 && RANDOM.nextInt(10) == 0) {
            serverLevel.sendParticles(
                ParticleTypes.SONIC_BOOM,
                x, y, z,
                1, // count
                0, 0, 0,
                0 // speed
            );
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (!(event.getSource().getEntity() instanceof Warden)) return;

        int totalLevel = EnchantmentHelper.getEnchantmentLevel(this, victim);
        if (totalLevel > 0) {
            // Consume player XP when protection is triggered
            if (victim instanceof ServerPlayer player) {
                int xpCost = 3 * totalLevel; // 3/6/9 XP points based on enchantment level
                if (player.experienceLevel > 0 || player.experienceProgress > 0) {
                    player.giveExperiencePoints(-xpCost); // Deduct XP
                    
                    Level level = player.getCommandSenderWorld();
                    if (level instanceof ServerLevel serverLevel) {
                        // Use consistent advancement ID
                        ResourceLocation firstUseAdvancementId = new ResourceLocation("enchantmentoverload", "wardenbane_first_use");
                        var advancement = player.getServer().getAdvancements().getAdvancement(firstUseAdvancementId);
                        
                        if (advancement != null) {
                            var progress = player.getAdvancements().getOrStartProgress(advancement);
                            if (!progress.isDone()) {
                                // Grant advancement
                                for (String criterion : progress.getRemainingCriteria()) {
                                    player.getAdvancements().award(advancement, criterion);
                                }
                                
                                // Play sound using the same method as in onEquipmentChange
                                SoundEvent soundEvent = SoundEvent.createVariableRangeEvent(WARDENBANE_SOUND);
                                serverLevel.playSound(null, 
                                    player.getX(), player.getY(), player.getZ(),
                                    soundEvent,
                                    SoundSource.PLAYERS,
                                    1.0F,
                                    1.0F);
                            }
                        }
                        
                        // Add particle effect when hit by a Warden
                        PARTICLE_EFFECTS.put(player.getUUID(), new ParticleEffectData(PARTICLE_DURATION, totalLevel));
                    }
                }
            }

            // Calculate damage reduction
            float reduction = 0.15f * totalLevel;
            float newDamage = event.getAmount() * (1.0f - reduction);
            event.setAmount(newDamage);
        }
    }
}