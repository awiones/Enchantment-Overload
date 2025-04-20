package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.LeftClickBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.network.chat.Component;
import java.util.HashMap;
import java.util.UUID;
import java.util.Random;
import java.util.List;

public class EndermanGlaresEnchantment extends Enchantment {
    private static final HashMap<UUID, Long> lastTeleport = new HashMap<>();
    private static final int COOLDOWN_TICKS = 40; // 2 second cooldown
    private static final double TELEPORT_RANGE = 50.0D; // Increased to 50 blocks
    private static final Random random = new Random(); // Add Random instance

    public EndermanGlaresEnchantment() {
        super(Rarity.VERY_RARE, EnchantmentCategory.ARMOR_HEAD,
                new EquipmentSlot[] { EquipmentSlot.HEAD });
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
    public int getMinCost(int level) {
        return 20 + 10 * level;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
    }

    @SubscribeEvent
    public void onLeftClickBlock(LeftClickBlock event) {
        handleTeleport(event.getEntity());
    }

    @SubscribeEvent
    public void onLeftClickEmpty(PlayerInteractEvent.LeftClickEmpty event) {
        handleTeleport(event.getEntity());
    }

    private void handleTeleport(Player player) {
        Level level = player.level();
        UUID playerId = player.getUUID();

        // Debug: Check if event is triggered
        player.displayClientMessage(Component.literal("§eTrying to teleport..."), true);

        if (!player.isCrouching()) {
            player.displayClientMessage(Component.literal("§cNot sneaking!"), true);
            return;
        }

        int enchLevel = player.getItemBySlot(EquipmentSlot.HEAD).getEnchantmentLevel(this);
        if (enchLevel <= 0) {
            player.displayClientMessage(Component.literal("§cNo Enderman Glares enchantment found!"), true);
            return;
        }

        // Check cooldown
        long currentTime = level.getGameTime();
        if (lastTeleport.containsKey(playerId) &&
                currentTime - lastTeleport.get(playerId) < COOLDOWN_TICKS) {
            player.displayClientMessage(Component.literal("§cOn cooldown!"), true);
            return;
        }

        // Get entity player is looking at
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();

        // Create bounding box for entity detection
        Vec3 endPos = eyePos.add(lookVec.scale(TELEPORT_RANGE));
        AABB searchBox = new AABB(
                eyePos.x - 0.5, eyePos.y - 0.5, eyePos.z - 0.5,
                endPos.x + 0.5, endPos.y + 0.5, endPos.z + 0.5);

        // Find closest entity in line of sight
        List<LivingEntity> entities = level.getEntitiesOfClass(
                LivingEntity.class,
                searchBox,
                e -> e != player && e.isAlive());

        LivingEntity target = null;
        double closestDist = Double.MAX_VALUE;

        for (LivingEntity entity : entities) {
            Vec3 dirToEntity = entity.position().subtract(eyePos).normalize();
            double dot = dirToEntity.dot(lookVec);

            // Check if entity is in front of player and within view angle
            if (dot > 0.98) { // About 11 degrees view angle
                double dist = entity.position().distanceTo(eyePos);
                if (dist < closestDist) {
                    target = entity;
                    closestDist = dist;
                }
            }
        }

        if (target == null) {
            player.displayClientMessage(Component.literal("§cNo entity in sight!"), true);
            return;
        }

        // Calculate teleport position behind target
        Vec3 targetPos = target.position();
        Vec3 targetBack = target.getLookAngle().scale(-2.0); // 2 blocks behind target
        Vec3 teleportPos = targetPos.add(targetBack);

        // Force position update
        player.setPos(teleportPos.x, teleportPos.y, teleportPos.z);

        // Stop any existing motion
        player.setDeltaMovement(0, 0, 0);

        // Play sound effect at both old and new positions
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0F, 1.0F);
        level.playSound(null, teleportPos.x, teleportPos.y, teleportPos.z,
                SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS,
                1.0F, 1.0F);

        // Update cooldown and display message
        lastTeleport.put(playerId, currentTime);
        player.displayClientMessage(Component.literal("§aTeleported behind " + target.getName().getString()), true);
    }
}
