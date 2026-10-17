package glen.euphoria.module.modules.player;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.RangeSetting;
import glen.euphoria.utils.math.MathUtils;
import glen.euphoria.utils.math.TimerUtil;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import glen.euphoria.utils.rotation.RotationController;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;

public final class AutoExtinguish extends Module {
    public static final RangeSetting delayMS = new RangeSetting("Delay (MS)", 0, 1200, 250, 330, 0.5);
    public static final BooleanSetting pickUP = new BooleanSetting("Pick up after", false);
    public static final BooleanSetting rotateBack = new BooleanSetting("Rotate back", false);
    public static final BooleanSetting toPrevSlot = new BooleanSetting("Goto prev-slot", false);

    private final TimerUtil timer = new TimerUtil();
    private final TimerUtil delay = new TimerUtil();
    private boolean isExtinguishing = false;
    private float originalPitch;
    private int originalSlot;
    private long currentDelay;
    private State currentState = State.READY;

    public AutoExtinguish() {
        super("Auto Extinguish", "Automatically places water to extinguish you", -1, Category.PLAYER);
        this.addSettings(delayMS, pickUP, rotateBack, toPrevSlot);
    }

    @EventHandler
    private void onTickEvent(TickEvent event) {
        if (isNull())
            return;

        if (mc.player.isOnFire()) {
            if (!isExtinguishing) {
                startExtinguishing();
            } else {
                handleExtinguishing();
            }
        } else if (isExtinguishing) {
            finishExtinguishing();
        }
    }

    private void startExtinguishing() {
        isExtinguishing = true;
        originalPitch = mc.player.getPitch();
        originalSlot = mc.player.getInventory().getSelectedSlot();

        long min = (long) delayMS.getMinValue();
        long max = (long) delayMS.getMaxValue();

        currentDelay = (long) MathUtils.randomDoubleBetween(min, max);

        currentState = State.SWITCHING;
        timer.reset();
        delay.reset();
    }

    private void handleExtinguishing() {
        if (timer.hasElapsedTime(currentDelay)) {
            switch (currentState) {
                case SWITCHING:
                    if (!InventoryUtil.swapToSlot(Items.WATER_BUCKET)) return;
                    currentState = State.AIMING;
                    timer.reset();
                    break;

                case AIMING:
                    RotationController.request(mc.player.getYaw(), 89.9f, 10);
                    currentState = State.PLACING;
                    timer.reset();
                    break;

                case PLACING:
                    if (!InventoryUtil.swapToSlot(Items.WATER_BUCKET)) return;
                    if (mc.player.getMainHandStack().isOf(Items.WATER_BUCKET)) {
                        ((MinecraftClientAccessor) mc).invokeDoItemUse();
                        currentState = pickUP.getValue() ? State.PICKING_UP : State.RETURNING;
                        timer.reset();
                    }
                    break;

                case PICKING_UP:
                    if (delay.hasElapsedTime(100)) {
                        ((MinecraftClientAccessor) mc).invokeDoItemUse();
                        currentState = State.RETURNING;
                        timer.reset();
                    }
                    break;

                case RETURNING:
                    if (toPrevSlot.getValue() && mc.player != null) {
                        if (!SlotGuard.tryClaim()) return;
                        mc.player.getInventory().setSelectedSlot(originalSlot);
                        InventoryUtil.syncSelectedSlot();
                    }
                    if (rotateBack.getValue()) {
                        RotationController.request(mc.player.getYaw(), originalPitch, 10);
                    }
                    isExtinguishing = false;
                    currentState = State.READY;
                    this.toggle();
                    break;
            }
        }
    }

    private void finishExtinguishing() {
        if (toPrevSlot.getValue() && mc.player != null && mc.player.getInventory().getSelectedSlot() != originalSlot) {
            if (!SlotGuard.tryClaim()) return;
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        isExtinguishing = false;
        if (rotateBack.getValue() && mc.player != null) {
            RotationController.request(mc.player.getYaw(), originalPitch, 10);
        }
        currentState = State.READY;
    }

    @Override
    public void onEnable() {
        delay.reset();
        timer.reset();
        isExtinguishing = false;
        currentState = State.READY;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        delay.reset();
        timer.reset();
        if (isExtinguishing && toPrevSlot.getValue() && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        isExtinguishing = false;
        currentState = State.READY;
        super.onDisable();
    }

    private enum State {
        READY,
        SWITCHING,
        AIMING,
        PLACING,
        PICKING_UP,
        RETURNING
    }
}