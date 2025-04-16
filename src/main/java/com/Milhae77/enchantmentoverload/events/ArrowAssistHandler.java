package com.Milhae77.enchantmentoverload.events;

import com.Milhae77.enchantmentoverload.init.ModEnchantments;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.WeakHashMap;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LightTexture;
import net.minecraftforge.event.TickEvent;
import java.util.Map;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashMap;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraftforge.event.entity.living.LivingDamageEvent;

// Change the class annotation to handle both client and server events
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ArrowAssistHandler {
    private static final KeyMapping ARROW_ASSIST_KEY = new KeyMapping(
            "key.enchantmentoverload.arrow_assist", GLFW.GLFW_KEY_Z, "key.categories.gameplay");
    private static final WeakHashMap<Player, LivingEntity> TARGETS = new WeakHashMap<>();
    private static final WeakHashMap<LivingEntity, Player> REVERSE_TARGETS = new WeakHashMap<>();
    private static final ResourceLocation MARKER_TEXTURE = new ResourceLocation("enchantmentoverload", "textures/misc/taget_position.png");
    private static boolean textureWarningShown = false;
    // Track arrows and their targets for homing animation
    private static final Map<AbstractArrow, LivingEntity> HOMING_ARROWS = new ConcurrentHashMap<>();
    // Remember last blocked direction for each arrow (simple learning)
    private static final Map<AbstractArrow, Vec3> ARROW_BLOCKED_DIR = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ARROW_ASSIST_KEY);
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ARROW_ASSIST_KEY.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null || mc.level == null) return;
            Player player = mc.player;
            ItemStack bow = player.getMainHandItem();
            if (bow.getItem() != Items.BOW) return;
            if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ARROW_ASSIST.get(), bow) == 0) return;

            // Manual raytrace for entity up to 80 blocks (was 30)
            double reach = 80.0D;
            Vec3 eyePos = player.getEyePosition(1.0F);
            Vec3 lookVec = player.getLookAngle();
            Vec3 reachVec = eyePos.add(lookVec.x * reach, lookVec.y * reach, lookVec.z * reach);
            AABB aabb = player.getBoundingBox().expandTowards(lookVec.scale(reach)).inflate(1.0D, 1.0D, 1.0D);

            LivingEntity closest = null;
            double closestDist = reach;
            for (var entity : mc.level.getEntities(player, aabb, e -> e instanceof LivingEntity && e != player)) {
                LivingEntity target = (LivingEntity) entity;
                AABB targetBox = target.getBoundingBox().inflate(0.3D);
                var optional = targetBox.clip(eyePos, reachVec);
                if (optional.isPresent()) {
                    double dist = optional.get().subtract(eyePos).length();
                    if (dist < closestDist) {
                        closestDist = dist;
                        closest = target;
                    }
                }
            }

            if (closest != null) {
                TARGETS.put(player, closest);
                REVERSE_TARGETS.put(closest, player);
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("Target set: " + closest.getName().getString()), true);
            } else {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.literal("No valid target in sight (up to 80 blocks)."), true);
            }
        }
    }

    @SubscribeEvent
    public static void onArrowSpawn(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow)) return;
        if (!(arrow.getOwner() instanceof Player player)) return;
        if (!TARGETS.containsKey(player)) return;
        ItemStack bow = player.getMainHandItem();
        if (bow.getItem() != Items.BOW) return;
        if (EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ARROW_ASSIST.get(), bow) == 0) return;

        LivingEntity target = TARGETS.get(player);
        if (target == null || !target.isAlive()) {
            TARGETS.remove(player);
            REVERSE_TARGETS.remove(target);
            return;
        }

        // Register this arrow for homing animation
        HOMING_ARROWS.put(arrow, target);

        // If target is Enderman, prevent despawn for 2 minutes (reset tickCount)
        if (target instanceof EnderMan) {
            arrow.tickCount = 0;
        }

        // Do not immediately set velocity toward target; let the homing logic handle it
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        if (HOMING_ARROWS.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        Iterator<Map.Entry<AbstractArrow, LivingEntity>> it = HOMING_ARROWS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<AbstractArrow, LivingEntity> entry = it.next();
            AbstractArrow arrow = entry.getKey();
            LivingEntity target = entry.getValue();

            // Remove if arrow or target is gone/dead (except for Enderman, keep tracking if alive)
            if (arrow == null || !arrow.isAlive() || target == null || !target.isAlive()) {
                it.remove();
                continue;
            }

            // If target is Enderman, keep arrow alive and prevent despawn
            if (target instanceof EnderMan) {
                arrow.tickCount = 0;
                arrow.setNoPhysics(false); // ensure arrow is not stuck
            }

            // Calculate direction to target (always update for Enderman, even if it teleports)
            double arrowX = arrow.getX();
            double arrowY = arrow.getY();
            double arrowZ = arrow.getZ();

            double targetX = target.getX();
            double targetY = target.getBoundingBox().maxY - 0.1; // Aim for head
            double targetZ = target.getZ();

            double dx = targetX - arrowX;
            double dy = targetY - arrowY;
            double dz = targetZ - arrowZ;
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

            // If very close, force arrow to hit target directly
            if (dist < 1.0) {
                double speed = Math.sqrt(arrow.getDeltaMovement().lengthSqr());
                if (speed < 0.01) speed = (target instanceof EnderMan) ? 2.5 : 1.5;
                double tx = dx / dist;
                double ty = dy / dist;
                double tz = dz / dist;
                arrow.setDeltaMovement(tx * speed, ty * speed, tz * speed);
                arrow.hasImpulse = true;
                continue;
            }

            // Check for obstacles between arrow and target
            Vec3 arrowPos = new Vec3(arrowX, arrowY, arrowZ);
            Vec3 targetPos = new Vec3(targetX, targetY, targetZ);
            HitResult hit = mc.level.clip(new ClipContext(
                arrowPos, targetPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow
            ));

            Vec3 steerDir;
            Vec3 lastBlocked = ARROW_BLOCKED_DIR.get(arrow);
            boolean blocked = false;

            if (hit.getType() == HitResult.Type.MISS || hit.getType() == HitResult.Type.ENTITY) {
                // No obstacle, steer directly to target
                steerDir = targetPos.subtract(arrowPos).normalize();
                ARROW_BLOCKED_DIR.remove(arrow); // Clear memory if path is open
            } else {
                // Improved obstacle avoidance with learning
                steerDir = null;
                double bestScore = Double.POSITIVE_INFINITY;
                Vec3 bestDir = null;
                int samples = 48;
                double radius = 1.0;
                Vec3 directDir = targetPos.subtract(arrowPos).normalize();

                for (int i = 0; i < samples; i++) {
                    // Sample directions in a sphere, but bias toward the direct direction
                    double theta = 2 * Math.PI * (i / (double)samples);
                    double phi = Math.acos(2 * (i / (double)samples) - 1);
                    double dxs = Math.sin(phi) * Math.cos(theta);
                    double dys = Math.cos(phi);
                    double dzs = Math.sin(phi) * Math.sin(theta);

                    // Blend sampled direction with direct direction for bias
                    double blend = 0.7;
                    double sx = dxs * (1 - blend) + directDir.x * blend;
                    double sy = dys * (1 - blend) + directDir.y * blend;
                    double sz = dzs * (1 - blend) + directDir.z * blend;
                    Vec3 sampleDir = new Vec3(sx, sy, sz).normalize();

                    // Try to keep Y close to target's head (avoid ground)
                    double targetYLevel = targetY - arrowY;
                    if (Math.abs(sampleDir.y - targetYLevel / dist) > 0.7) continue;

                    Vec3 sampleTarget = arrowPos.add(sampleDir.scale(radius));
                    HitResult sampleHit = mc.level.clip(new ClipContext(
                        arrowPos, sampleTarget, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow
                    ));

                    // Check if the path is open (no block in the way)
                    if (sampleHit.getType() == HitResult.Type.MISS || sampleHit.getType() == HitResult.Type.ENTITY) {
                        // Check for "tunnel" - is there space above and below for 1-block gaps?
                        Vec3 up = sampleTarget.add(0, 0.5, 0);
                        Vec3 down = sampleTarget.add(0, -0.5, 0);
                        boolean upClear = mc.level.clip(new ClipContext(
                            arrowPos, up, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow
                        )).getType() == HitResult.Type.MISS;
                        boolean downClear = mc.level.clip(new ClipContext(
                            arrowPos, down, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, arrow
                        )).getType() == HitResult.Type.MISS;

                        // Score: distance from this direction's endpoint to the real target,
                        // plus penalty if not aligned with direct direction,
                        // plus penalty if not enough vertical clearance
                        double alignPenalty = 1.0 - sampleDir.dot(directDir);
                        double clearancePenalty = (upClear && downClear) ? 0 : 0.5;
                        double score = sampleTarget.distanceTo(targetPos) + alignPenalty + clearancePenalty;

                        // Avoid going downward too much (into ground)
                        if (sampleDir.y < -0.5) score += 2.0;

                        // Penalize directions close to last blocked direction
                        if (lastBlocked != null && sampleDir.dot(lastBlocked) > 0.85) {
                            score += 1.5;
                        }

                        if (score < bestScore) {
                            bestScore = score;
                            bestDir = sampleDir;
                        }
                    }
                }
                if (bestDir != null) {
                    steerDir = bestDir.normalize();
                    ARROW_BLOCKED_DIR.put(arrow, null); // Found a new way, clear memory
                } else {
                    // All directions blocked, nudge arrow upward and forward to escape wall
                    steerDir = new Vec3(directDir.x, Math.abs(directDir.y) + 0.7, directDir.z).normalize();
                    ARROW_BLOCKED_DIR.put(arrow, directDir); // Remember this blocked direction
                }
            }

            // Sharper missile-like curve for higher precision
            double speed = Math.sqrt(arrow.getDeltaMovement().lengthSqr());
            // For Enderman, never slow down, always use at least 2.5
            if (target instanceof EnderMan && speed < 2.5) speed = 2.5;
            else if (speed < 0.01) speed = 1.5;

            double vx = arrow.getDeltaMovement().x;
            double vy = arrow.getDeltaMovement().y;
            double vz = arrow.getDeltaMovement().z;

            double tx = steerDir.x;
            double ty = steerDir.y;
            double tz = steerDir.z;

            // Increase blend for sharper turning (higher = more precise)
            double blend = 0.45;

            double nvx = vx * (1 - blend) + tx * speed * blend;
            double nvy = vy * (1 - blend) + ty * speed * blend;
            double nvz = vz * (1 - blend) + tz * speed * blend;

            arrow.setDeltaMovement(nvx, nvy, nvz);
            arrow.hasImpulse = true;
        }
    }

    @SubscribeEvent
    public static void onRenderWorld(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        LivingEntity target = TARGETS.get(mc.player);
        if (target == null || !target.isAlive()) return;

        // Only show marker if player is holding a bow with Arrow Assist
        ItemStack held = mc.player.getMainHandItem();
        boolean showMarker = held.getItem() == Items.BOW &&
            EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ARROW_ASSIST.get(), held) > 0;

        if (!showMarker) return;

        // Check if texture exists using try-catch
        if (!textureWarningShown) {
            try {
                mc.getResourceManager().getResource(MARKER_TEXTURE);
            } catch (Exception e) {
                System.out.println("[ArrowAssist] Warning: Marker texture not found.");
                textureWarningShown = true;
            }
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double x = target.getX() - cam.x;
        double y = target.getBoundingBox().maxY + 0.75 - cam.y; // Raise above head
        double z = target.getZ() - cam.z;

        poseStack.pushPose();
        poseStack.translate(x, y, z);
        // Billboard: rotate to always face camera
        poseStack.mulPose(mc.gameRenderer.getMainCamera().rotation());
        poseStack.scale(1.0f, 1.0f, 1.0f); // Adjust size as needed

        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();
        Matrix4f matrix = poseStack.last().pose();
        int light = LightTexture.FULL_BRIGHT;
        int overlay = OverlayTexture.NO_OVERLAY;

        VertexConsumer builder = buffer.getBuffer(RenderType.entityTranslucent(MARKER_TEXTURE));
        // Draw a 2D quad centered at (0,0,0), facing camera
        float halfSize = 0.5f; // Adjust for desired marker size
        builder.vertex(matrix, -halfSize, -halfSize, 0).color(255,255,255,255).uv(0,1).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
        builder.vertex(matrix,  halfSize, -halfSize, 0).color(255,255,255,255).uv(1,1).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
        builder.vertex(matrix,  halfSize,  halfSize, 0).color(255,255,255,255).uv(1,0).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
        builder.vertex(matrix, -halfSize,  halfSize, 0).color(255,255,255,255).uv(0,0).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();

        poseStack.popPose();
    }

    // HUD overlay: show distance to selected target in the bottom right
    @SubscribeEvent
    public static void onRenderGuiOverlay(RenderGuiOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        LivingEntity target = TARGETS.get(mc.player);
        if (target == null || !target.isAlive()) return;

        ItemStack held = mc.player.getMainHandItem();
        if (held.getItem() != Items.BOW ||
            EnchantmentHelper.getItemEnchantmentLevel(ModEnchantments.ARROW_ASSIST.get(), held) == 0) return;

        // Calculate distance
        double px = mc.player.getX();
        double py = mc.player.getY() + mc.player.getEyeHeight();
        double pz = mc.player.getZ();
        double tx = target.getX();
        double ty = target.getBoundingBox().maxY;
        double tz = target.getZ();
        double dist = Math.sqrt((px - tx) * (px - tx) + (py - ty) * (py - ty) + (pz - tz) * (pz - tz));
        String distText = String.format("Target Distance: %.1f blocks", dist);

        // Render on HUD (bottom right)
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = screenWidth - 10 - mc.font.width(distText);
        int y = screenHeight - 30;

        event.getGuiGraphics().drawString(mc.font, distText, x, y, 0x00FFFF, true);
    }
}
