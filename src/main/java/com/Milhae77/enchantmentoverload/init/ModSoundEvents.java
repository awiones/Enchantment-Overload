package com.Milhae77.enchantmentoverload.init;

import com.Milhae77.enchantmentoverload.EnchantmentOverload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSoundEvents {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = 
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EnchantmentOverload.MOD_ID);

    public static final RegistryObject<SoundEvent> WARDENBANE_ACHIEVE = SOUND_EVENTS.register(
        "wardenbane_achieve",
        () -> SoundEvent.createVariableRangeEvent(
            new ResourceLocation(EnchantmentOverload.MOD_ID, "wardenbane_achieve")
        )
    );
}
