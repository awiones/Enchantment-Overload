package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class FreezeEnchantment extends Enchantment {
    public FreezeEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.BOW,
                new EquipmentSlot[] { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack) {
        // Allow on bows and crossbows
        return stack.is(Items.BOW) || stack.is(Items.CROSSBOW);
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
        return 10 + 15 * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 20;
    }

    @SubscribeEvent
    public void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        Level level = proj.level();
        if (level.isClientSide)
            return;

        if (!(proj instanceof AbstractArrow arrow))
            return;
        if (!(arrow.getOwner() instanceof LivingEntity shooter))
            return;

        int freezeLevel = Math.max(
                EnchantmentHelper.getItemEnchantmentLevel(this, shooter.getMainHandItem()),
                EnchantmentHelper.getItemEnchantmentLevel(this, shooter.getOffhandItem()));
        if (freezeLevel <= 0)
            return;

        if (event.getRayTraceResult() instanceof net.minecraft.world.phys.EntityHitResult hit) {
            if (hit.getEntity() instanceof LivingEntity target) {
                // Freeze entity: set movement speed to 0 for 3 seconds (60 ticks)
                target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 255, false, true, true));
                target.addEffect(new MobEffectInstance(MobEffects.JUMP, 60, 128, false, true, true)); // Prevent jumping

                // Only pick positions around (not below) the entity
                BlockPos base = target.blockPosition();
                List<BlockPos> possible = Arrays.asList(
                        base.north(), base.south(), base.east(), base.west(), base.above());
                Collections.shuffle(possible);
                int placed = 0;
                for (BlockPos pos : possible) {
                    if (placed >= 2)
                        break;
                    // Do NOT place ice under the entity
                    if (pos.equals(base.below()))
                        continue;
                    if (level.isEmptyBlock(pos) && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                        level.setBlockAndUpdate(pos, Blocks.ICE.defaultBlockState());
                        placed++;
                    }
                }
            }
        }
    }
}
