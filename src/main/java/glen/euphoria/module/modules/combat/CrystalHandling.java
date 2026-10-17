package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import glen.euphoria.utils.mc.BlockUtil;
import glen.euphoria.utils.mc.CrystalUtil;
import glen.euphoria.utils.mc.WorldUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import glen.euphoria.utils.mc.ToolItems;
import glen.euphoria.utils.rotation.RotationUtils;
import glen.euphoria.utils.rotation.SilentRotation;
import net.minecraft.item.ShovelItem;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public final class CrystalHandling extends Module {

    private final KeybindSetting obsidianPlaceBind = new KeybindSetting(
            "Obsidian Place Bind", GLFW.GLFW_MOUSE_BUTTON_LEFT, false
    );

    private final KeybindSetting autoCrystalBind = new KeybindSetting(
            "Auto Crystal Bind", GLFW.GLFW_MOUSE_BUTTON_LEFT, false
    );

    private final BooleanSetting headbob = new BooleanSetting(
            "Headbob", true
    );

    private final BooleanSetting damageCheck = new BooleanSetting(
            "Damage Check", false
    );

    private final NumberSetting placeDelay = new NumberSetting(
            "Place Delay", 0, 10, 0, 1
    );

    public boolean crystalling;

    private long lastAttackTime = 0;
    private PlayerEntity lastAttackedPlayer = null;
    private int placeClock = 0;

    /**
     * After placing obsidian, the crystal placement is queued here
     * and handled on the NEXT tick. This prevents two block-interactions
     * in a single tick (the main cause of BadPackets flags).
     */
    private BlockPos pendingCrystalPos = null;

    public CrystalHandling() {
        super("CrystalHandling",
                "Handles obsidian placement and crystaling in one module",
                -1,
                Category.COMBAT);
        addSettings(obsidianPlaceBind, autoCrystalBind, headbob, damageCheck, placeDelay);
    }

    @Override
    public void onEnable() {
        resetState();
        super.onEnable();
    }

    @Override
    public void onDisable() {
        resetState();
        super.onDisable();
    }

    /**
     * Returns true if the player is holding an end crystal or obsidian and
     * is looking at a BLOCK (not an entity). This prevents breaking blocks
     * but still allows hitting players, mobs, and crystals.
     */
    public boolean shouldCancelBlockBreaking() {
        if (mc.player == null || !isEnabled()) return false;

        Item mainHand = mc.player.getMainHandStack().getItem();
        if (mainHand != Items.END_CRYSTAL && mainHand != Items.OBSIDIAN) return false;

        HitResult target = mc.crosshairTarget;
        if (target == null) return false;

        return !(target instanceof EntityHitResult);
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;
        if (mc.currentScreen != null) return;

        // Don't interfere while holding tools
        if (isHoldingTool()) {
            crystalling = false;
            pendingCrystalPos = null;
            return;
        }

        // Decrement delay before the gate check (matches original timing)
        if (placeClock > 0) placeClock--;

        // ── Keybind check ──
        boolean obsidianDown = KeyUtils.isKeyPressed(obsidianPlaceBind.getKeyCode());
        boolean crystalDown  = KeyUtils.isKeyPressed(autoCrystalBind.getKeyCode());
        boolean actionDown   = obsidianDown || crystalDown;

        if (!actionDown) {
            crystalling = false;
            pendingCrystalPos = null;
            return;
        }

        HitResult target = mc.crosshairTarget;

        // ── ATTACK CRYSTAL (always allowed, even during place delay) ──
        if (target instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof EndCrystalEntity crystal) {
            if (mc.player.getEntityPos().distanceTo(crystal.getEntityPos()) <= 4.5) {
                mc.interactionManager.attackEntity(mc.player, crystal);
                mc.player.swingHand(Hand.MAIN_HAND);
                crystalling = true;
                placeClock = 0;
                pendingCrystalPos = null;
                return;
            }
        }

        // ── HEADBOB: BREAK CRYSTAL IF AIMING AT OBSIDIAN WITH CRYSTAL ON TOP ──
        if (headbob.getValue() && target instanceof BlockHitResult blockHit && target.getType() == HitResult.Type.BLOCK) {
            BlockPos lookingAt = blockHit.getBlockPos();
            if (isObsidianOrBedrock(lookingAt)) {
                EndCrystalEntity crystal = getCrystalOnBlock(lookingAt);
                if (crystal != null && mc.player.getEntityPos().distanceTo(crystal.getEntityPos()) <= 4.5) {
                    Vec3d center = crystal.getBoundingBox().getCenter();
                    float crystalPitch = calculatePitchTo(center);
                    float crystalYaw = calculateYawTo(center);
                    SilentRotation.set(crystalPitch, crystalYaw, () -> {
                        if (mc.player != null && mc.interactionManager != null && crystal.isAlive()) {
                            mc.interactionManager.attackEntity(mc.player, crystal);
                            mc.player.swingHand(Hand.MAIN_HAND);
                        }
                    });
                    crystalling = true;
                    placeClock = 0;
                    pendingCrystalPos = null;
                    return;
                }
            }
        }

        // ── TRACK PLAYER HITS (for iFrame / damage check) ──
        if (target instanceof EntityHitResult ehr
                && ehr.getEntity() instanceof PlayerEntity player) {
            lastAttackedPlayer = player;
            lastAttackTime = System.currentTimeMillis();
            crystalling = false;
            return;
        }

        // ── PLACE DELAY GATE ──
        if (placeClock > 0) return;

        // ── PENDING CRYSTAL (queued after obsidian was placed last tick) ──
        if (pendingCrystalPos != null) {
            handlePendingCrystal();
            return;
        }

        // ── REQUIRE A BLOCK TARGET FROM HERE ON ──
        if (!(target instanceof BlockHitResult hit)
                || target.getType() != HitResult.Type.BLOCK) {
            crystalling = false;
            return;
        }

        BlockPos lookingAt = hit.getBlockPos();

        if (isObsidianOrBedrock(lookingAt)) {
            handleCrystalPlacement(lookingAt, hit);
        } else {
            handleObsidianPlacement(hit);
        }
    }

    // ═══════════════════════════════════════════════════════
    //                   PLACEMENT LOGIC
    // ═══════════════════════════════════════════════════════

    /**
     * Looking at obsidian / bedrock → swap to crystal (if needed) and place.
     * One swap + one place per tick is legitimate (number-key + right-click).
     */
    private void handleCrystalPlacement(BlockPos surfacePos, BlockHitResult hit) {
        // Can a crystal actually go here?
        if (!CrystalUtil.canPlaceCrystalClientAssumeObsidian(surfacePos)) {
            crystalling = false;
            return;
        }

        // A crystal already exists on this block — don't double-place
        if (hasCrystalAt(surfacePos)) {
            crystalling = false;
            return;
        }

        // Make sure mainhand holds a crystal
        if (!isMainHandItem(Items.END_CRYSTAL)) {
            if (!trySwap(Items.END_CRYSTAL)) {
                crystalling = false;
                return;
            }
            // Swap succeeded — fall through to place (1 swap + 1 place = legit)
        }

        // Respect iFrame check
        if (damageCheck.getValue() && shouldWaitForIFrames()) {
            crystalling = false;
            return;
        }

        placeCrystalOnBlock(surfacePos, hit);
        placeClock = placeDelay.getValueInt();
        crystalling = true;
    }

    /**
     * Looking at something that isn't obsidian / bedrock → place obsidian,
     * then queue a crystal for the next tick.
     * Obsidian can be placed at any reachable Y level.
     */
    private void handleObsidianPlacement(BlockHitResult hit) {
        Direction face      = hit.getSide();
        BlockPos  targetPos = hit.getBlockPos().offset(face);

        // Safe reach check (4.5 blocks)
        if (mc.player.getEyePos().distanceTo(Vec3d.ofCenter(targetPos)) > 4.5) {
            crystalling = false;
            return;
        }

        // Cannot place inside player's own bounding box
        if (mc.player.getBoundingBox().intersects(new Box(targetPos))) {
            crystalling = false;
            return;
        }

        // Target space must be empty / replaceable
        if (!mc.world.getBlockState(targetPos).isReplaceable()) {
            crystalling = false;
            return;
        }

        // No entity standing in the way
        if (isEntityBlockingPlacement(targetPos)) {
            crystalling = false;
            return;
        }

        // Make sure mainhand holds obsidian
        if (!isMainHandItem(Items.OBSIDIAN)) {
            if (!trySwap(Items.OBSIDIAN)) {
                crystalling = false;
                return;
            }
            // Swap succeeded — fall through to place
        }

        // Place obsidian (one interaction this tick)
        WorldUtil.placeBlock(hit, true);

        // Queue crystal placement for the next tick if viable
        if (CrystalUtil.canPlaceCrystalClientAssumeObsidian(targetPos)) {
            pendingCrystalPos = targetPos;
        }

        crystalling = false;
    }

    /**
     * Runs one tick after obsidian was placed.
     * Swaps to crystal and places it on the saved position.
     */
    private void handlePendingCrystal() {
        BlockPos pos = pendingCrystalPos;
        pendingCrystalPos = null;

        // Re-validate
        if (!CrystalUtil.canPlaceCrystalClientAssumeObsidian(pos)) {
            crystalling = false;
            return;
        }

        if (hasCrystalAt(pos)) {
            crystalling = false;
            return;
        }

        // Swap to crystal if needed (one swap + one place = legit)
        if (!isMainHandItem(Items.END_CRYSTAL)) {
            if (!trySwap(Items.END_CRYSTAL)) {
                crystalling = false;
                return;
            }
        }

        if (damageCheck.getValue() && shouldWaitForIFrames()) {
            crystalling = false;
            return;
        }

        BlockHitResult hit = null;
        if (mc.crosshairTarget instanceof BlockHitResult bhr && bhr.getBlockPos().equals(pos)) {
            hit = bhr;
        }
        placeCrystalOnBlock(pos, hit);

        placeClock = placeDelay.getValueInt();
        crystalling = true;
    }

    // ═══════════════════════════════════════════════════════
    //                      UTILITIES
    // ═══════════════════════════════════════════════════════

    /**
     * Send a crystal-place interaction on the given obsidian block.
     * Prefers the player's actual crosshair hit result when aiming at the block,
     * which matches the server-side raytrace perfectly.
     */
    private void placeCrystalOnBlock(BlockPos pos, BlockHitResult originalHit) {
        BlockHitResult placeHit;
        if (originalHit != null && originalHit.getBlockPos().equals(pos)) {
            placeHit = originalHit;
        } else {
            Vec3d hitVec = new Vec3d(
                    pos.getX() + 0.5,
                    pos.getY() + 1.0,
                    pos.getZ() + 0.5
            );
            placeHit = new BlockHitResult(
                    hitVec, Direction.UP, pos, false
            );
        }
        WorldUtil.placeBlock(placeHit, true);
    }

    /**
     * Returns true if an end-crystal entity already sits above {@code basePos}.
     * Checks a 1×2×1 box above the block to catch the crystal's hitbox.
     */
    private boolean hasCrystalAt(BlockPos basePos) {
        Box box = new Box(
                basePos.getX(),       basePos.getY() + 1.0, basePos.getZ(),
                basePos.getX() + 1.0, basePos.getY() + 3.0, basePos.getZ() + 1.0
        );
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (e instanceof EndCrystalEntity && e.isAlive()) return true;
        }
        return false;
    }

    /**
     * Switch to a hotbar slot containing {@code item}.
     * Blocked if we already switched this tick (prevents BadPackets).
     *
     * @return true if the item is now in the selected slot.
     */
    private boolean trySwap(Item item) {
        return InventoryUtil.swapToSlot(item);
    }

    /** Checks the MAINHAND stack specifically (not offhand). */
    private boolean isMainHandItem(Item item) {
        return mc.player.getMainHandStack().getItem() == item;
    }

    private boolean isHoldingTool() {
        Item hand = mc.player.getMainHandStack().getItem();
        return ToolItems.isPickaxe(hand)
                || hand instanceof AxeItem
                || hand instanceof ShovelItem;
    }

    private boolean isEntityBlockingPlacement(BlockPos pos) {
        Box blockBox = new Box(pos);
        List<Entity> entities = mc.world.getOtherEntities(
                mc.player, blockBox,
                e -> e.isAlive() && !e.isSpectator()
        );
        return !entities.isEmpty();
    }

    private int findHotbarSlot(Item item) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).getItem() == item)
                return i;
        }
        return -1;
    }

    private boolean isObsidianOrBedrock(BlockPos pos) {
        return BlockUtil.isBlock(pos, Blocks.OBSIDIAN)
                || BlockUtil.isBlock(pos, Blocks.BEDROCK);
    }

    private boolean shouldWaitForIFrames() {
        long now = System.currentTimeMillis();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player) continue;
            if (player.squaredDistanceTo(mc.player) > 36) continue;

            if (player == lastAttackedPlayer && (now - lastAttackTime) < 500) {
                continue;
            }

            if (player.hurtTime >= 2 && !player.isOnGround()
                    && player.getLastAttacker() == null) {
                return true;
            }
        }

        return false;
    }

    private EndCrystalEntity getCrystalOnBlock(BlockPos basePos) {
        if (mc.world == null) return null;
        Box box = new Box(
                basePos.getX(),       basePos.getY() + 0.5, basePos.getZ(),
                basePos.getX() + 1.0, basePos.getY() + 3.0, basePos.getZ() + 1.0
        );
        for (Entity e : mc.world.getOtherEntities(null, box)) {
            if (e instanceof EndCrystalEntity crystal && crystal.isAlive() && !crystal.isRemoved()) {
                return crystal;
            }
        }
        return null;
    }

    private boolean isLowerSideOfObsidian(BlockHitResult hit) {
        if (hit == null) return false;
        if (hit.getSide() != Direction.UP) return true;
        return (hit.getPos().y - hit.getBlockPos().getY()) < 0.85;
    }

    private float calculatePitchTo(Vec3d target) {
        if (mc.player == null) return 0f;
        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.x - eyePos.x;
        double dy = target.y - eyePos.y;
        double dz = target.z - eyePos.z;
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        return MathHelper.clamp((float) -Math.toDegrees(Math.atan2(dy, horizDist)), -89.9f, 89.9f);
    }

    private float calculateYawTo(Vec3d target) {
        if (mc.player == null) return 0f;
        Vec3d eyePos = mc.player.getEyePos();
        double dx = target.x - eyePos.x;
        double dz = target.z - eyePos.z;
        return MathHelper.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)));
    }

    private void resetState() {
        crystalling         = false;
        lastAttackTime      = 0;
        lastAttackedPlayer  = null;
        placeClock          = 0;
        pendingCrystalPos   = null;
        SilentRotation.clear();
    }
}
