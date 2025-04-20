package com.Milhae77.enchantmentoverload.init;

import com.Milhae77.enchantmentoverload.EnchantmentOverload;
import com.Milhae77.enchantmentoverload.enchantment.LifeStealEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.ArrowAssistEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.FireDefenseEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.HeadlampEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.AntiWardenEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.CreeperEscapeEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.VoidbiteEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.FreezeEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.EndermanGlaresEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
        public static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister
                        .create(ForgeRegistries.ENCHANTMENTS, EnchantmentOverload.MOD_ID);

        public static final RegistryObject<Enchantment> LIFE_STEAL = ENCHANTMENTS.register(
                        "life_steal", LifeStealEnchantment::new);

        public static final RegistryObject<Enchantment> ARROW_ASSIST = ENCHANTMENTS.register(
                        "arrow_assist", ArrowAssistEnchantment::new);

        public static final RegistryObject<Enchantment> FIRE_DEFENSE = ENCHANTMENTS.register(
                        "fire_defense", FireDefenseEnchantment::new);

        public static final RegistryObject<Enchantment> HEADLAMP = ENCHANTMENTS.register(
                        "headlamp", HeadlampEnchantment::new);

        public static final RegistryObject<Enchantment> ANTI_WARDEN = ENCHANTMENTS.register(
                        "anti_warden", AntiWardenEnchantment::new);

        public static final RegistryObject<Enchantment> CREEPER_ESCAPE = ENCHANTMENTS.register(
                        "creeper_escape", CreeperEscapeEnchantment::new);

        public static final RegistryObject<Enchantment> VOIDBITE = ENCHANTMENTS.register(
                        "voidbite", VoidbiteEnchantment::new);

        // Register Freeze enchantment
        public static final RegistryObject<Enchantment> FREEZE = ENCHANTMENTS.register(
                        "freeze", FreezeEnchantment::new);

        public static final RegistryObject<Enchantment> ENDERMAN_GLARES = ENCHANTMENTS.register(
                        "enderman_glares", EndermanGlaresEnchantment::new);

        public static void register(IEventBus eventBus) {
                ENCHANTMENTS.register(eventBus);
        }
}
