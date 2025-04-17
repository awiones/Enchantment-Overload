package com.Milhae77.enchantmentoverload.enchantment;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageTypes;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.fml.common.Mod;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

@Mod.EventBusSubscriber(value = Dist.CLIENT)
public class FireDefenseEnchantment extends Enchantment {
    private static final HashMap<UUID, Long> lastFireTrigger = new HashMap<>();
    private static final long COOLDOWN_TICKS = 20; // 1 second cooldown
    private static final double RANGE = 5.0; // Increased range to 5 blocks
    private static final long PARTICLE_UPDATE_TICKS = 5; // Update particles every 0.25 seconds
    private static final HashMap<UUID, Long> lastParticleSpawn = new HashMap<>();
    // Track player positions to avoid spawning particles when stationary
    private static final HashMap<UUID, PlayerPosition> lastPlayerPositions = new HashMap<>();
    // Track mobs for damage application
    private static final HashMap<UUID, Long> lastDamageTime = new HashMap<>();
    private static final long DAMAGE_COOLDOWN_TICKS = 10; // Apply damage every 0.5 seconds
    
    // Shockwave related fields
    private static final HashMap<UUID, Long> shockwaveChargeStart = new HashMap<>();
    private static final HashMap<UUID, Long> lastShockwaveUse = new HashMap<>();
    private static final long SHOCKWAVE_CHARGE_TICKS = 100; // 5 seconds (20 ticks per second)
    private static final long SHOCKWAVE_COOLDOWN_TICKS = 1200; // 1 minute (60 seconds * 20 ticks)
    private static final double SHOCKWAVE_RANGE = 10.0; // 10 block range
    private static final double SHOCKWAVE_KNOCKBACK_STRENGTH = 2.0; // Knockback strength
    private static final float SHOCKWAVE_DAMAGE = 8.0f; // Base damage for shockwave
    
    // New particle effect fields
    private static final HashMap<UUID, List<ParticlePosition>> gatheringParticles = new HashMap<>();
    private static final HashMap<UUID, Long> crouchStartTime = new HashMap<>();
    private static final long PARTICLE_GATHER_TICKS = 40; // 2 seconds to gather particles
    private static final Random random = new Random();
    
    // Track right-click state
    private static final HashMap<UUID, Boolean> isRightClickHeld = new HashMap<>();
    
    // --- Add blue fire particle tracking ---
    private static final HashMap<UUID, Boolean> blueFireActive = new HashMap<>();
    private static final long BLUE_FIRE_PARTICLE_UPDATE_TICKS = 10; // Reduced update frequency (0.5s)
    
