package com.Milhae77.enchantmentoverload.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import com.Milhae77.enchantmentoverload.init.ModEnchantments;
import net.minecraft.network.chat.Component;
import java.util.Random;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;

public class StructureChestCommand {
    private static final Random RANDOM = new Random();

    // Define rarity tiers for structures
    private static final Map<String, Double> STRUCTURE_RARITY = Map.of(
        "stronghold", 0.60,      // 60% chance for rare items
        "ancient_city", 0.75,    // 75% chance for rare items
        "mineshaft", 0.30,       // 30% chance for rare items
        "desert_pyramid", 0.45,  // 45% chance for rare items
        "jungle_temple", 0.50,   // 50% chance for rare items
        "end_city", 0.85        // 85% chance for rare items
    );

    private static final Map<String, List<ItemStack>> STRUCTURE_LOOT = new HashMap<>() {{
        put("stronghold", Arrays.asList(
            new ItemStack(Items.IRON_INGOT, randomCount(2, 5)),
            new ItemStack(Items.GOLDEN_APPLE, randomCount(1, 2)),
            new ItemStack(Items.ENDER_PEARL, randomCount(1, 3)),
            new ItemStack(Items.DIAMOND, randomCount(1, 3)),
            new ItemStack(Items.EMERALD, randomCount(1, 3)),
            new ItemStack(Items.ENCHANTED_BOOK), // Will be enchanted based on rarity
            new ItemStack(Items.EXPERIENCE_BOTTLE, randomCount(2, 5))
        ));
        
        put("ancient_city", Arrays.asList(
            new ItemStack(Items.ECHO_SHARD, randomCount(2, 4)),
            new ItemStack(Items.SCULK_SENSOR, randomCount(1, 2)),
            new ItemStack(Items.DISC_FRAGMENT_5),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
            new ItemStack(Items.DIAMOND, randomCount(2, 4)),
            new ItemStack(Items.NETHERITE_SCRAP),
            new ItemStack(Items.RECOVERY_COMPASS)
        ));
        
        put("mineshaft", Arrays.asList(
            new ItemStack(Items.RAIL, randomCount(8, 16)),
            new ItemStack(Items.TORCH, randomCount(4, 8)),
            new ItemStack(Items.COAL, randomCount(4, 8)),
            new ItemStack(Items.IRON_PICKAXE),
            new ItemStack(Items.IRON_INGOT, randomCount(2, 4)),
            new ItemStack(Items.REDSTONE, randomCount(4, 8)),
            new ItemStack(Items.LAPIS_LAZULI, randomCount(4, 8))
        ));

        put("desert_pyramid", Arrays.asList(
            new ItemStack(Items.GOLD_INGOT, randomCount(2, 5)),
            new ItemStack(Items.EMERALD, randomCount(1, 3)),
            new ItemStack(Items.BONE, randomCount(4, 8)),
            new ItemStack(Items.SPIDER_EYE, randomCount(2, 4)),
            new ItemStack(Items.ENCHANTED_BOOK),
            new ItemStack(Items.TNT, randomCount(2, 4)),
            new ItemStack(Items.GOLDEN_APPLE)
        ));

        put("jungle_temple", Arrays.asList(
            new ItemStack(Items.DIAMOND, randomCount(1, 2)),
            new ItemStack(Items.BAMBOO, randomCount(4, 8)),
            new ItemStack(Items.EMERALD, randomCount(2, 4)),
            new ItemStack(Items.ARROW, randomCount(8, 16)),
            new ItemStack(Items.ENCHANTED_BOOK),
            new ItemStack(Items.DISPENSER),
            new ItemStack(Items.CROSSBOW)
        ));

        put("end_city", Arrays.asList(
            new ItemStack(Items.DIAMOND, randomCount(3, 6)),
            new ItemStack(Items.SHULKER_SHELL, randomCount(2, 4)),
            new ItemStack(Items.ELYTRA),
            new ItemStack(Items.ENCHANTED_GOLDEN_APPLE),
            new ItemStack(Items.DRAGON_BREATH, randomCount(1, 2)),
            new ItemStack(Items.END_CRYSTAL),
            new ItemStack(Items.NETHERITE_INGOT)
        ));
    }};

