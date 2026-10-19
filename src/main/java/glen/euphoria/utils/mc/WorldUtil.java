package glen.euphoria.utils.mc;

import glen.euphoria.utils.IMinecraft;
import lombok.experimental.UtilityClass;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.ToolComponent;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;

import java.util.Objects;
import java.util.stream.Stream;

@UtilityClass
public final class WorldUtil implements IMinecraft {

    public static void placeBlock(BlockHitResult hit, boolean swing) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        if (swing) mc.player.swingHand(Hand.MAIN_HAND);
    }

    public static boolean isDeadBodyNearby() {
        if (mc.world == null || mc.player == null) return false;
        return mc.world.getPlayers().parallelStream()
                .filter(e -> e != mc.player)
                .filter(e -> e.squaredDistanceTo(mc.player) <= 36)
                .anyMatch(LivingEntity::isDead);
    }

    public static Entity findNearestEntity(PlayerEntity toPlayer, float radius, boolean seeOnly) {
        if (mc.world == null || mc.player == null) return null;

        float mr = Float.MAX_VALUE;
        Entity entity = null;

        for (Entity e : mc.world.getEntities()) {
            float d = e.distanceTo(toPlayer);
            if (e != toPlayer && d <= radius && mc.player.canSee(e) == seeOnly) {
                if (d < mr) {
                    mr = d;
                    entity = e;
                }
            }
        }
        return entity;
    }

    public static double distance(Vec3d fromVec, Vec3d toVec) {
        return Math.sqrt(
                Math.pow(toVec.x - fromVec.x, 2)
                        + Math.pow(toVec.y - fromVec.y, 2)
                        + Math.pow(toVec.z - fromVec.z, 2)
        );
    }

    public static PlayerEntity findNearestPlayer(PlayerEntity toPlayer, float range, boolean seeOnly, boolean excludeFriends) {
        if (mc.world == null) return null;

        float minRange = Float.MAX_VALUE;
        PlayerEntity minPlayer = null;

        for (PlayerEntity player : mc.world.getPlayers()) {
            float dist = (float) distance(toPlayer.getEntityPos(), player.getEntityPos());

            // TODO: integrate your friend system here when excludeFriends is true

            if (player != toPlayer && dist <= range && player.canSee(toPlayer) == seeOnly) {
                if (dist < minRange) {
                    minRange = dist;
                    minPlayer = player;
                }
            }
        }

        return minPlayer;
    }

    public static Vec3d getPlayerLookVec(float yaw, float pitch) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;

        float h = MathHelper.cos(g);
        float i = MathHelper.sin(g);
        float j = MathHelper.cos(f);
        float k = MathHelper.sin(f);

        return new Vec3d(i * j, -k, h * j);
    }

    public static Vec3d getPlayerLookVec(PlayerEntity player) {
        return getPlayerLookVec(player.getYaw(), player.getPitch());
    }

    public static HitResult getHitResult(double radius) {
        if (mc.player == null) return null;
        return getHitResult(mc.player, false, mc.player.getYaw(), mc.player.getPitch(), radius);
    }

    public static HitResult getHitResult(PlayerEntity entity, boolean ignoreInvisibles, float yaw, float pitch, double distance) {
        if (entity == null || mc.world == null) return null;

        double d = distance;
        Vec3d cameraPosVec = entity.getCameraPosVec(1.0f);
        Vec3d rotationVec = getPlayerLookVec(yaw, pitch);
        Vec3d range = cameraPosVec.add(rotationVec.x * d, rotationVec.y * d, rotationVec.z * d);

        HitResult result = mc.world.raycast(new RaycastContext(
                cameraPosVec, range,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                entity
        ));

        double e = d * d;
        d = distance;

        if (result != null) {
            e = result.getPos().squaredDistanceTo(cameraPosVec);
        }

        Vec3d vec3d3 = cameraPosVec.add(rotationVec.x * d, rotationVec.y * d, rotationVec.z * d);
        Box box = entity.getBoundingBox().stretch(rotationVec.multiply(d)).expand(1.0, 1.0, 1.0);

        EntityHitResult entityHitResult = ProjectileUtil.raycast(
                entity, cameraPosVec, vec3d3, box,
                entityx -> !entityx.isSpectator() && entityx.canHit() && (!entityx.isInvisible() || !ignoreInvisibles),
                e
        );

        if (entityHitResult != null) {
            Vec3d vec3d4 = entityHitResult.getPos();
            double g = cameraPosVec.squaredDistanceTo(vec3d4);

            if (g < e || result == null) {
                result = g > Math.pow(distance, 2)
                        ? BlockHitResult.createMissed(vec3d4, Direction.getFacing(rotationVec.x, rotationVec.y, rotationVec.z), BlockPos.ofFloored(vec3d4))
                        : entityHitResult;
            }
        }

        return result;
    }

    public static Stream<WorldChunk> getLoadedChunks() {
        if (mc.player == null || mc.world == null) return Stream.empty();

        int radius = Math.max(2, mc.options.getClampedViewDistance()) + 3;
        int diameter = radius * 2 + 1;

        ChunkPos center = mc.player.getChunkPos();
        ChunkPos min = new ChunkPos(center.x - radius, center.z - radius);
        ChunkPos max = new ChunkPos(center.x + radius, center.z + radius);

        return Stream.iterate(min, pos -> {
                    int x = pos.x;
                    int z = pos.z;
                    x++;
                    if (x > max.x) {
                        x = min.x;
                        z++;
                    }
                    if (z > max.z)
                        throw new IllegalStateException("Stream limit didn't work.");
                    return new ChunkPos(x, z);
                }).limit((long) diameter * diameter)
                .filter(c -> mc.world.isChunkLoaded(c.x, c.z))
                .map(c -> mc.world.getChunk(c.x, c.z))
                .filter(Objects::nonNull);
    }

    public static boolean isShieldFacingAway(PlayerEntity player) {
        if (mc.player == null || player == null) return false;

        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d targetPos = player.getEntityPos();

        Vec3d directionToPlayer = playerPos.subtract(targetPos).normalize();

        float yaw = player.getYaw();
        float pitch = player.getPitch();
        Vec3d facingDirection = new Vec3d(
                -Math.sin(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch)),
                -Math.sin(Math.toRadians(pitch)),
                Math.cos(Math.toRadians(yaw)) * Math.cos(Math.toRadians(pitch))
        ).normalize();

        return facingDirection.dotProduct(directionToPlayer) < 0;
    }

    /**
     * 1.21.4: ToolItem no longer exists. Tools are identified by having a ToolComponent.
     * A "good" tool (diamond/netherite tier) is detected by checking mining speed rules —
     * diamond tools have a default mining speed of 8.0, netherite 9.0.
     */
    public static boolean isTool(ItemStack itemStack) {
        ToolComponent toolComponent = itemStack.get(DataComponentTypes.TOOL);
        if (toolComponent == null) return false;
        // Diamond mining speed = 8.0, Netherite = 9.0
        // All lower tiers (wood=2, stone=4, iron=6, gold=12) — we want only diamond+
        // Gold is 12.0 but is not a "good" tool, however the original argon code
        // only checked for DIAMOND and NETHERITE materials, so we match speeds >= 8.0 and != 12.0
        float speed = toolComponent.defaultMiningSpeed();
        return speed >= 8.0f && speed != 12.0f;
    }

    public static boolean isCrit(PlayerEntity player, Entity target) {
        return player.getAttackCooldownProgress(0.5F) > 0.9F
                && player.fallDistance > 0.0F
                && !player.isOnGround()
                && !player.isClimbing()
                && !player.isSubmergedInWater()
                && !player.hasStatusEffect(StatusEffects.BLINDNESS)
                && target instanceof LivingEntity;
    }

    public static void hitEntity(Entity entity, boolean swingHand) {
        if (mc.interactionManager == null || mc.player == null) return;
        mc.interactionManager.attackEntity(mc.player, entity);
        if (swingHand) mc.player.swingHand(Hand.MAIN_HAND);
    }
}