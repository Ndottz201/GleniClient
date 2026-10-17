package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.modules.misc.Teams;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.friend.FriendManager;
import glen.euphoria.utils.mc.ToolItems;
import glen.euphoria.utils.rotation.RotationController;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class AimAssist extends Module {

    private final NumberSetting fov = new NumberSetting("FOV", 10.0, 180.0, 90.0, 1.0);
    private final NumberSetting range = new NumberSetting("Range", 1.0, 10.0, 5.0, 0.1);
    private final NumberSetting speed = new NumberSetting("Speed", 1.0, 15.0, 10.0, 0.5);

    private final BooleanSetting targetPlayers = new BooleanSetting("Target Players", true);
    private final BooleanSetting targetMobs = new BooleanSetting("Target Mobs", false);
    private final BooleanSetting weaponsOnly = new BooleanSetting("Weapons Only", false);
    private final BooleanSetting throughWalls = new BooleanSetting("Through Walls", false);
    private final BooleanSetting ignoreBlocks = new BooleanSetting("Ignore Blocks", true);

    private Entity currentTarget = null;

    public AimAssist() {
        super("Aim Assist", "Gives you assistance on your aim", Category.COMBAT);
        addSettings(fov, range, speed, targetPlayers, targetMobs, weaponsOnly, throughWalls, ignoreBlocks);
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || mc.currentScreen != null)
            return;

        if (weaponsOnly.getValue() && !isHoldingWeapon())
            return;

        if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.BLOCK
                && mc.options.attackKey.isPressed()) {
            return;
        }

        if (ignoreBlocks.getValue() && mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK) {
            return;
        }

        currentTarget = findBestTarget();

        if (currentTarget != null) {
            if (!throughWalls.getValue() && !mc.player.canSee(currentTarget))
                return;

            Vec3d chestPos = getChestPosition(currentTarget);
            float[] rotation = calculateRotation(chestPos);

            // Fixed per-tick fraction instead of wall-clock frame easing
            float step = Math.min(1.0f, speed.getValueFloat() / 10.0f);
            float newYaw = mc.player.getYaw() + MathHelper.wrapDegrees(rotation[0] - mc.player.getYaw()) * step;
            float newPitch = MathHelper.clamp(mc.player.getPitch() + (rotation[1] - mc.player.getPitch()) * step, -89.0f, 89.0f);

            RotationController.request(newYaw, newPitch, 1);
        }
    }

    private Entity findBestTarget() {
        if (isNull())
            return null;

        Entity bestTarget = null;
        double bestScore = Double.MAX_VALUE;

        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity))
                continue;

            double distance = mc.player.distanceTo(entity);
            if (distance > range.getValue())
                continue;

            Vec3d chestPos = getChestPosition(entity);
            float[] rotation = calculateRotation(chestPos);
            double fovDistance = getFOVDistance(rotation[0], rotation[1]);

            if (fovDistance <= fov.getValue() / 2.0) {
                double score = distance + (fovDistance * 2.0);
                if (score < bestScore) {
                    bestScore = score;
                    bestTarget = entity;
                }
            }
        }

        return bestTarget;
    }

    private boolean isValidTarget(Entity entity) {
        if (entity == null || entity == mc.player || !(entity instanceof LivingEntity livingEntity))
            return false;
        if (!livingEntity.isAlive() || livingEntity.isDead())
            return false;
        if (Teams.isTeammate(entity))
            return false;
        if (entity instanceof PlayerEntity player && FriendManager.isFriend(player.getUuid()))
            return false;

        return entity instanceof PlayerEntity ? targetPlayers.getValue() : targetMobs.getValue();
    }

    private Vec3d getChestPosition(Entity entity) {
        return new Vec3d(entity.getX(), entity.getEyeY(), entity.getZ());
    }

    private float[] calculateRotation(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0f, 89.0f)};
    }

    private double getFOVDistance(float targetYaw, float targetPitch) {
        float yawDiff = MathHelper.wrapDegrees(targetYaw - mc.player.getYaw());
        float pitchDiff = targetPitch - mc.player.getPitch();
        return Math.sqrt(yawDiff * yawDiff + pitchDiff * pitchDiff);
    }

    private boolean isHoldingWeapon() {
        if (mc.player == null)
            return false;
        if (mc.player.getMainHandStack().isEmpty())
            return false;
        Item heldItem = mc.player.getMainHandStack().getItem();
        return ToolItems.isSword(heldItem) || heldItem instanceof AxeItem;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        currentTarget = null;
    }
}
