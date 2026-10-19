package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.ItemUseEvent;
import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.mc.BlockUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import glen.euphoria.utils.mc.WorldUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

public final class AutoAnchor extends Module {

    private final KeybindSetting placeKey = new KeybindSetting("Place Key", GLFW.GLFW_KEY_V, false);
    private final BooleanSetting holdToAnchor = new BooleanSetting("Hold to Anchor", true);
    private final NumberSetting switchDelay = new NumberSetting("Switch Delay", 0, 20, 0, 1);
    private final NumberSetting glowstoneDelay = new NumberSetting("Glowstone Delay", 0, 20, 0, 1);
    private final NumberSetting explodeDelay = new NumberSetting("Explode Delay", 0, 20, 0, 1);
    private final NumberSetting explodeSlot = new NumberSetting("Explode Slot", 1, 9, 6, 1);
    private final BooleanSetting onlyOwn = new BooleanSetting("Only Own", true);
    private final BooleanSetting autoCharge = new BooleanSetting("Auto Charge", true);
    private final BooleanSetting autoExplode = new BooleanSetting("Auto Explode", true);
    private final BooleanSetting safeAnchor = new BooleanSetting("Safe Anchor", false);

    private final Set<BlockPos> myAnchors = new HashSet<>();
    private boolean wasPlaceKeyDown = false;

    private int switchClock = 0;
    private int glowstoneClock = 0;
    private int explodeClock = 0;

    private boolean awaitingShield = false;
    private BlockPos pendingAnchorPos = null;

    private boolean needsPlace = false;
    private int placeClock = 0;

    public AutoAnchor() {
        super("Auto Anchor", "Automates respawn anchor placement and detonation", -1, Category.COMBAT);
        addSettings(placeKey, holdToAnchor, switchDelay, glowstoneDelay, explodeDelay,
                explodeSlot, onlyOwn, autoCharge, autoExplode, safeAnchor);
    }

    @Override
    public void onEnable() {
        myAnchors.clear();
        wasPlaceKeyDown = false;
        needsPlace = false;
        resetClocks();
        awaitingShield = false;
        pendingAnchorPos = null;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        myAnchors.clear();
        needsPlace = false;
        resetClocks();
        awaitingShield = false;
        pendingAnchorPos = null;
        super.onDisable();
    }

    private void resetClocks() {
        switchClock = 0;
        glowstoneClock = 0;
        explodeClock = 0;
        placeClock = 0;
    }

    private boolean isKeyHeld() {
        int key = placeKey.getKeyCode();
        long handle = mc.getWindow().getHandle();

        if (key < 8) {
            return GLFW.glfwGetMouseButton(handle, key) == GLFW.GLFW_PRESS;
        } else {
            return GLFW.glfwGetKey(handle, key) == GLFW.GLFW_PRESS;
        }
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null || mc.currentScreen != null) return;

        boolean placeKeyDown = isKeyHeld();

        // Handle pending place
        if (needsPlace) {
            if (placeClock < switchDelay.getValueInt()) {
                placeClock++;
                return;
            }

            if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
                needsPlace = false;
                placeClock = 0;
                return;
            }

            BlockHitResult hit = getBlockTarget(4.5);
            if (hit == null) {
                needsPlace = false;
                placeClock = 0;
                return;
            }

            BlockPos placePos = getPlacementPos(hit);

            if (!mc.world.getBlockState(placePos).isReplaceable()) {
                needsPlace = false;
                placeClock = 0;
                return;
            }

            WorldUtil.placeBlock(hit, true);
            trackAnchor(placePos);

            if (canSafeAnchor(placePos) && placeKeyDown) {
                awaitingShield = true;
                pendingAnchorPos = placePos;
            }

