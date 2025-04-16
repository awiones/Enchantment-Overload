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

@Mod(EnchantmentOverload.MOD_ID)
public class EnchantmentOverload {
    public static final String MOD_ID = "enchantmentoverload";

    public EnchantmentOverload() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEnchantments.register(modEventBus);
        
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new VillagerKillTracker());
        MinecraftForge.EVENT_BUS.register(ArrowAssistHandler.class); // Register ArrowAssistHandler

        // Register command
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        StructureChestCommand.register(event.getDispatcher());
    }
}