    public FireDefenseEnchantment() {
        super(Rarity.RARE, EnchantmentCategory.ARMOR, new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        });
        MinecraftForge.EVENT_BUS.register(this);
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
        return 10 + 20 * (level - 1);
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 25;
    }
    
    @SubscribeEvent
    public void onRightClick(PlayerInteractEvent.RightClickEmpty event) {
        Player player = event.getEntity();
        UUID playerId = player.getUUID();
        long currentTime = player.level().getGameTime();

        // Check if player has the enchantment
        int totalLevel = calculateTotalLevel(player);
        if (totalLevel <= 0) return;

        // --- PREVENT CHARGE IF IN COOLDOWN ---
        if (lastShockwaveUse.containsKey(playerId)) {
            long lastUse = lastShockwaveUse.get(playerId);
            long since = currentTime - lastUse;
            if (since < SHOCKWAVE_COOLDOWN_TICKS) {
                return; // Do nothing if in cooldown
            }
        }

        // Check if player is crouching
        if (player.isCrouching()) {
            // Mark that right-click is being held
            isRightClickHeld.put(playerId, true);

            // Start charging shockwave if not already charging
            if (!shockwaveChargeStart.containsKey(playerId)) {
                shockwaveChargeStart.put(playerId, currentTime);

                // Only notify on server side to avoid duplicate messages
                if (!player.level().isClientSide) {
                    player.displayClientMessage(Component.literal("§6Charging Fire Shockwave..."), true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Player player = event.player;
        Level level = player.level();
        UUID playerId = player.getUUID();
        long currentTime = level.getGameTime();
        
        final int totalLevel = calculateTotalLevel(player);
        if (totalLevel <= 0) return;
        
        // --- RESET EFFECT STATE AFTER COOLDOWN ---
        if (lastShockwaveUse.containsKey(playerId)) {
            long lastUse = lastShockwaveUse.get(playerId);
            long since = currentTime - lastUse;
            if (since >= SHOCKWAVE_COOLDOWN_TICKS) {
                // Reset all effect state for this player
                gatheringParticles.remove(playerId);
                lastParticleSpawn.remove(playerId);
                lastPlayerPositions.remove(playerId);
                shockwaveChargeStart.remove(playerId);
                // Only clear crouch/right-click state if player is NOT sneaking
                if (!player.isCrouching()) {
                    crouchStartTime.remove(playerId);
                    isRightClickHeld.remove(playerId);
                }
                // IMPORTANT: Remove lastShockwaveUse entry to allow new shockwaves
                lastShockwaveUse.remove(playerId);
                // --- Reactivate blue fire after cooldown ---
                blueFireActive.put(playerId, true);
            }
        }

        // --- PREVENT ALL CHARGE/PARTICLE LOGIC IF IN COOLDOWN ---
        boolean inShockwaveCooldown = false;
        if (lastShockwaveUse.containsKey(playerId)) {
            long lastUse = lastShockwaveUse.get(playerId);
            long since = currentTime - lastUse;
            if (since < SHOCKWAVE_COOLDOWN_TICKS) {
                inShockwaveCooldown = true;
                // Also clear any attempt to start charging
                isRightClickHeld.remove(playerId);
                shockwaveChargeStart.remove(playerId);
            }
        }

        // If player is not crouching, they're not holding right-click either
        if (!player.isCrouching()) {
            isRightClickHeld.remove(playerId);
            // --- Remove blue fire if not crouching ---
            blueFireActive.remove(playerId);
        }
        
        // Handle shockwave charging and activation
        if (!inShockwaveCooldown) {
            handleShockwave(player, level, playerId, currentTime, totalLevel);
        }
        
        // Check combat cooldown
        if (lastFireTrigger.containsKey(playerId) && 
            currentTime - lastFireTrigger.get(playerId) < COOLDOWN_TICKS) {
            return;
        }

        // Handle particles - ONLY WHEN CROUCHING
        if (level.isClientSide) {
            if (inShockwaveCooldown) {
                // Remove all gathering particles and skip rendering
                if (gatheringParticles.containsKey(playerId)) {
                    gatheringParticles.get(playerId).clear();
                }
                // Also clear crouch state to prevent re-spawn
                crouchStartTime.remove(playerId);
                // --- Remove blue fire during cooldown ---
                blueFireActive.remove(playerId);
            } else if (player.isCrouching()) {
                // Track when player started crouching
                if (!crouchStartTime.containsKey(playerId)) {
                    crouchStartTime.put(playerId, currentTime);
                    // Initialize particle list if needed
                    if (!gatheringParticles.containsKey(playerId)) {
                        gatheringParticles.put(playerId, new ArrayList<>());
                    } else {
                        // Clear existing particles if any
                        gatheringParticles.get(playerId).clear();
                    }
                }

                long crouchDuration = currentTime - crouchStartTime.get(playerId);

                // Only spawn particles if enough time has passed since last spawn
                long lastSpawn = lastParticleSpawn.getOrDefault(playerId, 0L);
                if (currentTime - lastSpawn >= PARTICLE_UPDATE_TICKS) {
                    boolean isHoldingRightClick = isRightClickHeld.getOrDefault(playerId, false);

                    if (isHoldingRightClick) {
                        // Gather particles above player's head when right-click is held
                        handleGatheringParticles(player, level, totalLevel, crouchDuration, currentTime);
                    } else {
                        // --- ALWAYS CLEAR GATHERING PARTICLES WHEN ONLY SNEAKING ---
                        if (gatheringParticles.containsKey(playerId)) {
                            gatheringParticles.get(playerId).clear();
                        }
                        // --- ALWAYS SHOW BARRIER PARTICLES WHEN SNEAKING AND NOT CHARGING ---
                        spawnFireBarrierParticles(player, level, totalLevel);
                    }

                    lastParticleSpawn.put(playerId, currentTime);

                    // Update last known position
                    lastPlayerPositions.put(playerId, new PlayerPosition(player.getX(), player.getY(), player.getZ()));
                }
                // --- Activate blue fire when crouching and not in cooldown ---
                blueFireActive.put(playerId, true);
            } else {
                // Player stopped crouching
                if (crouchStartTime.containsKey(playerId)) {
                    long crouchDuration = currentTime - crouchStartTime.get(playerId);
                    boolean wasHoldingRightClick = isRightClickHeld.getOrDefault(playerId, false);
                    
                    // Clear crouching state
                    crouchStartTime.remove(playerId);
                    
                    // Gradually fade out particles
                    if (gatheringParticles.containsKey(playerId)) {
                        List<ParticlePosition> particles = gatheringParticles.get(playerId);
                        if (!particles.isEmpty() && currentTime - lastParticleSpawn.getOrDefault(playerId, 0L) >= PARTICLE_UPDATE_TICKS) {
                            fadeOutParticles(player, level, particles);
                            lastParticleSpawn.put(playerId, currentTime);
                        }
                    }
                }
                // --- Remove blue fire when not crouching ---
                blueFireActive.remove(playerId);
            }
        }

        // --- Blue fire should always be active for players with the enchantment ---
        if (event.player.level().isClientSide) {
            if (totalLevel > 0 && !lastShockwaveUse.containsKey(playerId)) {
                blueFireActive.put(playerId, true);
            } else if (lastShockwaveUse.containsKey(playerId)) {
                blueFireActive.remove(playerId);
            } else {
                blueFireActive.remove(playerId);
            }

            // --- Only spawn blue fire X for the local player to avoid trails ---
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null && mc.player.getUUID().equals(playerId)) {
                boolean showBlueFire = blueFireActive.getOrDefault(playerId, false);
                if (showBlueFire) {
                    double centerX = event.player.getX();
                    double centerY = event.player.getY() + 2.5;
                    double centerZ = event.player.getZ();
                    double length = 0.2 + (totalLevel * 0.03); // Reduced size
                    int segments = 3 + totalLevel; // Fewer segments

                    // Only spawn particles at the tips of the X
                    event.player.level().addParticle(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        centerX + length, centerY, centerZ + length,
                        0, 0.01, 0
                    );
                    event.player.level().addParticle(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        centerX - length, centerY, centerZ - length,
                        0, 0.01, 0
                    );
                    event.player.level().addParticle(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        centerX + length, centerY, centerZ - length,
                        0, 0.01, 0
                    );
                    event.player.level().addParticle(
                        ParticleTypes.SOUL_FIRE_FLAME,
                        centerX - length, centerY, centerZ + length,
                        0, 0.01, 0
                    );
                    lastParticleSpawn.put(playerId, currentTime);
                }
            }
        }

        // Only process combat effects on the server side
        // Note: Combat effects are ALWAYS active, regardless of crouching
        if (!level.isClientSide && !inShockwaveCooldown) {
            AABB searchBox = player.getBoundingBox().inflate(RANGE);
            player.level().getEntitiesOfClass(Mob.class, searchBox, 
                mob -> {
                    if (!mob.isAlive() || mob.isAlliedTo(player)) return false;
                    return mob.getTarget() == player || 
                           (mob.isAggressive() && mob.distanceToSqr(player) <= RANGE * RANGE);
                }
            ).forEach(mob -> {
                // DOUBLED FIRE DURATION: 4 seconds per level instead of 2
                mob.setSecondsOnFire(4 * totalLevel);
                
                // Apply direct fire damage with cooldown
                UUID mobId = mob.getUUID();
                long lastDamage = lastDamageTime.getOrDefault(mobId, 0L);
                if (currentTime - lastDamage >= DAMAGE_COOLDOWN_TICKS) {
                    // Apply direct fire damage based on enchantment level
                    float damageAmount = 1.0f + (totalLevel * 0.5f); // 1.0 + 0.5 per level
                    mob.hurt(mob.damageSources().onFire(), damageAmount);
                    
                    // Update damage cooldown
                    lastDamageTime.put(mobId, currentTime);
                }
                
                lastFireTrigger.put(playerId, currentTime);
            });
        }
    }
    
    private void handleGatheringParticles(Player player, Level level, int totalLevel, long crouchDuration, long currentTime) {
        UUID playerId = player.getUUID();
        List<ParticlePosition> particles = gatheringParticles.get(playerId);
        
        // Calculate gathering progress (0.0 to 1.0)
        float gatherProgress = Math.min(1.0f, (float)crouchDuration / PARTICLE_GATHER_TICKS);
        
        // Generate new particles at the perimeter
        if (gatherProgress < 0.8f) {
            // Particle count scales with enchantment level
            int newParticleCount = 2 + totalLevel;
            
            for (int i = 0; i < newParticleCount; i++) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = RANGE;
                
                double x = player.getX() + distance * Math.cos(angle);
                double z = player.getZ() + distance * Math.sin(angle);
                double y = player.getY() + 0.1;
                
                // Create a new particle position with target above player's head
                ParticlePosition particlePos = new ParticlePosition(
                    x, y, z,
                    player.getX(), player.getY() + 2.5, player.getZ()
                );
                
                particles.add(particlePos);
            }
        }
        
        // Move existing particles toward their targets
        List<ParticlePosition> particlesToRemove = new ArrayList<>();
        for (ParticlePosition particle : particles) {
            // Move particle toward target based on gathering progress
            double moveSpeed = 0.18 + (gatherProgress * 0.22);
            
            // Calculate direction vector
            double dx = particle.targetX - particle.currentX;
            double dy = particle.targetY - particle.currentY;
            double dz = particle.targetZ - particle.currentZ;
            
            // Normalize and scale by move speed
            double length = Math.sqrt(dx*dx + dy*dy + dz*dz);
            if (length > 0.1) {
                particle.currentX += (dx / length) * moveSpeed;
                particle.currentY += (dy / length) * moveSpeed;
                particle.currentZ += (dz / length) * moveSpeed;
                
            } else {
                // Particle has reached its target, mark for removal
                particlesToRemove.add(particle);
            }
        }
        
        // Remove particles that reached their targets
        particles.removeAll(particlesToRemove);
        
        // Add swirling particles above player's head when gathering is complete
        if (gatherProgress >= 0.8f) {
            double radius = 0.5 + (gatherProgress * 0.5);
            int swirlingParticles = 5 + (totalLevel * 2);
            
            for (int i = 0; i < swirlingParticles; i++) {
                double angle = (currentTime % 40) / 20.0 * Math.PI + (i * 2 * Math.PI / swirlingParticles);
                double x = player.getX() + radius * Math.cos(angle);
                double z = player.getZ() + radius * Math.sin(angle);
                double y = player.getY() + 2.5 + (Math.sin(currentTime / 10.0) * 0.2);
                
            }
        }
    }
    
    private void createParticleExplosion(Player player, Level level, int totalLevel) {
        // Play sound effect
        level.playSound(
            null, 
            player.getX(), player.getY(), player.getZ(), 
            SoundEvents.FIRECHARGE_USE, 
            SoundSource.PLAYERS, 
            0.7F, 
            1.0F
        );

        // Optionally, keep only SMOKE particles if desired:
        int particleCount = 30 + (totalLevel * 10);
        double baseSpeed = 0.5 + (totalLevel * 0.15);
        double centerX = player.getX();
        double centerY = player.getY() + 2.5;
        double centerZ = player.getZ();
        for (int i = 0; i < particleCount; i++) {
            double theta = random.nextDouble() * 2 * Math.PI;
            double phi = Math.acos(2 * random.nextDouble() - 1);
            double xDir = Math.sin(phi) * Math.cos(theta);
            double yDir = Math.cos(phi);
            double zDir = Math.sin(phi) * Math.sin(theta);
            double speed = baseSpeed * (0.7 + random.nextDouble() * 0.6);
            level.addParticle(
                ParticleTypes.SMOKE,
                centerX, centerY, centerZ,
                xDir * speed * 0.7, yDir * speed * 0.7, zDir * speed * 0.7
            );
        }

        // Clear gathering particles
        UUID playerId = player.getUUID();
        if (gatheringParticles.containsKey(playerId)) {
            gatheringParticles.get(playerId).clear();
        }
    }
    
    private void fadeOutParticles(Player player, Level level, List<ParticlePosition> particles) {
        // Remove 20% of particles each tick
        int particlesToRemove = Math.max(1, particles.size() / 5);
        for (int i = 0; i < particlesToRemove && !particles.isEmpty(); i++) {
            int index = random.nextInt(particles.size());
            ParticlePosition particle = particles.remove(index);
            
            // Create a final "poof" effect
            level.addParticle(
                ParticleTypes.SMOKE,
                particle.currentX, particle.currentY, particle.currentZ,
                (random.nextDouble() - 0.5) * 0.1,
                random.nextDouble() * 0.1,
                (random.nextDouble() - 0.5) * 0.1
            );
        }
    }
    
    private void handleShockwave(Player player, Level level, UUID playerId, long currentTime, int totalLevel) {
        // Check if player is charging a shockwave
        if (shockwaveChargeStart.containsKey(playerId)) {
            long chargeStartTime = shockwaveChargeStart.get(playerId);
            long chargeTime = currentTime - chargeStartTime;

            // --- TNT explosion particle effect 0.5s before charge completes ---
            if (level.isClientSide) {
                // Create charging blue fire effect
                if (chargeTime < SHOCKWAVE_CHARGE_TICKS - 10) {
                    double centerX = player.getX();
                    double centerY = player.getY() + 1.0;
                    double centerZ = player.getZ();
                    
                    // Create spinning particles around player
                    int spirals = 3 + totalLevel;
                    double baseRadius = 0.8 + (totalLevel * 0.1);
                    
                    for (int spiral = 0; spiral < spirals; spiral++) {
                        double spiralOffset = (2 * Math.PI * spiral) / spirals + (chargeTime * 0.1);
                        double heightOffset = Math.sin(chargeTime * 0.2) * 0.3;
                        
                        // Create vertical spiral
                        for (int i = 0; i < 8; i++) {
                            double progress = (double) i / 8;
                            double height = progress * 2.0; // 2 blocks tall spiral
                            double radius = baseRadius * (1 - progress * 0.5); // Spiral gets tighter at top
                            
                            double angle = progress * 4 * Math.PI + spiralOffset + (chargeTime * 0.2);
                            
                            double x = centerX + radius * Math.cos(angle);
                            double y = centerY + height + heightOffset;
                            double z = centerZ + radius * Math.sin(angle);
                            
                            player.level().addParticle(
                                ParticleTypes.SOUL_FIRE_FLAME,
                                x, y, z,
                                0, 0.02, 0
                            );
                        }
                    }
                }
                
                // TNT explosion particles near completion
                if (chargeTime >= SHOCKWAVE_CHARGE_TICKS - 10 && chargeTime < SHOCKWAVE_CHARGE_TICKS) {
                    // Create spreading explosion effect
                    double centerX = player.getX();
                    double centerY = player.getY() + 2.5;
                    double centerZ = player.getZ();
                    int tntParticles = 8 + totalLevel * 2;
                    
                    // Create multiple explosion rings with different heights and radiuses
                    for (int ring = 0; ring < 3; ring++) {
                        double ringRadius = (0.7 + (totalLevel * 0.15)) * (1 + ring * 0.5);
                        double ringHeight = centerY + (ring * 0.3) * Math.sin(chargeTime * 0.2);
                        
                        for (int i = 0; i < tntParticles; i++) {
                            double angle = ((2 * Math.PI * i) / tntParticles) + (chargeTime * 0.1);
                            double wobble = Math.sin(chargeTime * 0.2 + i) * 0.2;
                            
                            double x = centerX + (ringRadius + wobble) * Math.cos(angle);
                            double z = centerZ + (ringRadius + wobble) * Math.sin(angle);
                            double y = ringHeight + Math.sin(angle * 2 + chargeTime * 0.1) * 0.2;
                            
                            player.level().addParticle(
                                ParticleTypes.EXPLOSION,
                                x, y, z,
                                (random.nextDouble() - 0.5) * 0.1, // Add random motion
                                0.02 + random.nextDouble() * 0.02,
                                (random.nextDouble() - 0.5) * 0.1
                            );
                        }
                    }
                }
            }

            // If player stops crouching or releases right click, cancel the charge
            if (!player.isCrouching() || !isRightClickHeld.getOrDefault(playerId, false)) {
                shockwaveChargeStart.remove(playerId);
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.literal("§cFire Shockwave canceled"), true);
                }
                return;
            }
            
            // Check if charge is complete
            if (chargeTime >= SHOCKWAVE_CHARGE_TICKS) {
                // Check cooldown
                if (lastShockwaveUse.containsKey(playerId) && 
                    currentTime - lastShockwaveUse.get(playerId) < SHOCKWAVE_COOLDOWN_TICKS) {
                    
                    if (!level.isClientSide) {
                        long remainingCooldown = SHOCKWAVE_COOLDOWN_TICKS - (currentTime - lastShockwaveUse.get(playerId));
                        int remainingSeconds = (int) (remainingCooldown / 20);
                        player.displayClientMessage(
                            Component.literal("§cFire Shockwave on cooldown: " + remainingSeconds + " seconds remaining"), 
                            true
                        );
                    }
                    
                    shockwaveChargeStart.remove(playerId);
                    return;
                }
                
                // Activate shockwave
                activateShockwave(player, level, totalLevel);
                
                // Update cooldown
                lastShockwaveUse.put(playerId, currentTime);
                shockwaveChargeStart.remove(playerId);
            }
            // Display charge progress every second
            else if (!level.isClientSide && chargeTime % 20 == 0) {
                int secondsRemaining = (int) ((SHOCKWAVE_CHARGE_TICKS - chargeTime) / 20);
                player.displayClientMessage(
                    Component.literal("§6Fire Shockwave charging: " + secondsRemaining + " seconds remaining"), 
                    true
                );
            }
        }
    }
    
    private void activateShockwave(Player player, Level level, int totalLevel) {
        // Remove the client-side check to allow effects on both sides
        
        // Play sound effect
        level.playSound(
            null, 
            player.getX(), player.getY(), player.getZ(), 
            SoundEvents.GENERIC_EXPLODE, 
            SoundSource.PLAYERS, 
            1.0F, 
            1.0F
        );
        
        // Notify player
        if (!level.isClientSide) {
            player.displayClientMessage(Component.literal("§6Fire Shockwave released!"), true);
        }
        
        // --- ENSURE EXPLOSION PARTICLE IS VISIBLE ON BOTH SIDES ---
        createParticleExplosion(player, level, totalLevel);
        
        // --- CLEAR ALL GATHERING PARTICLES ABOVE PLAYER'S HEAD ---
        UUID playerId = player.getUUID();
        if (gatheringParticles.containsKey(playerId)) {
            gatheringParticles.get(playerId).clear();
        }
        // --- RESET CROUCH AND RIGHT-CLICK STATE AFTER SHOCKWAVE ---
        crouchStartTime.remove(playerId);
        isRightClickHeld.remove(playerId);
        shockwaveChargeStart.remove(playerId);
        lastParticleSpawn.remove(playerId);
        lastPlayerPositions.remove(playerId);

        // Find all mobs in range and apply effects
        AABB searchBox = player.getBoundingBox().inflate(SHOCKWAVE_RANGE);
        player.level().getEntitiesOfClass(Mob.class, searchBox, 
            mob -> mob.isAlive() && !mob.isAlliedTo(player)
        ).forEach(mob -> {
            Vec3 direction = new Vec3(
                mob.getX() - player.getX(),
                0.5,
                mob.getZ() - player.getZ()
            ).normalize();
            
            double distance = mob.distanceTo(player);
            double distanceFactor = 1.0 - (distance / SHOCKWAVE_RANGE);
            distanceFactor = Math.max(0.2, distanceFactor);
            
            // Increase knockback effect
            double knockbackStrength = SHOCKWAVE_KNOCKBACK_STRENGTH * distanceFactor * (1 + totalLevel * 0.5);
            mob.setDeltaMovement(
                direction.x * knockbackStrength,
                direction.y * knockbackStrength * 0.75,
                direction.z * knockbackStrength
            );
            
            if (!level.isClientSide) {
                float damage = SHOCKWAVE_DAMAGE * (float)distanceFactor * (1 + totalLevel * 0.25f);
                mob.hurt(mob.damageSources().onFire(), damage);
                mob.setSecondsOnFire(5 * totalLevel);
            }
        });
    }

    private void spawnFireBarrierParticles(Player player, Level level, int totalLevel) {
        double y = player.getY() + 0.1;
        int particleCount = 8 + (totalLevel * 2);
        
        // Main circle
        for (int i = 0; i < particleCount; i++) {
            double angle = (2 * Math.PI * i) / particleCount;
            double x = player.getX() + RANGE * Math.cos(angle);
            double z = player.getZ() + RANGE * Math.sin(angle);

            // Reduced smoke particles - only at cardinal points
            if (i % (particleCount / 4) == 0) {
                level.addParticle(
                    ParticleTypes.SMOKE,
                    x, y, z,
                    0, 0.005, 0
                );
            }
        }
    }

    private int calculateTotalLevel(Player player) {
        int level = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            level += EnchantmentHelper.getItemEnchantmentLevel(this, player.getItemBySlot(slot));
        }
        return level;
    }
    
    // Helper class to track player position
    private static class PlayerPosition {
        public final double x;
        public final double y;
        public final double z;
        
        public PlayerPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
    
    // Helper class to track particle positions and targets
    private static class ParticlePosition {
        public double currentX;
        public double currentY;
        public double currentZ;
        public final double targetX;
        public final double targetY;
        public final double targetZ;
        
        public ParticlePosition(double startX, double startY, double startZ, 
                               double targetX, double targetY, double targetZ) {
            this.currentX = startX;
            this.currentY = startY;
            this.currentZ = startZ;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
        }
    }

    // Show cooldown when inventory is opened
    @SubscribeEvent
    public void onInventoryOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        UUID playerId = player.getUUID();
        long currentTime = player.level().getGameTime();

        int totalLevel = calculateTotalLevel(player);
        if (totalLevel <= 0) return;

        if (lastShockwaveUse.containsKey(playerId)) {
            long lastUse = lastShockwaveUse.get(playerId);
            long since = currentTime - lastUse;
            if (since < SHOCKWAVE_COOLDOWN_TICKS) {
                long remainingCooldown = SHOCKWAVE_COOLDOWN_TICKS - since;
                int remainingSeconds = (int) (remainingCooldown / 20);
                player.displayClientMessage(
                    Component.literal("§cFire Shockwave Cooldown: " + remainingSeconds + " seconds remaining"),
                    true
                );
            }
        }
    }

    // HUD cooldown rendering (client-side only)
    @OnlyIn(Dist.CLIENT)
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        Player player = mc.player;
        UUID playerId = player.getUUID();

        // Check if player has the enchantment
        int totalLevel = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
        }) {
            if (player.getItemBySlot(slot).isEmpty()) continue;
            if (EnchantmentHelper.getEnchantments(player.getItemBySlot(slot)).keySet().stream()
                .anyMatch(e -> e instanceof FireDefenseEnchantment)) {
                totalLevel += EnchantmentHelper.getItemEnchantmentLevel(
                    (Enchantment) EnchantmentHelper.getEnchantments(player.getItemBySlot(slot)).keySet().stream()
                        .filter(e -> e instanceof FireDefenseEnchantment).findFirst().orElse(null),
                    player.getItemBySlot(slot)
                );
            }
        }
        if (totalLevel <= 0) return;

        // Check cooldown
        if (lastShockwaveUse.containsKey(playerId)) {
            long currentTime = mc.level.getGameTime();
            long lastUse = lastShockwaveUse.get(playerId);
            long since = currentTime - lastUse;
            if (since < SHOCKWAVE_COOLDOWN_TICKS) {
                long remainingCooldown = SHOCKWAVE_COOLDOWN_TICKS - since;
                int remainingSeconds = (int) (remainingCooldown / 20);

                // Render text on the right side of the HUD
                GuiGraphics guiGraphics = event.getGuiGraphics();
                Window window = mc.getWindow();
                String text = "§cFire Shockwave: " + remainingSeconds + "s";
                int x = window.getGuiScaledWidth() - 110;
                int y = window.getGuiScaledHeight() / 2 - 40;

                // Remove formatting for drawString
                String plainText = text.replaceAll("§[0-9a-fk-or]", "");
                int color = 0xFFAA2222; // Red color

                guiGraphics.drawString(mc.font, plainText, x, y, color, true);
            }
        }
    }
}