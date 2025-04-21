package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.GameRules;

import java.util.Random;
import java.util.UUID;
import java.util.HashMap;

public class SchizopreniaEnchantment extends Enchantment {
    private static final Random random = new Random();
    private static final HashMap<UUID, Integer> tickCounter = new HashMap<>();
    private static final HashMap<UUID, Integer> nauseaTicks = new HashMap<>();
    private static final int NAUSEA_DURATION = 400; // 20 seconds (in ticks)
    private static final HashMap<UUID, FireGuideData> fireGuideMap = new HashMap<>();
    private static final HashMap<UUID, CloneData> playerCloneMap = new HashMap<>();

    // Constants for different environments
    private static final int MINING_MESSAGE_CHANCE = 40; // 40% when underground
    private static final int CHAT_MESSAGE_CHANCE = 70; // 70% normal case
    private static final int LEAVE_MESSAGE_CHANCE = 85; // 15% chance
    private static final int JOIN_MESSAGE_CHANCE = 100; // 15% chance
    private static final int CLONE_SPAWN_CHANCE = 2500; // Chance to spawn clone (1 in X ticks)
    private static final int CLONE_DURATION = 160; // How long clone stands there (8 seconds)
    private static final int CLONE_VANISH_DISTANCE = 4; // Distance at which clone vanishes

    // Environment tracking
    private static final HashMap<UUID, PlayerEnvironment> playerEnvironments = new HashMap<>();

    private static class FireGuideData {
        public double x, y, z;
        public int ticksLeft;