    private static int randomCount(int min, int max) {
        return RANDOM.nextInt(max - min + 1) + min;
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_ENCHANTMENTS = (context, builder) -> {
        return suggestEnchantments(builder);
    };

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_STRUCTURES = (context, builder) -> {
        return suggestStructures(builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("structure-chest")
            .requires(source -> source.hasPermission(2))
            .then(Commands.argument("enchantment", StringArgumentType.word())
                .suggests(SUGGEST_ENCHANTMENTS)
                .then(Commands.argument("structure", StringArgumentType.word())
                    .suggests(SUGGEST_STRUCTURES)
                    .executes(context -> {
                        String enchantment = StringArgumentType.getString(context, "enchantment");
                        String structure = StringArgumentType.getString(context, "structure");
                        return spawnStructureChest(context.getSource(), enchantment, structure);
                    }))));
    }

    private static CompletableFuture<Suggestions> suggestEnchantments(SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        if ("life_steal".startsWith(input)) {
            builder.suggest("life_steal", () -> "Spawns a chest with Life Steal enchanted book");
        }
        if ("arrow_assist".startsWith(input)) {
            builder.suggest("arrow_assist", () -> "Spawns a chest with Arrow Assist enchanted book");
        }
        // Add more enchantments here when they are added to the mod
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestStructures(SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        record StructureInfo(String name, String description) {}
        
        StructureInfo[] structureInfos = {
            new StructureInfo("stronghold", "Stronghold loot (60% rare chance)"),
            new StructureInfo("ancient_city", "Ancient City loot (75% rare chance)"),
            new StructureInfo("mineshaft", "Mineshaft loot (30% rare chance)"),
            new StructureInfo("desert_pyramid", "Desert Pyramid loot (45% rare chance)"),
            new StructureInfo("jungle_temple", "Jungle Temple loot (50% rare chance)"),
            new StructureInfo("end_city", "End City loot (85% rare chance)")
        };
        
        for (StructureInfo info : structureInfos) {
            if (info.name.startsWith(input)) {
                builder.suggest(info.name, () -> info.description);
            }
        }
        return builder.buildFuture();
    }

    private static int spawnStructureChest(CommandSourceStack source, String enchantment, String structure) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return 0;
        }

        BlockPos pos = new BlockPos((int)player.getX(), (int)player.getY(), (int)player.getZ()).offset(2, 0, 0);
        
        BlockState chestState = Blocks.CHEST.defaultBlockState();
        player.level().setBlock(pos, chestState, 3);

        if (player.level().getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            // Get rarity chance for structure
            double rarityChance = STRUCTURE_RARITY.getOrDefault(structure.toLowerCase(), 0.3);
            
            // Create enchanted book with level based on structure rarity
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            int level = getEnchantmentLevel(enchantment, rarityChance);
            
            if (level > 0) {
                switch (enchantment.toLowerCase()) {
                    case "life_steal" -> EnchantmentHelper.setEnchantments(
                        java.util.Map.of(ModEnchantments.LIFE_STEAL.get(), level),
                        enchantedBook
                    );
                    case "arrow_assist" -> EnchantmentHelper.setEnchantments(
                        java.util.Map.of(ModEnchantments.ARROW_ASSIST.get(), level),
                        enchantedBook
                    );
                    default -> {
                        source.sendFailure(Component.literal("Unknown enchantment: " + enchantment));
                        return 0;
                    }
                }
            }

            // Add book to first slot
            chest.setItem(0, enchantedBook);

            // Get and add structure-specific loot
            List<ItemStack> loot = STRUCTURE_LOOT.getOrDefault(structure.toLowerCase(), List.of());
            if (loot.isEmpty()) {
                source.sendFailure(Component.literal("Unknown structure: " + structure));
                return 0;
            }

            // Add random loot items to random slots
            for (ItemStack item : loot) {
                if (RANDOM.nextDouble() < 0.7) { // 70% chance for each item to appear
                    int slot = RANDOM.nextInt(26) + 1; // Random slot (1-26, keeping 0 for book)
                    chest.setItem(slot, item.copy());
                }
            }

            source.sendSuccess(() -> Component.literal(String.format(
                "Spawned %s chest with %s %d book and structure loot (%.0f%% rare chance)", 
                structure, enchantment, level, rarityChance * 100)), true);
            return 1;
        }

        return 0;
    }

    private static int getEnchantmentLevel(String enchantment, double rarityChance) {
        double roll = RANDOM.nextDouble();
        if (roll > rarityChance) return 1; // Base level
        
        if (enchantment.equals("life_steal")) {
            if (roll > 0.95) return 5;      // 5% chance for level 5
            if (roll > 0.85) return 4;      // 10% chance for level 4
            if (roll > 0.70) return 3;      // 15% chance for level 3
            return 2;                       // 30% chance for level 2
        }
        
        return 1; // Arrow assist is always level 1
    }
}
