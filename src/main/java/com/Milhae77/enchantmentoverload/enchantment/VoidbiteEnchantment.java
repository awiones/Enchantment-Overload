package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import java.util.Random;
import java.lang.reflect.Method;

public class VoidbiteEnchantment extends Enchantment {
    private static final Random random = new Random();

    public VoidbiteEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.WEAPON, new EquipmentSlot[] {
                EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
        });
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
        return 15 + 20 * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 30;
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        return stack.getItem() == Items.TRIDENT;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return stack.getItem() == Items.TRIDENT;
    }

    @Override
    public boolean isTradeable() {
        return true;
    }

    @Override
    public boolean isDiscoverable() {
        return true;
    }

    @Override
    public boolean isAllowedOnBooks() {
        return true;
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker))
            return;
        LivingEntity target = event.getEntity();

        // Only trigger for player or mob attacks
        ItemStack weapon = attacker.getMainHandItem();
        int level = EnchantmentHelper.getItemEnchantmentLevel(this, weapon);
        if (level <= 0)
            return;

        // Only tridents
        if (weapon.getItem() != Items.TRIDENT)
            return;

        // Check for critical hit (player: falling, not on ground, not climbing, not in
        // water, not blinded, not riding)
        boolean isCritical = false;
        if (attacker instanceof Player player) {
            isCritical = player.fallDistance > 0.0F && !player.onGround() && !player.onClimbable() &&
                    !player.isInWater() && !player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS) &&
                    !player.isPassenger();
        } else {
            // For mobs, treat all attacks as non-critical
            isCritical = false;
        }
        if (!isCritical)
            return;

        // Chance to teleport (15% per level)
        if (random.nextFloat() < (0.15f * level)) {
            // Teleport target behind attacker
            Vec3 look = attacker.getLookAngle().normalize();
            double behindDist = 2.0;
            Vec3 behind = attacker.position().subtract(look.scale(behindDist));
            double y = attacker.getY();
            target.teleportTo(behind.x, y, behind.z);
            // Optional: play sound or particles here
        }
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof ThrownTrident trident))
            return;
        Entity hitEntity = event.getRayTraceResult() instanceof EntityHitResult ehr ? ehr.getEntity() : null;
        if (!(hitEntity instanceof LivingEntity target))
            return;

        // Use reflection to access protected getPickupItem()
        ItemStack tridentStack = ItemStack.EMPTY;
        try {
            Method m = ThrownTrident.class.getDeclaredMethod("getPickupItem");
            m.setAccessible(true);
            tridentStack = (ItemStack) m.invoke(trident);
        } catch (Exception e) {
            // fallback or log error
            return;
        }
        if (tridentStack == null || EnchantmentHelper.getItemEnchantmentLevel(this, tridentStack) <= 0)
            return;

        // Check thrower
        Entity owner = trident.getOwner();
        if (!(owner instanceof Player player))
            return;

        // Only if player was sneaking when thrown
        if (!player.isShiftKeyDown())
            return;

        // Teleport player behind the hit entity
        Vec3 look = target.getLookAngle().normalize();
        double behindDist = 2.0;
        Vec3 behind = target.position().subtract(look.scale(behindDist));
        double y = target.getY();
        player.teleportTo(behind.x, y, behind.z);
        // Optional: play sound or particles here
    }
}