        public FireGuideData(double x, double y, double z, int ticksLeft) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticksLeft = ticksLeft;
        }
    }

    // Clone entity tracking class
    private static class CloneData {
        public Entity cloneEntity;
        public int ticksLeft;
        public boolean hasSpawnedParticles;

        public CloneData(Entity entity, int duration) {
            this.cloneEntity = entity;
            this.ticksLeft = duration;
            this.hasSpawnedParticles = false;
        }
    }

    // Player environment tracking class
    private static class PlayerEnvironment {
        public boolean isMining; // Player is underground
        public boolean isInNether; // Player is in the Nether
        public boolean isInEnd; // Player is in the End
        public boolean isNearWater; // Player is near water
        public boolean isInDarkness; // Player is in a dark area
        public boolean isNearMobs; // Player is near hostile mobs
        public boolean isNearVillage; // Player is near a village
        public boolean isNightTime; // It's night time in the overworld
        public long timeSinceLastEvent; // Ticks since last hallucination

        public PlayerEnvironment() {
            this.isMining = false;
            this.isInNether = false;
            this.isInEnd = false;
            this.isNearWater = false;
            this.isInDarkness = false;
            this.isNearMobs = false;
            this.isNearVillage = false;
            this.isNightTime = false;
            this.timeSinceLastEvent = 0;
        }
    }

    public SchizopreniaEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR_HEAD, new EquipmentSlot[] { EquipmentSlot.HEAD });
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public int getMinLevel() {
        return 1;
    }

    @Override
    public int getMaxLevel() {
        return 1;
    }

    @Override
    public int getMinCost(int level) {
        return 10;
    }

    @Override
    public int getMaxCost(int level) {
        return 30;
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;
        if (!(event.player instanceof Player))
            return;
        Player player = (Player) event.player;
        if (player.level().isClientSide)
            return;

        // Check if player has the enchantment on any armor
        boolean hasSchizo = false;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = player.getItemBySlot(slot);
            if (EnchantmentHelper.getItemEnchantmentLevel(this, stack) > 0) {
                hasSchizo = true;
                break;
            }
        }
        if (!hasSchizo)
            return;

        UUID uuid = player.getUUID();

        // Update player environment
        updatePlayerEnvironment(player, uuid);

        // --- Nausea effect logic ---
        handleNauseaEffect(player, uuid);

        int ticks = tickCounter.getOrDefault(uuid, 0) + 1;
        tickCounter.put(uuid, ticks);

        // --- Fire guiding particle logic ---
        handleFireGuide(player, uuid);

        // --- Player clone logic ---
        handlePlayerClone(player, uuid);

        // Generate hallucination messages based on environment
        if (ticks > 400 + random.nextInt(400)) { // Every 20-40 seconds
            generateHallucinationMessage(player, uuid);
            tickCounter.put(uuid, 0);
        }
    }

    private void updatePlayerEnvironment(Player player, UUID uuid) {
        PlayerEnvironment env = playerEnvironments.getOrDefault(uuid, new PlayerEnvironment());

        // Update environment status
        env.isMining = player.getY() < 0;
        env.isInNether = player.level().dimension() == Level.NETHER;
        env.isInEnd = player.level().dimension() == Level.END;
        env.isNightTime = player.level().isNight();
        env.isInDarkness = player.level().getRawBrightness(player.blockPosition(), 0) < 8;

        // Could add additional checks for water, mobs, villages using bounding boxes or
        // similar
        // For now, simplified version
        env.isNearWater = player.isInWater() || player.isInWaterRainOrBubble();

        playerEnvironments.put(uuid, env);
    }

    private void handleNauseaEffect(Player player, UUID uuid) {
        if (nauseaTicks.containsKey(uuid)) {
            int ticksLeft = nauseaTicks.get(uuid) - 1;
            if (ticksLeft <= 0) {
                nauseaTicks.remove(uuid);
                player.sendSystemMessage(Component.literal("§7You okay? you look pale"));
            } else {
                nauseaTicks.put(uuid, ticksLeft);
            }
        } else if (random.nextInt(4000) == 0) { // ~0.025% chance per tick (~once per 3+ minutes)
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, NAUSEA_DURATION, 0, false, true, true));
            nauseaTicks.put(uuid, NAUSEA_DURATION);
        }
    }

    private void handleFireGuide(Player player, UUID uuid) {
        if (!player.level().isClientSide && player instanceof ServerPlayer serverPlayer) {
            FireGuideData guide = fireGuideMap.get(uuid);
            if (guide != null) {
                if (guide.ticksLeft > 0) {
                    guide.ticksLeft--;
                    // Show flame particle at guide location
                    ((ServerLevel) serverPlayer.level()).sendParticles(
                            serverPlayer,
                            ParticleTypes.FLAME,
                            true,
                            guide.x, guide.y, guide.z,
                            1, 0, 0, 0, 0.01);
                    // Move the fire a little each tick (random walk, but generally away from
                    // player)
                    Vec3 playerPos = player.position();
                    double dx = guide.x - playerPos.x;
                    double dz = guide.z - playerPos.z;
                    double dist = Math.sqrt(dx * dx + dz * dz);
                    double moveAngle = Math.atan2(dz, dx) + (random.nextDouble() - 0.5) * 0.5;
                    double moveDist = 0.05 + random.nextDouble() * 0.05; // slower movement
                    if (dist < 5)
                        moveDist += 0.05; // Encourage it to move away, but slower
                    guide.x += Math.cos(moveAngle) * moveDist;
                    guide.z += Math.sin(moveAngle) * moveDist;
                    guide.y += (random.nextDouble() - 0.5) * 0.02; // less vertical movement
                } else {
                    // Fire has stopped moving, keep showing the particle at the same location
                    ((ServerLevel) serverPlayer.level()).sendParticles(
                            serverPlayer,
                            ParticleTypes.FLAME,
                            true,
                            guide.x, guide.y, guide.z,
                            1, 0, 0, 0, 0.01);
                    // Check if player is within 2 blocks of the fire
                    Vec3 playerPos = player.position();
                    double dist = playerPos.distanceTo(new Vec3(guide.x, guide.y, guide.z));
                    if (dist <= 2.0) {
                        fireGuideMap.remove(uuid);
                        player.sendSystemMessage(Component.literal("§6go dig below right the fire stopped!"));
                    }
                }
            } else if (random.nextInt(6000) == 0) { // ~once every 5 minutes
                // Spawn fire guide at player's eye level, a few blocks in front
                Vec3 look = player.getLookAngle();
                double fx = player.getX() + look.x * 2;
                double fy = player.getY() + 1.5;
                double fz = player.getZ() + look.z * 2;
                fireGuideMap.put(uuid, new FireGuideData(fx, fy, fz, 200)); // 10 seconds (200 ticks)
            }
        }
    }

    private void handlePlayerClone(Player player, UUID uuid) {
        // Handle existing clones
        if (playerCloneMap.containsKey(uuid)) {
            CloneData cloneData = playerCloneMap.get(uuid);

            // Check if clone entity is still valid
            if (cloneData.cloneEntity == null || !cloneData.cloneEntity.isAlive()) {
                playerCloneMap.remove(uuid);
                return;
            }

            // Make clone look at player
            Entity clone = cloneData.cloneEntity;
            Vec3 playerPos = player.position();
            Vec3 clonePos = clone.position();

            // Calculate the direction from clone to player
            double dx = playerPos.x - clonePos.x;
            double dz = playerPos.z - clonePos.z;
            float yRot = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
            clone.setYRot(yRot);

            // Check if player is getting too close
            double distSq = player.distanceToSqr(clone);
            if (distSq < CLONE_VANISH_DISTANCE * CLONE_VANISH_DISTANCE) {
                // Player got too close, disappear with particles
                if (!cloneData.hasSpawnedParticles) {
                    cloneData.hasSpawnedParticles = true;

                    if (player instanceof ServerPlayer serverPlayer
                            && clone.level() instanceof ServerLevel serverLevel) {
                        // Create smoke effect when clone vanishes
                        serverLevel.sendParticles(
                                serverPlayer,
                                ParticleTypes.LARGE_SMOKE,
                                true,
                                clone.getX(), clone.getY() + 1.0, clone.getZ(),
                                20, 0.2, 0.5, 0.2, 0.02);
                    }

                    // Send creepy message
                    player.sendSystemMessage(Component.literal("§7You blinked... and it was gone"));

                    // Remove clone
                    clone.discard();
                    playerCloneMap.remove(uuid);
                }
            } else {
                // Decrement time left for clone
                cloneData.ticksLeft--;
                if (cloneData.ticksLeft <= 0) {
                    // Time ran out, remove clone
                    if (player instanceof ServerPlayer serverPlayer
                            && clone.level() instanceof ServerLevel serverLevel) {
                        // Create smoke effect when clone vanishes
                        serverLevel.sendParticles(
                                serverPlayer,
                                ParticleTypes.LARGE_SMOKE,
                                true,
                                clone.getX(), clone.getY() + 1.0, clone.getZ(),
                                20, 0.2, 0.5, 0.2, 0.02);
                    }

                    clone.discard();
                    playerCloneMap.remove(uuid);
                }
            }
        }
        // Spawn new clone
        else if (random.nextInt(CLONE_SPAWN_CHANCE) == 0 && player instanceof ServerPlayer serverPlayer) {
            // Find a position behind the player to spawn clone
            Vec3 playerPos = player.position();
            Vec3 lookVec = player.getLookAngle();

            // Position behind player (opposite of where they're looking)
            double spawnDist = 8.0 + random.nextDouble() * 4.0; // 8-12 blocks away
            double spawnX = playerPos.x - lookVec.x * spawnDist + (random.nextDouble() - 0.5) * 5.0;
            double spawnY = playerPos.y;
            double spawnZ = playerPos.z - lookVec.z * spawnDist + (random.nextDouble() - 0.5) * 5.0;

            // Create clone
            createPlayerClone(serverPlayer, spawnX, spawnY, spawnZ);
        }
    }

    private void createPlayerClone(ServerPlayer player, double x, double y, double z) {
        ServerLevel level = player.serverLevel();

        // Create an armor stand as the "clone"
        ArmorStand clone = new ArmorStand(level, x, y, z);
        clone.setNoGravity(true);
        clone.setInvisible(true); // Make base entity invisible
        clone.setInvulnerable(true);
        clone.setSilent(true);
        clone.setCustomName(Component.literal(player.getName().getString())); // Same name
        clone.setCustomNameVisible(false);

        // Copy player's equipment to clone (all slots)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            clone.setItemSlot(slot, player.getItemBySlot(slot).copy());
        }

        // Make sure the clone faces the player initially
        Vec3 playerPos = player.position();
        double dx = playerPos.x - x;
        double dz = playerPos.z - z;
        float yRot = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0F;
        clone.setYRot(yRot);

        // Spawn the clone with particles
        level.addFreshEntity(clone);

        // Create smoke effect when clone appears
        level.sendParticles(
                player,
                ParticleTypes.LARGE_SMOKE,
                true,
                x, y + 1.0, z,
                15, 0.2, 0.5, 0.2, 0.02);

        // Store clone data and send creepy message to player
        playerCloneMap.put(player.getUUID(), new CloneData(clone, CLONE_DURATION));
        player.sendSystemMessage(Component.literal("§7Did you... just see yourself?"));
    }

    private void generateHallucinationMessage(Player player, UUID uuid) {
        PlayerEnvironment env = playerEnvironments.get(uuid);
        int roll = random.nextInt(100);

        // Choose message type based on player environment and random chance
        if (env.isMining && random.nextInt(100) < MINING_MESSAGE_CHANCE) {
            sendFakeMessage(player, getMiningMessage());
        } else if (env.isInNether && random.nextInt(100) < 50) {
            sendFakeMessage(player, getNetherMessage());
        } else if (env.isInEnd && random.nextInt(100) < 50) {
            sendFakeMessage(player, getEndMessage());
        } else if (env.isNearWater && random.nextInt(100) < 30) {
            sendFakeMessage(player, getWaterMessage());
        } else if (env.isInDarkness && random.nextInt(100) < 40) {
            sendFakeMessage(player, getDarknessMessage());
        } else if (env.isNightTime && random.nextInt(100) < 30) {
            sendFakeMessage(player, getNightTimeMessage());
        } else if (roll < CHAT_MESSAGE_CHANCE) {
            // Regular chat message
            String fakeName = getRandomGamertag();
            player.sendSystemMessage(Component.literal("<" + fakeName + "> " + getRandomChatMessage()));
        } else if (roll < LEAVE_MESSAGE_CHANCE) {
            // Fake leave message
            String fakeName = getRandomGamertag();
            player.sendSystemMessage(Component.literal("§e" + fakeName + " left the game"));
        } else {
            // Fake join message
            String fakeName = getRandomGamertag();
            player.sendSystemMessage(Component.literal("§e" + fakeName + " joined the game"));
        }
    }

    private void sendFakeMessage(Player player, String message) {
        String fakeName = getRandomGamertag();
        player.sendSystemMessage(Component.literal("<" + fakeName + "> " + message));
    }

    // Environment-specific message getters
    private String getMiningMessage() {
        String[] messages = {
                "There's lava nearby, go jump in!",
                "I hear diamonds, dig straight down!",
                "You're close to bedrock, keep going!",
                "There's a cave above you, dig up!",
                "I hear something behind the wall.",
                "There's an ancient city here.",
                "You should mine with your fists.",
                "Throw your pickaxe, it's cursed.",
                "Cover yourself in gravel, it's safe.",
                "There's a mineshaft under you.",
                "There's a secret if you stand in lava.",
                "You don't need torches, it's bright enough.",
                "Eat some raw ore, it's healthy.",
                "You hear whispers from the stone.",
                "The diamonds are fake, ignore them.",
                "You should place TNT here.",
                "I hear a silverfish nest nearby.",
                "Break the spawner with your hand!",
                "The deepslate is watching you.",
                "There's gold behind that block.",
                "Don't trust the light level."
        };
        return messages[random.nextInt(messages.length)];
    }

    private String getNetherMessage() {
        String[] messages = {
                "The piglin are planning something.",
                "The lava is rising, run!",
                "Jump into the lava lake, there's a secret.",
                "I see a fortress through the wall.",
                "Break the nether gold ore with your hand.",
                "Punch a hoglin, they drop better loot.",
                "There's a blaze spawner right above us.",
                "I see ghast tears floating in the lava.",
                "The striders are watching you.",
                "The soul sand is whispering your name.",
                "There's netherite below that lava.",
                "The basalt is moving when you're not looking.",
                "This bastion has a hidden chamber.",
                "The wither skeletons are friendly if you drop your sword.",
                "Ancient debris always spawns under lava pools."
        };
        return messages[random.nextInt(messages.length)];
    }

    private String getEndMessage() {
        String[] messages = {
                "The dragon is respawning!",
                "Jump into the void, there's an easter egg.",
                "The endermen are having a meeting about you.",
                "There's a second dragon hiding in the darkness.",
                "If you stare at endermen long enough, they become friendly.",
                "I found a secret end city below the island.",
                "The chorus fruit is singing.",
                "The shulkers are planning an invasion.",
                "There's a hidden portal in the obsidian pillars.",
                "The end crystals can be worn as a helmet.",
                "The dragon egg is hatching!",
                "The void is actually a portal to a secret dimension.",
                "End stone contains diamonds if you smelt it.",
                "The end gateway leads to a different location each time."
        };
        return messages[random.nextInt(messages.length)];
    }

    private String getWaterMessage() {
        String[] messages = {
                "There's something swimming below you.",
                "I saw a drowned with a trident right behind you.",
                "The water is rising!",
                "There's a hidden underwater temple nearby.",
                "The squid are plotting against us.",
                "I heard an elder guardian sound.",
                "The bubbles are pulling you down.",
                "Guardian laser incoming!",
                "There's treasure right below where you're swimming.",
                "The axolotls whispered your name.",
                "The water is turning red.",
                "Something brushed against my leg!",
                "The water current is getting stronger.",
                "I see glowing eyes watching from the deep."
        };
        return messages[random.nextInt(messages.length)];
    }

    private String getDarknessMessage() {
        String[] messages = {
                "I hear a creeper hissing.",
                "There's a skeleton aiming at your head.",
                "I see glowing eyes in the darkness.",
                "Something is moving in the shadows.",
                "Did you hear that zombie groan?",
                "I think I saw Herobrine.",
                "The darkness is moving.",
                "There's a witch brewing potions nearby.",
                "I smell gunpowder...",
                "The cave sounds are getting louder.",
                "Something is digging through the wall.",
                "I see spiders on the ceiling.",
                "Keep your shield up, I heard an arrow.",
                "The phantoms are circling above us.",
                "I think this cave is cursed.",
                "There's something following us."
        };
        return messages[random.nextInt(messages.length)];
    }

    private String getNightTimeMessage() {
        String[] messages = {
                "Did you see that shadow?",
                "The moon is watching us.",
                "I hear zombies breaking down a door.",
                "The stars are moving.",
                "I saw something flying overhead.",
                "The darkness hides many secrets.",
                "I hear spiders climbing the walls.",
                "Don't go outside, it's not safe.",
                "The phantoms know when you last slept.",
                "I see eyes watching from the forest.",
                "The night is unusually quiet.",
                "I think I saw a wither skeleton outside.",
                "The wind sounds like whispers.",
                "There's a witch's hut that wasn't there before.",
                "I hear endermen teleporting around us."
        };
        return messages[random.nextInt(messages.length)];
    }

    private String getRandomGamertag() {
        String[] adjectives = {
                "Swift", "Dark", "Crazy", "Silent", "Epic", "Wild", "Fuzzy", "Sneaky", "Lucky", "Rapid",
                "Shadow", "Frost", "Blaze", "Thunder", "Mystic", "Vivid", "Pixel", "Nova", "Ghost", "Turbo"
        };
        String[] nouns = {
                "Wolf", "Tiger", "Falcon", "Ninja", "Wizard", "Knight", "Rider", "Hunter", "Sniper", "Dragon",
                "Viper", "Eagle", "Lion", "Shark", "Phantom", "Rogue", "Storm", "Blade", "Gamer", "Beast"
        };
        int number = 10 + random.nextInt(990);
        String adj = adjectives[random.nextInt(adjectives.length)];
        String noun = nouns[random.nextInt(nouns.length)];
        return adj + noun + number;
    }

    private String getRandomChatMessage() {
        String[] messages = {
                "Anyone here?",
                "Where am I?",
                "Help!",
                "Is this server laggy?",
                "I found diamonds!",
                "Who's online?",
                "Can you hear me?",
                "What is that noise?",
                "I'm lost...",
                "Is someone watching me?",
                "Did you hear that?",
                "I swear I saw someone.",
                "Stop following me.",
                "Why is it so quiet?",
                "Don't trust them.",
                "They're coming.",
                "I can't sleep.",
                "The voices are back.",
                "It's all a simulation.",
                "Wake up.",
                "I'm not alone.",
                "They're watching us.",
                "I saw Herobrine.",
                "Don't look behind you.",
                "I keep hearing footsteps.",
                "Why is my inventory changing?",
                "Did you move my stuff?",
                "I just saw a name tag.",
                "Who's breathing?",
                "I can't log out.",
                "Why is the sky red?",
                "There's something in the shadows.",
                "I keep seeing chat messages.",
                "Did you just whisper?",
                "I hear blocks breaking.",
                "Why is there a door opening?",
                "I saw a flash.",
                "My screen glitched.",
                "I can't find the exit.",
                "Why is it getting darker?",
                "I keep respawning.",
                "Is this a dream?",
                "I can't remember how I got here.",
                "Why is my health dropping?",
                "I just saw myself.",
                "There's a clone of me.",
                "I can't move.",
                "My controls are reversed.",
                "I hear laughter.",
                "Someone's calling my name.",
                "I can't type.",
                "My chat is broken."
        };
        return messages[random.nextInt(messages.length)];
    }
}