package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.AttackEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import glen.euphoria.utils.rotation.RotationController;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public class HitCob extends Module {
    private static final double SPEED_STOPPED = 0.05;
    private static final double SPEED_WALKING = 0.13;
    private static final double SPEED_SPRINTING = 0.26;

    private PlayerEntity pendingTarget = null;
    private int restoreSlot = -1;

    public HitCob() {
        super("Hit Cob", "Places cobweb at player feet when you hit them (DO NOT USE ITS BEING TESTED)", Category.COMBAT);
    }

    @Override
    public void onDisable() {
        if (restoreSlot != -1 && mc.player != null && mc.player.getInventory().getSelectedSlot() != restoreSlot) {
            if (SlotGuard.tryClaim()) {
                mc.player.getInventory().setSelectedSlot(restoreSlot);
                InventoryUtil.syncSelectedSlot();
            }
        }
        restoreSlot = -1;
        pendingTarget = null;
        super.onDisable();
    }

    @EventHandler
    private void onAttack(AttackEvent event) {
        if (isNull() || !(event.getTarget() instanceof PlayerEntity target)) return;
        pendingTarget = target;
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull()) return;

        // Restore slot on the tick AFTER placement (prevents same-tick multi-action swap)
        if (restoreSlot != -1) {
            if (mc.player != null && mc.player.getInventory().getSelectedSlot() != restoreSlot) {
                if (!SlotGuard.tryClaim()) return;
                mc.player.getInventory().setSelectedSlot(restoreSlot);
                InventoryUtil.syncSelectedSlot();
            }
            restoreSlot = -1;
            return;
        }

        if (pendingTarget == null || !pendingTarget.isAlive()) {
            pendingTarget = null;
            return;
        }

        PlayerEntity target = pendingTarget;
        pendingTarget = null;

        if (!InventoryUtil.hasItem(Items.COBWEB)) return;

        BlockPos feetPos = target.getBlockPos();
        if (mc.world.getBlockState(feetPos).getBlock() == Blocks.COBWEB) return;
        if (mc.world.getBlockState(feetPos).getBlock() == Blocks.WATER) return;

        double distance = mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(feetPos));
        if (distance > 4.5) return;

        double targetSpeed = Math.hypot(target.getVelocity().x, target.getVelocity().z);
        Vec3d knockbackDirection = target.getEntityPos().subtract(mc.player.getEntityPos()).normalize();

        double knockbackDistance;
        if (targetSpeed < SPEED_STOPPED) {
            knockbackDistance = mc.player.isSprinting() ? 1.8 : 1.2;
        } else if (targetSpeed < SPEED_WALKING) {
            knockbackDistance = mc.player.isSprinting() ? 1.5 : 1.0;
        } else {
            knockbackDistance = mc.player.isSprinting() ? 1.2 : 0.8;
        }

        Vec3d predictedPos = target.getEntityPos().add(knockbackDirection.multiply(knockbackDistance));
        BlockPos predictedFeet = BlockPos.ofFloored(predictedPos);

        if (mc.world.getBlockState(predictedFeet).getBlock() == Blocks.COBWEB) return;
        if (mc.world.getBlockState(predictedFeet).getBlock() == Blocks.WATER) return;

        if (!mc.world.getBlockState(predictedFeet).isAir()) {
            predictedFeet = feetPos;
        }

        BlockPos groundPos = predictedFeet.down();
        if (mc.world.getBlockState(groundPos).isAir()) return;

        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(predictedFeet)) > 4.5) return;

        Vec3d hitVec = Vec3d.ofCenter(groundPos).add(0, 0.5, 0);
        float[] rotation = calculateRotation(hitVec);

        // Rotation requested on the tick
        RotationController.request(rotation[0], rotation[1], 15);

        // Store slot to restore on the subsequent tick
        restoreSlot = mc.player.getInventory().getSelectedSlot();
        if (!InventoryUtil.swapToSlot(Items.COBWEB)) return;

        BlockHitResult hitResult = new BlockHitResult(
                hitVec,
                Direction.UP,
                groundPos,
                false
        );

        if (mc.interactionManager != null && mc.player.getMainHandStack().isOf(Items.COBWEB)) {
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
        }
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private int findCobwebInHotbar() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.COBWEB) {
                return i;
            }
        }
        return -1;
    }

    private float[] calculateRotation(Vec3d target) {
        Vec3d diff = target.subtract(mc.player.getEyePos());
        double distance = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
        float yaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0f;
        float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, distance));
        return new float[]{MathHelper.wrapDegrees(yaw), MathHelper.clamp(pitch, -89.0f, 89.0f)};
    }
}
