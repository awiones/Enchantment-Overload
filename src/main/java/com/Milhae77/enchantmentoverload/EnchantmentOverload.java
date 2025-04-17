package com.Milhae77.enchantmentoverload;

import com.Milhae77.enchantmentoverload.commands.StructureChestCommand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import com.Milhae77.enchantmentoverload.init.ModEnchantments;
import com.Milhae77.enchantmentoverload.events.VillagerKillTracker;
import com.Milhae77.enchantmentoverload.events.ArrowAssistHandler;
import com.Milhae77.enchantmentoverload.lighting.DynamicLightHandler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import com.Milhae77.enchantmentoverload.init.ModSoundEvents;

@Mod(EnchantmentOverload.MOD_ID)
public class EnchantmentOverload {
    public static final String MOD_ID = "enchantmentoverload";

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = 
        DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MOD_ID);

    public static final RegistryObject<SoundEvent> WARDENBANE_ACHIEVE = SOUND_EVENTS.register(
        "wardenbane_achieve", 
        () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MOD_ID, "wardenbane_achieve"))
    );

    public EnchantmentOverload() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEnchantments.register(modEventBus);
        ModSoundEvents.SOUND_EVENTS.register(modEventBus); // Register sounds
        
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new VillagerKillTracker());
        MinecraftForge.EVENT_BUS.register(ArrowAssistHandler.class); // Register ArrowAssistHandler

        // Register lighting updates on server tick
        MinecraftForge.EVENT_BUS.register(new DynamicLightHandler());

        // Register command
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        StructureChestCommand.register(event.getDispatcher());
    }
}