            needsPlace = false;
            placeClock = 0;
            return;
        }

        // Place anchor
        if (placeKeyDown && (holdToAnchor.getValue() || !wasPlaceKeyDown)) {
            BlockHitResult hit = getBlockTarget(4.5);
            if (hit != null) {
                BlockPos targetBlock = hit.getBlockPos();
                if (!BlockUtil.isBlock(targetBlock, Blocks.RESPAWN_ANCHOR) || mc.world.getBlockState(targetBlock).isReplaceable()) {
                    BlockPos placePos = getPlacementPos(hit);
                    if (mc.world.getBlockState(placePos).isReplaceable()) {
                        if (!mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
                            if (!InventoryUtil.swapToSlot(Items.RESPAWN_ANCHOR)) {
                                wasPlaceKeyDown = placeKeyDown;
                                return;
                            }
                            if (switchDelay.getValueInt() > 0) {
                                needsPlace = true;
                                placeClock = 0;
                                wasPlaceKeyDown = placeKeyDown;
                                return;
                            }
                        }

                        if (mc.player.getMainHandStack().isOf(Items.RESPAWN_ANCHOR)) {
                            WorldUtil.placeBlock(hit, true);
                            trackAnchor(placePos);

                            if (canSafeAnchor(placePos)) {
                                awaitingShield = true;
                                pendingAnchorPos = placePos;
                            }
                            wasPlaceKeyDown = placeKeyDown;
                            return;
                        }
                    }
                }
            }
        }
        wasPlaceKeyDown = placeKeyDown;

        // Safe anchor shield
        if (awaitingShield && pendingAnchorPos != null) {
            if (!placeKeyDown) {
                awaitingShield = false;
                pendingAnchorPos = null;
            } else {
                tryPlaceShield();
                return;
            }
        }

        // Auto charge/explode
        BlockHitResult anchorHit = null;
        if (mc.crosshairTarget instanceof BlockHitResult hit) {
            if (BlockUtil.isBlock(hit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
                anchorHit = hit;
            } else if (mc.world.getBlockState(hit.getBlockPos()).isReplaceable() && BlockUtil.isBlock(hit.getBlockPos().down(), Blocks.RESPAWN_ANCHOR)) {
                anchorHit = hit;
            }
        }
        if (anchorHit == null) {
            HitResult ray = WorldUtil.getHitResult(4.5);
            if (ray instanceof BlockHitResult rayHit) {
                if (BlockUtil.isBlock(rayHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)) {
                    anchorHit = rayHit;
                } else if (mc.world.getBlockState(rayHit.getBlockPos()).isReplaceable() && BlockUtil.isBlock(rayHit.getBlockPos().down(), Blocks.RESPAWN_ANCHOR)) {
                    anchorHit = rayHit;
                }
            }
        }

        if (anchorHit == null) return;

        BlockPos anchorPos = BlockUtil.isBlock(anchorHit.getBlockPos(), Blocks.RESPAWN_ANCHOR)
                ? anchorHit.getBlockPos()
                : anchorHit.getBlockPos().down();

        if (onlyOwn.getValue() && !isMyAnchor(anchorPos)) return;

        // Charge
        if (autoCharge.getValue() && BlockUtil.isAnchorNotCharged(anchorPos)) {
            if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                if (switchClock < switchDelay.getValueInt()) {
                    switchClock++;
                    return;
                }

                if (!InventoryUtil.swapToSlot(Items.GLOWSTONE)) return;

                switchClock = 0;
                if (glowstoneDelay.getValueInt() > 0) {
                    return;
                }
            }

            if (glowstoneClock < glowstoneDelay.getValueInt()) {
                glowstoneClock++;
                return;
            }

            glowstoneClock = 0;
            if (mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
                WorldUtil.placeBlock(anchorHit, true);
            }
            return;
        }

        // Explode
        if (autoExplode.getValue() && BlockUtil.isAnchorCharged(anchorPos)) {
            int slot = getExplodeSlot();

            if (mc.player.getInventory().getSelectedSlot() != slot) {
                if (switchClock < switchDelay.getValueInt()) {
                    switchClock++;
                    return;
                }

                if (!SlotGuard.tryClaim()) return;

                switchClock = 0;
                mc.player.getInventory().setSelectedSlot(slot);
                InventoryUtil.syncSelectedSlot();

                if (explodeDelay.getValueInt() > 0) {
                    return;
                }
            }

            if (explodeClock < explodeDelay.getValueInt()) {
                explodeClock++;
                return;
            }

            explodeClock = 0;
            WorldUtil.placeBlock(anchorHit, true);
            removeAnchor(anchorPos);
            return;
        }
    }

    private int getExplodeSlot() {
        int configured = explodeSlot.getValueInt() - 1;
        if (configured >= 0 && configured < 9) {
            ItemStack stack = mc.player.getInventory().getStack(configured);
            if (!stack.isOf(Items.GLOWSTONE) && !stack.isOf(Items.RESPAWN_ANCHOR)) {
                return configured;
            }
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.isOf(Items.TOTEM_OF_UNDYING)) return i;
        }
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isOf(Items.GLOWSTONE) && !stack.isOf(Items.RESPAWN_ANCHOR)) return i;
        }
        return configured >= 0 && configured < 9 ? configured : 0;
    }

    private void trackAnchor(BlockPos pos) {
        myAnchors.add(pos);
        myAnchors.add(pos.up());
        myAnchors.add(pos.down());
        myAnchors.add(pos.north());
        myAnchors.add(pos.south());
        myAnchors.add(pos.east());
        myAnchors.add(pos.west());
    }

    private boolean isMyAnchor(BlockPos pos) {
        if (myAnchors.contains(pos)) return true;

        for (BlockPos tracked : myAnchors) {
            if (tracked.isWithinDistance(pos, 2)) {
                return true;
            }
        }

        return false;
    }

    private void removeAnchor(BlockPos pos) {
        myAnchors.remove(pos);
        myAnchors.removeIf(tracked -> tracked.isWithinDistance(pos, 2));
    }

    private boolean canSafeAnchor(BlockPos anchorPos) {
        if (!safeAnchor.getValue()) return false;
        if (mc.player == null) return false;
        int playerFeetY = (int) Math.floor(mc.player.getY() + 0.15);
        return anchorPos.getY() <= playerFeetY;
    }

    private void tryPlaceShield() {
        if (pendingAnchorPos == null) return;
        if (!canSafeAnchor(pendingAnchorPos)) {
            awaitingShield = false;
            pendingAnchorPos = null;
            return;
        }
        BlockHitResult hit = getBlockTarget(4.5);
        if (hit == null) return;

        BlockPos lookingAt = hit.getBlockPos();
        BlockPos placePos = getPlacementPos(hit);

        if (lookingAt.equals(pendingAnchorPos)) return;
        if (placePos.equals(pendingAnchorPos)) return;
        if (BlockUtil.isBlock(lookingAt, Blocks.RESPAWN_ANCHOR)) return;
        if (!mc.world.getBlockState(placePos).isReplaceable()) return;
        if (!isShieldBetweenPlayerAndAnchor(placePos)) return;

        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) {
            if (!InventoryUtil.swapToSlot(Items.GLOWSTONE)) return;
        }

        if (!mc.player.getMainHandStack().isOf(Items.GLOWSTONE)) return;

        WorldUtil.placeBlock(hit, true);
        awaitingShield = false;
    }

    private boolean isShieldBetweenPlayerAndAnchor(BlockPos shieldPos) {
        if (pendingAnchorPos == null) return false;

        // Must be near the anchor (within 2.5 blocks)
        if (!shieldPos.isWithinDistance(pendingAnchorPos, 2.5)) return false;

        // Y level must be close to anchor
        int yDiff = Math.abs(shieldPos.getY() - pendingAnchorPos.getY());
        if (yDiff > 2) return false;

        // Must not be the anchor itself
        if (shieldPos.equals(pendingAnchorPos)) return false;

        // Must be within reach of the player
        Vec3d playerPos = mc.player.getEntityPos();
        Vec3d shieldCenter = Vec3d.ofCenter(shieldPos);
        if (playerPos.distanceTo(shieldCenter) > 5.0) return false;

        return true;
    }

    private BlockPos getPlacementPos(BlockHitResult hit) {
        BlockPos hitPos = hit.getBlockPos();
        if (mc.world == null) return hitPos.offset(hit.getSide());
        return mc.world.getBlockState(hitPos).isReplaceable() ? hitPos : hitPos.offset(hit.getSide());
    }

    private BlockHitResult getBlockTarget(double reach) {
        if (mc.player == null || mc.world == null) return null;
        if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            return bhr;
        }
        Vec3d cameraPosVec = mc.player.getCameraPosVec(1.0f);
        Vec3d rotationVec = WorldUtil.getPlayerLookVec(mc.player);
        Vec3d range = cameraPosVec.add(rotationVec.multiply(reach));
        BlockHitResult blockRay = mc.world.raycast(new RaycastContext(
                cameraPosVec, range,
                RaycastContext.ShapeType.OUTLINE,
                RaycastContext.FluidHandling.NONE,
                mc.player
        ));
        if (blockRay != null && blockRay.getType() == HitResult.Type.BLOCK) {
            return blockRay;
        }
        return null;
    }

    @EventHandler
    private void onItemUse(ItemUseEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (!(mc.crosshairTarget instanceof BlockHitResult hitResult)) return;
        if (hitResult.getType() != HitResult.Type.BLOCK) return;

        if (mc.player.getMainHandStack().getItem() == Items.RESPAWN_ANCHOR) {
            BlockPos anchorPos = getPlacementPos(hitResult);
            trackAnchor(anchorPos);
        }

        BlockPos bp = hitResult.getBlockPos();
        if (mc.player.getMainHandStack().getItem() != Items.GLOWSTONE && BlockUtil.isAnchorCharged(bp)) {
            removeAnchor(bp);
        } else if (mc.world.getBlockState(bp).isReplaceable() && BlockUtil.isAnchorCharged(bp.down())) {
            removeAnchor(bp.down());
        }
    }
}