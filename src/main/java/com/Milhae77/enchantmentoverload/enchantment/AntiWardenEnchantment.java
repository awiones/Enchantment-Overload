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

public class AntiWardenEnchantment extends Enchantment {
    
    // Define sound resource location as a constant for consistency
    private static final ResourceLocation WARDENBANE_SOUND = new ResourceLocation("enchantmentoverload", "wardenbane_achieve");
    
    // Add sound delay tracking
    private static final HashMap<UUID, Integer> SOUND_DELAY = new HashMap<>();
    private static final int DELAY_TICKS = 100; // 5 seconds (20 ticks per second)
    
    public AntiWardenEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR, 
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
        return 20 + (level - 1) * 10;  // High enchantment cost
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 15;
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other);
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
                        // Play the sound
                        player.level().playSound(null, 
                            player.getX(), player.getY(), player.getZ(),
                            EnchantmentOverload.WARDENBANE_ACHIEVE.get(),
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F);
                    }
                }
                return true; // Remove from map
            } else {
                entry.setValue(remainingTicks);
                return false;
            }
        });
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (!(event.getSource().getEntity() instanceof Warden)) return;

        int totalLevel = EnchantmentHelper.getEnchantmentLevel(this, victim);
        if (totalLevel > 0) {
            // If victim is a player, grant advancement and play sound
            if (victim instanceof ServerPlayer player) {
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
                        player.level().playSound(null, 
                            player.getX(), player.getY(), player.getZ(),
                            soundEvent,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F);
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
