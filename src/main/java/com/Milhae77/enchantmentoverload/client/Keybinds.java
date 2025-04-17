package com.Milhae77.enchantmentoverload.client;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "enchantmentoverload", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class Keybinds {
    public static final KeyMapping ARROW_ASSIST_KEY = new KeyMapping(
            "key.enchantmentoverload.arrow_assist",
            GLFW.GLFW_KEY_Z,
            "category.enchantmentoverload.name"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ARROW_ASSIST_KEY);
    }
}
