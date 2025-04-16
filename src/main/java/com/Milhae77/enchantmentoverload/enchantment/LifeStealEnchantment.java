package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.UUID;

public class LifeStealEnchantment extends Enchantment {
    private final HashMap<UUID, Long> cooldowns = new HashMap<>();
    private static final long COOLDOWN_TIME = 250; // 250ms cooldown between heals

    public LifeStealEnchantment() {
        super(Enchantment.Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 5;
    }

    @Override
    public int getMinCost(int level) {
        return 10 + 20 * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return super.getMinCost(level) + 50;
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer attacker) {
            int level = EnchantmentHelper.getItemEnchantmentLevel(this, attacker.getMainHandItem());
            
            if (level > 0 && event.getAmount() > 0) {
                // Grant advancement
                ResourceLocation advancementId = new ResourceLocation("enchantmentoverload", "life_steal");
                Advancement advancement = attacker.server.getAdvancements().getAdvancement(advancementId);
                if (advancement != null) {
                    AdvancementProgress progress = attacker.getAdvancements().getOrStartProgress(advancement);
                    if (!progress.isDone()) {
                        attacker.getAdvancements().award(advancement, "life_steal_trigger");
                    }
                }

                float healAmount = level * 1.6f;
                attacker.heal(healAmount);
                
                // Visual effects
                if (attacker.level() instanceof ServerLevel serverLevel) {
                    double x = attacker.getX();
                    double y = attacker.getY() + 1.5; // Move particles higher above the player
                    double z = attacker.getZ();
                    
                    // Spawn fewer particles in a smaller pattern
                    for (int i = 0; i < 5; i++) { // Reduced from 10 to 5 particles
                        double angle = i * Math.PI * 0.4; // Increased angle spacing
                        double offsetX = Math.cos(angle) * 0.3; // Reduced radius from 0.5 to 0.3
                        double offsetZ = Math.sin(angle) * 0.3;
                        Vec3 pos = new Vec3(x + offsetX, y, z + offsetZ);
                        
                        // Only spawn heart particles
                        serverLevel.sendParticles(ParticleTypes.HEART,
                                pos.x, pos.y, pos.z,
                                1, // count
                                0.0, 0.0, 0.0, // no motion
                                0.0); // speed
                    }
                }
            }
        }
    }
}
