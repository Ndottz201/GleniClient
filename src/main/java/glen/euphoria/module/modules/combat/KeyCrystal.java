package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.KeybindSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.keybinding.KeyUtils;
import glen.euphoria.utils.math.TimerUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public final class KeyCrystal extends Module {

    private final KeybindSetting crystalKeybind = new KeybindSetting("Crystal Key", GLFW.GLFW_MOUSE_BUTTON_4, false);
    private final BooleanSetting antiSuicide = new BooleanSetting("Anti Suicide", true);
    private final BooleanSetting antiWeakness = new BooleanSetting("Anti Weakness", true);
    private final BooleanSetting stopOnKill = new BooleanSetting("Stop On Kill", false);

    private final NumberSetting placeChance = new NumberSetting("Place Chance (%)", 0, 100, 100, 1);
    private final NumberSetting breakChance = new NumberSetting("Break Chance (%)", 0, 100, 100, 1);

    private final NumberSetting minBreakDelay = new NumberSetting("Min Break Delay (MS)", 10, 500, 50, 1);
    private final NumberSetting maxBreakDelay = new NumberSetting("Max Break Delay (MS)", 10, 500, 100, 1);
    private final NumberSetting minPlaceDelay = new NumberSetting("Min Place Delay (MS)", 10, 500, 30, 1);
    private final NumberSetting maxPlaceDelay = new NumberSetting("Max Place Delay (MS)", 10, 500, 80, 1);

    private final TimerUtil breakTimer = new TimerUtil();
    private final TimerUtil placeTimer = new TimerUtil();
    private final Random random = new Random();

    private boolean keyPressed = false;
    private boolean isActive = false;
    private int originalSlot = -1;

    private long currentBreakDelay;
    private long currentPlaceDelay;

    private enum Phase {
        IDLE,
        PLACE_OBSIDIAN,
        PLACE_CRYSTAL,
        BREAK_CRYSTAL
    }

    private Phase phase = Phase.IDLE;

    public KeyCrystal() {
        super("Key Crystal", "Automatically places and explodes crystals and obsidian for PvP", -1, Category.COMBAT);
        this.addSettings(
                crystalKeybind,
                placeChance, breakChance, stopOnKill,
                minBreakDelay, maxBreakDelay, minPlaceDelay, maxPlaceDelay,
                antiSuicide, antiWeakness
        );

        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(crystalKeybind));
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || mc.currentScreen != null) return;

        boolean currentKeyState = KeyUtils.isKeyPressed(crystalKeybind.getKeyCode());

        if (currentKeyState && !keyPressed) startCrystalPvP();
        else if (!currentKeyState && keyPressed) stopCrystalPvP();

        keyPressed = currentKeyState;

        if (isActive) processCrystalPvP();
    }

    private void startCrystalPvP() {
        if (isActive) return;
        isActive = true;
        originalSlot = mc.player.getInventory().getSelectedSlot();
        phase = Phase.IDLE;
        resetDelays();
    }

    private void stopCrystalPvP() {
        if (!isActive) return;
        if (originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        isActive = false;
        originalSlot = -1;
        phase = Phase.IDLE;
        resetDelays();
    }

    private void resetDelays() {
        breakTimer.reset();
        placeTimer.reset();

        int minBreak = minBreakDelay.getValueInt();
        int maxBreak = maxBreakDelay.getValueInt();
        int minPlace = minPlaceDelay.getValueInt();
        int maxPlace = maxPlaceDelay.getValueInt();

        if (minBreak >= maxBreak) {
            maxBreak = minBreak + 1;
            maxBreakDelay.setValue(maxBreak);
        }
        if (minPlace >= maxPlace) {
            maxPlace = minPlace + 1;
            maxPlaceDelay.setValue(maxPlace);
        }

        currentBreakDelay = minBreak + random.nextInt(Math.max(1, maxBreak - minBreak));
        currentPlaceDelay = minPlace + random.nextInt(Math.max(1, maxPlace - minPlace));
    }

    private void processCrystalPvP() {
        if (antiSuicide.getValue() && !mc.player.isOnGround()) return;
        if (stopOnKill.getValue() && isDeadPlayerNearby()) return;

        int randomInt = random.nextInt(100) + 1;

        // Determine next action if idle
        if (phase == Phase.IDLE) {
            if (mc.crosshairTarget instanceof EntityHitResult entityHit
                    && entityHit.getEntity() instanceof EndCrystalEntity crystal
                    && crystal.isAlive()) {
                phase = Phase.BREAK_CRYSTAL;
            } else if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
                BlockPos targetBlock = blockHit.getBlockPos();
                BlockPos placementPos = targetBlock.offset(blockHit.getSide());
                if (isObsidianOrBedrock(targetBlock) && isValidCrystalPosition(placementPos)) {
                    phase = Phase.PLACE_CRYSTAL;
                } else if (isValidPosition(placementPos)) {
                    phase = Phase.PLACE_OBSIDIAN;
                }
            }
        }

        // Execute exactly ONE action per tick according to phase state machine
        switch (phase) {
            case BREAK_CRYSTAL -> {
                if (mc.crosshairTarget instanceof EntityHitResult entityHit
                        && entityHit.getEntity() instanceof EndCrystalEntity crystal
                        && breakTimer.hasElapsedTime(currentBreakDelay)) {
                    if (randomInt <= breakChance.getValueInt()
                            && mc.player.getEntityPos().distanceTo(crystal.getEntityPos()) <= 4.5) {
                        if (antiWeakness.getValue() && mc.player.hasStatusEffect(StatusEffects.WEAKNESS)) {
                            if (!InventoryUtil.swapToWeapon(ItemTags.SWORDS)) return;
                        }

                        mc.interactionManager.attackEntity(mc.player, crystal);
                        mc.player.swingHand(Hand.MAIN_HAND);

                        breakTimer.reset();
                        currentBreakDelay = random.nextLong(minBreakDelay.getValueInt(), Math.max(minBreakDelay.getValueInt() + 1, maxBreakDelay.getValueInt()));
                    }
                }
                phase = Phase.IDLE;
            }

            case PLACE_OBSIDIAN -> {
                if (mc.crosshairTarget instanceof BlockHitResult blockHit && placeTimer.hasElapsedTime(currentPlaceDelay)) {
                    BlockPos targetBlock = blockHit.getBlockPos();
                    BlockPos placementPos = targetBlock.offset(blockHit.getSide());
                    BlockPos below = placementPos.down();

                    if (!mc.world.getBlockState(below).isAir() && isValidPosition(placementPos)) {
                        if (hasItemInHotbar(Items.OBSIDIAN)) {
                            if (!InventoryUtil.swapToSlot(Items.OBSIDIAN)) return;
                            if (mc.player.getMainHandStack().isOf(Items.OBSIDIAN)) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                                mc.player.swingHand(Hand.MAIN_HAND);

                                placeTimer.reset();
                                currentPlaceDelay = random.nextLong(minPlaceDelay.getValueInt(), Math.max(minPlaceDelay.getValueInt() + 1, maxPlaceDelay.getValueInt()));
                                phase = Phase.PLACE_CRYSTAL;
                                return;
                            }
                        }
                    }
                }
                phase = Phase.IDLE;
            }

            case PLACE_CRYSTAL -> {
                if (mc.crosshairTarget instanceof BlockHitResult blockHit && placeTimer.hasElapsedTime(currentPlaceDelay)) {
                    BlockPos targetBlock = blockHit.getBlockPos();
                    BlockPos placementPos = targetBlock.offset(blockHit.getSide());

                    if (isObsidianOrBedrock(targetBlock) && isValidCrystalPosition(placementPos)
                            && randomInt <= placeChance.getValueInt()) {
                        if (hasItemInHotbar(Items.END_CRYSTAL)) {
                            if (!InventoryUtil.swapToSlot(Items.END_CRYSTAL)) return;
                            if (mc.player.getMainHandStack().isOf(Items.END_CRYSTAL)) {
                                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                                mc.player.swingHand(Hand.MAIN_HAND);

                                placeTimer.reset();
                                currentPlaceDelay = random.nextLong(minPlaceDelay.getValueInt(), Math.max(minPlaceDelay.getValueInt() + 1, maxPlaceDelay.getValueInt()));
                                phase = Phase.BREAK_CRYSTAL;
                                return;
                            }
                        }
                    }
                }
                phase = Phase.IDLE;
            }

            case IDLE -> {}
        }
    }

    private boolean hasItemInHotbar(Item item) {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == item) return true;
        }
        return false;
    }

    private boolean isValidPosition(BlockPos pos) {
        if (mc.world == null) return false;
        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos)) > 4.5) return false;
        if (!mc.world.getBlockState(pos).isAir()) return false;

        BlockPos playerPos = mc.player.getBlockPos();
        return !pos.equals(playerPos) && !pos.equals(playerPos.up());
    }

    private boolean isObsidianOrBedrock(BlockPos pos) {
        if (mc.world == null) return false;
        var block = mc.world.getBlockState(pos).getBlock();
        return block == Blocks.OBSIDIAN || block == Blocks.BEDROCK;
    }

    private boolean isValidCrystalPosition(BlockPos pos) {
        if (mc.world == null) return false;
        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos)) > 4.5) return false;
        if (!mc.world.getBlockState(pos).isAir()) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;

        BlockPos playerPos = mc.player.getBlockPos();
        return !pos.equals(playerPos) && !pos.equals(playerPos.up()) &&
                !pos.up().equals(playerPos) && !pos.up().equals(playerPos.up());
    }

    private boolean isDeadPlayerNearby() {
        if (mc.world == null) return false;
        List<? extends Entity> players = mc.world.getPlayers();
        for (Entity e : players) {
            if (e == mc.player) continue;
            if (e.isRemoved() || ((LivingEntity) e).getHealth() <= 0.0f)
                if (e.squaredDistanceTo(mc.player) < 36)
                    return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        super.onDisable();
        stopCrystalPvP();
    }

    @Override
    public int getKey() {
        return -1;
    }
}