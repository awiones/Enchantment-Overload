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
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.concurrent.CompletableFuture;

public class StructureChestCommand {
    private static final Random RANDOM = new Random();

    // Structure-specific loot tables
    private static final List<ItemStack> STRONGHOLD_LOOT = Arrays.asList(
        new ItemStack(Items.IRON_INGOT, randomCount(1, 3)),
        new ItemStack(Items.GOLDEN_APPLE),
        new ItemStack(Items.ENDER_PEARL),
        new ItemStack(Items.DIAMOND, randomCount(1, 2))
    );

    private static final List<ItemStack> ANCIENT_CITY_LOOT = Arrays.asList(
        new ItemStack(Items.ECHO_SHARD, randomCount(1, 2)),
        new ItemStack(Items.SCULK_SENSOR),
        new ItemStack(Items.DISC_FRAGMENT_5),
        new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)
    );

    private static final List<ItemStack> MINESHAFT_LOOT = Arrays.asList(
        new ItemStack(Items.RAIL, randomCount(4, 8)),
        new ItemStack(Items.TORCH, randomCount(2, 6)),
        new ItemStack(Items.COAL, randomCount(2, 5)),
        new ItemStack(Items.IRON_PICKAXE)
    );

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
        // Add more enchantments here when they are added to the mod
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestStructures(SuggestionsBuilder builder) {
        String input = builder.getRemaining().toLowerCase();
        record StructureInfo(String name, String description) {}
        
        StructureInfo[] structureInfos = {
            new StructureInfo("stronghold", "Stronghold loot with higher level chance"),
            new StructureInfo("ancient_city", "Ancient City loot with medium level chance"),
            new StructureInfo("mineshaft", "Mineshaft loot with basic level chance")
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
            // Create enchanted book with random level
            ItemStack enchantedBook = new ItemStack(Items.ENCHANTED_BOOK);
            int level = RANDOM.nextInt(100) < 5 ? 5 : // 5% chance for level 5
                       RANDOM.nextInt(100) < 15 ? 4 : // 15% chance for level 4
                       RANDOM.nextInt(100) < 30 ? 3 : // 30% chance for level 3
                       RANDOM.nextInt(100) < 50 ? 2 : 1; // 50% chance for level 2, else level 1
            
            switch (enchantment.toLowerCase()) {
                case "life_steal" -> EnchantmentHelper.setEnchantments(
                    java.util.Map.of(ModEnchantments.LIFE_STEAL.get(), level),
                    enchantedBook
                );
                default -> {
                    source.sendFailure(Component.literal("Unknown enchantment: " + enchantment));
                    return 0;
                }
            }

            // Add book to first slot
            chest.setItem(0, enchantedBook);

            // Add structure-specific loot
            List<ItemStack> loot = switch (structure.toLowerCase()) {
                case "stronghold" -> STRONGHOLD_LOOT;
                case "ancient_city" -> ANCIENT_CITY_LOOT;
                case "mineshaft" -> MINESHAFT_LOOT;
                default -> {
                    source.sendFailure(Component.literal("Unknown structure: " + structure));
                    yield List.of();
                }
            };

            // Add random loot items to random slots
            for (ItemStack item : loot) {
                int slot = RANDOM.nextInt(26) + 1; // Random slot (1-26, keeping 0 for book)
                chest.setItem(slot, item.copy());
            }

            source.sendSuccess(() -> Component.literal(
                "Spawned " + structure + " chest with Life Steal " + level + " book and structure loot"), 
                true);
            return 1;
        }

        return 0;
    }
}
