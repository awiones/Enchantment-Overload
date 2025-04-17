package com.Milhae77.enchantmentoverload.events;

import com.Milhae77.enchantmentoverload.init.ModEnchantments;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.HashMap;
import java.util.UUID;

public class VillagerKillTracker {
    private static final HashMap<UUID, Integer> villagerKills = new HashMap<>();
    
    @SubscribeEvent
    public void onVillagerKill(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Villager)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;
        
        // Check if kill was done with Life Steal enchanted weapon
        if (event.getSource().getDirectEntity() != event.getSource().getEntity()) return; // Reject indirect/projectile kills
        
        var mainHand = player.getMainHandItem();
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.LIFE_STEAL.get(), mainHand) > 0) {
            UUID playerId = player.getUUID();
            int kills = villagerKills.getOrDefault(playerId, 0) + 1;
            villagerKills.put(playerId, kills);
            
            if (kills >= 50) {
                ResourceLocation advancementId = new ResourceLocation("enchantmentoverload", "traitor_of_humanity");
                Advancement advancement = player.getServer().getAdvancements().getAdvancement(advancementId);
                if (advancement != null) {
                    AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
                    if (!progress.isDone()) {
                        player.getAdvancements().award(advancement, "kill_villagers");
                    }
                }
            }
        }
    }
}
