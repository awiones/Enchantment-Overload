package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.Milhae77.enchantmentoverload.lighting.DynamicLightHandler;

public class HeadlampEnchantment extends Enchantment {
    private static final HashMap<UUID, Boolean> lightActive = new HashMap<>();
    
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
        return 3; // Now supports 3 levels
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        
        Player player = event.player;
        Level level = player.level();
        UUID playerId = player.getUUID();

        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(this, player);
        if (enchantLevel <= 0) {
            lightActive.remove(playerId);
            DynamicLightHandler.updatePlayerLight(player, 0, false);
            return;
        }

        boolean shouldActivate = shouldActivateLight(player, level);
        lightActive.put(playerId, shouldActivate);
        
        // Update dynamic lighting
        DynamicLightHandler.updatePlayerLight(player, enchantLevel, shouldActivate);
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
}
