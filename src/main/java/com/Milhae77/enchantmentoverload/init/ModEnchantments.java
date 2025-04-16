package com.Milhae77.enchantmentoverload.init;

import com.Milhae77.enchantmentoverload.EnchantmentOverload;
import com.Milhae77.enchantmentoverload.enchantment.LifeStealEnchantment;
import com.Milhae77.enchantmentoverload.enchantment.ArrowAssistEnchantment;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS = 
        DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, EnchantmentOverload.MOD_ID);

    public static final RegistryObject<Enchantment> LIFE_STEAL = ENCHANTMENTS.register(
        "life_steal", LifeStealEnchantment::new);

    public static final RegistryObject<Enchantment> ARROW_ASSIST = ENCHANTMENTS.register(
        "arrow_assist", ArrowAssistEnchantment::new);

    public static void register(IEventBus eventBus) {
        ENCHANTMENTS.register(eventBus);
    }
}
