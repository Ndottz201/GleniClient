package glen.euphoria.module.modules.combat;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MouseHandlerAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

public final class AutoInventoryTotem extends Module {

    private static volatile boolean inputLocked = false;
    private static volatile long lastUnlockTime = 0L;
    private static AutoInventoryTotem instance;

    public static boolean isInputLocked() {
        return inputLocked;
    }

    public static long getLastUnlockTime() {
        return lastUnlockTime;
    }

    private final NumberSetting totemSlot = new NumberSetting("Totem Slot", 1, 9, 6, 1);
    private final NumberSetting reactionDelayMin = new NumberSetting("Reaction Delay Min (ms)", 0, 500, 80, 1);
    private final NumberSetting reactionDelayMax = new NumberSetting("Reaction Delay Max (ms)", 0, 500, 150, 1);
    private final NumberSetting swapDelayMin = new NumberSetting("Swap Delay Min (ms)", 0, 500, 40, 1);
    private final NumberSetting swapDelayMax = new NumberSetting("Swap Delay Max (ms)", 0, 500, 80, 1);
    private final NumberSetting closeDelayMin = new NumberSetting("Close Delay Min (ms)", 0, 500, 30, 1);
    private final NumberSetting closeDelayMax = new NumberSetting("Close Delay Max (ms)", 0, 500, 60, 1);
    private final NumberSetting cursorSpeedMin = new NumberSetting("Cursor Speed Min", 1, 40, 3, 1);
    private final NumberSetting cursorSpeedMax = new NumberSetting("Cursor Speed Max", 1, 40, 5, 1);

    private enum State {
        IDLE,
        REACTION_WAIT,
        OPEN_INV,
        WAIT_INV_OPEN,
        MOVE_TO_SLOT_1,
        SWAP_1,
        POST_SWAP_1,
        MOVE_TO_SLOT_2,
        SWAP_2,
        POST_SWAP_2,
        PRE_CLOSE,
        CLOSING
    }

    private State state = State.IDLE;
    private long nextActionAt = 0L;

    private boolean lastTickHadOffhandTotem = false;
    private boolean lastTickHadSlotTotem = false;

    // Virtual cursor
    private double curX, curY;
    private double savedMouseX, savedMouseY;

    // Animation
    private double animStartX, animStartY;
    private double animEndX, animEndY;
    private double ctrl1X, ctrl1Y, ctrl2X, ctrl2Y;
    private long animStartTime, animDurationMs;
    private boolean animating = false;

    // What we need to do
    private boolean needOffhand, needSlot;
    private int sourceSlot1 = -1, sourceSlot2 = -1;
    private boolean slot1IsForOffhand;
    private boolean slot2IsForOffhand;

    private boolean weOpenedInventory = false;
    private int previousSelectedSlot = -1;

    private int retryCount = 0;
    private static final int MAX_RETRIES = 3;

    private boolean hookRegistered = false;

    private static final int GUI_W = 176;
    private static final int GUI_H = 166;

    // ─── PANIC / INTERRUPT SYSTEM ───
    private volatile boolean panicInterrupt = false;

    // FIX 1: Stall threshold reduced from 3 → 1 tick (50ms vs 150ms)
    // Under crystal spam, even 2 ticks of stalling can be fatal.
    private int stalledTicks = 0;
    private static final int STALL_THRESHOLD = 1;

    private boolean inSwapSequence = false;

    // FIX 2: Track consecutive pops to skip animation under extreme spam pressure
    private int panicPopCount = 0;
    private static final int SKIP_ANIM_AFTER_POPS = 2;

    public AutoInventoryTotem() {
        super("AutoInventoryTotem", "Auto totems offhand and slot on pop", -1, Category.COMBAT);
        instance = this;
        addSettings(totemSlot,
                reactionDelayMin, reactionDelayMax,
                swapDelayMin, swapDelayMax,
                closeDelayMin, closeDelayMax,
                cursorSpeedMin, cursorSpeedMax);
    }

    @Override
    public void onEnable() {
        state = State.IDLE;
        nextActionAt = 0L;
        weOpenedInventory = false;
        inputLocked = false;
        animating = false;
        retryCount = 0;
        previousSelectedSlot = -1;
        panicInterrupt = false;
        stalledTicks = 0;
        inSwapSequence = false;
        panicPopCount = 0;
        lastUnlockTime = 0L;

        if (mc.player != null) {
            lastTickHadOffhandTotem = isTotem(mc.player.getOffHandStack());
            lastTickHadSlotTotem = isTotem(mc.player.getInventory().getStack(getHotbarIndex()));
        }

        if (!hookRegistered) {
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
                if (screen instanceof InventoryScreen) {
                    net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, delta) -> {
                        if (instance != null && instance.isEnabled()) {
                            instance.tickAnimation();
                        }
                    });
                }
            });
            hookRegistered = true;
        }

        super.onEnable();
    }

    @Override
    public void onDisable() {
        fullReset();
        super.onDisable();
    }

    // ─── Helpers ───

    private int getHotbarIndex() {
        return totemSlot.getValueInt() - 1;
    }

    private int getScreenSlotForHotbar() {
        return 36 + getHotbarIndex();
    }

    private boolean isTotem(ItemStack stack) {
        return stack != null && stack.getItem() == Items.TOTEM_OF_UNDYING;
    }

    private long nowMs() {
        return System.nanoTime() / 1_000_000L;
    }

    private int randRange(int min, int max) {
        if (min >= max) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }

    private void scheduleDelay(int minMs, int maxMs) {
        nextActionAt = nowMs() + Math.max(0, randRange(minMs, maxMs));
    }

    private void scheduleImmediate() {
        nextActionAt = 0L;
    }

    private boolean offhandHasTotem() {
        return mc.player != null && isTotem(mc.player.getOffHandStack());
    }

    private boolean slotHasTotem() {
        return mc.player != null && isTotem(mc.player.getInventory().getStack(getHotbarIndex()));
    }

    private boolean allGood() {
        return offhandHasTotem() && slotHasTotem();
    }

    private int countAvailableTotems() {
        if (mc.player == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (i == getHotbarIndex()) continue;
            if (isTotem(mc.player.getInventory().getStack(i))) count++;
        }
        return count;
    }

    private int findTotemInScreen(int exclude) {
        if (!(mc.currentScreen instanceof InventoryScreen inv)) return -1;

        int best = -1;
        double bestDist = Double.MAX_VALUE;
        int skipSlot = getScreenSlotForHotbar();

        for (int i = 9; i <= 44; i++) {
            if (i == exclude || i == skipSlot || i == 45) continue;
            ItemStack stack = inv.getScreenHandler().getSlot(i).getStack();
            if (!isTotem(stack)) continue;

            double[] pos = getSlotCenter(i);
            double dx = pos[0] - curX;
            double dy = pos[1] - curY;
            double dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        return best;
    }

    // FIX 3: findTotemInInventory — works WITHOUT inventory screen open
    // Used by panic path to pre-locate a totem slot before screen is open
    private int findTotemInInventoryRaw(int exclude) {
        if (mc.player == null) return -1;
        int skipIdx = getHotbarIndex();
        int skipScreenSlot = getScreenSlotForHotbar(); // = 36 + hotbarIdx

        // Search main inventory (screen slots 9-35 = inv idx 9-35) and hotbar (screen slots 36-44 = inv idx 0-8)
        // Screen slot mapping: screen[9..35] = player inv[9..35], screen[36..44] = player inv[0..8]
        for (int screenSlot = 9; screenSlot <= 44; screenSlot++) {
            if (screenSlot == exclude || screenSlot == skipScreenSlot || screenSlot == 45) continue;
            int invIdx;
            if (screenSlot >= 36) {
                invIdx = screenSlot - 36; // hotbar
            } else {
                invIdx = screenSlot; // main inv
            }
            if (invIdx == skipIdx) continue;
            if (isTotem(mc.player.getInventory().getStack(invIdx))) return screenSlot;
        }
        return -1;
    }

    private boolean lastTotemIsInHotbarSlot() {
        if (mc.player == null) return false;
        int hotbarIdx = getHotbarIndex();
        if (!isTotem(mc.player.getInventory().getStack(hotbarIdx))) return false;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            if (i == hotbarIdx) continue;
            if (isTotem(mc.player.getInventory().getStack(i))) count++;
        }
        if (isTotem(mc.player.getOffHandStack())) count++;
        return count == 0;
    }

    private double[] getSlotCenter(int screenSlot) {
        int guiLeft = (mc.getWindow().getScaledWidth() - GUI_W) / 2;
        int guiTop = (mc.getWindow().getScaledHeight() - GUI_H) / 2;

        int x, y;
        if (screenSlot >= 9 && screenSlot <= 35) {
            int idx = screenSlot - 9;
            x = 8 + (idx % 9) * 18;
            y = 84 + (idx / 9) * 18;
        } else if (screenSlot >= 36 && screenSlot <= 44) {
            x = 8 + (screenSlot - 36) * 18;
            y = 142;
        } else if (screenSlot == 45) {
            x = 77;
            y = 62;
        } else {
            x = GUI_W / 2;
            y = GUI_H / 2;
        }

        return new double[]{guiLeft + x + 8, guiTop + y + 8};
    }

    // ─── Input Lock ───

    private void lockInput() {
        if (mc.mouse == null || mc.getWindow() == null) return;

        savedMouseX = mc.mouse.getX();
        savedMouseY = mc.mouse.getY();

        inputLocked = true;
        weOpenedInventory = true;

        curX = mc.getWindow().getScaledWidth() / 2.0;
        curY = mc.getWindow().getScaledHeight() / 2.0;
        pushCursorToScreen();
    }

    private void unlockInput() {
        lastUnlockTime = System.currentTimeMillis();
        inputLocked = false;
        weOpenedInventory = false;
        animating = false;

        if (mc.mouse != null && mc.getWindow() != null) {
            ((MouseHandlerAccessor) mc.mouse).setCursorDeltaX(0.0);
            ((MouseHandlerAccessor) mc.mouse).setCursorDeltaY(0.0);
        }
    }

    private void pushCursorToScreen() {
        if (mc.mouse == null || mc.getWindow() == null) return;

        double scaleX = (double) mc.getWindow().getWidth() / mc.getWindow().getScaledWidth();
        double scaleY = (double) mc.getWindow().getHeight() / mc.getWindow().getScaledHeight();

        double pixelX = Math.max(0, Math.min(curX * scaleX, mc.getWindow().getWidth()));
        double pixelY = Math.max(0, Math.min(curY * scaleY, mc.getWindow().getHeight()));

        ((MouseHandlerAccessor) mc.mouse).setX(pixelX);
        ((MouseHandlerAccessor) mc.mouse).setY(pixelY);
        GLFW.glfwSetCursorPos(mc.getWindow().getHandle(), pixelX, pixelY);

        if (mc.currentScreen != null) {
            mc.currentScreen.mouseMoved(curX, curY);
        }
    }

    // ─── Animation ───

    // FIX 4: skipAnimation flag — under heavy panic pressure, teleport cursor instantly
    private boolean skipAnimation = false;

    private void startCursorAnimation(int screenSlot) {
        double[] target = getSlotCenter(screenSlot);

        // FIX 4: Skip bezier animation entirely when under heavy spam pressure
        if (skipAnimation) {
            curX = target[0];
            curY = target[1];
            animating = false;
            pushCursorToScreen();
            return;
        }

        animStartX = curX;
        animStartY = curY;

        animEndX = target[0] + ThreadLocalRandom.current().nextDouble(-4.0, 4.0);
        animEndY = target[1] + ThreadLocalRandom.current().nextDouble(-4.0, 4.0);

        double dx = animEndX - animStartX;
        double dy = animEndY - animStartY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        double perpX = 0, perpY = 0;
        if (dist > 0.01) {
            perpX = -dy / dist;
            perpY = dx / dist;
        }

        double curveAmount = Math.min(dist * 0.15, 25.0);
        double curveDir = ThreadLocalRandom.current().nextBoolean() ? 1.0 : -1.0;

        double t1 = ThreadLocalRandom.current().nextDouble(0.28, 0.38);
        double t2 = ThreadLocalRandom.current().nextDouble(0.62, 0.72);

        ctrl1X = animStartX + dx * t1 + curveDir * perpX * curveAmount;
        ctrl1Y = animStartY + dy * t1 + curveDir * perpY * curveAmount;
        ctrl2X = animStartX + dx * t2 + curveDir * perpX * curveAmount * 0.4;
        ctrl2Y = animStartY + dy * t2 + curveDir * perpY * curveAmount * 0.4;

        int speedMin = cursorSpeedMin.getValueInt();
        int speedMax = cursorSpeedMax.getValueInt();
        double baseDuration = randRange(speedMin * 8, speedMax * 8);
        double distFactor = Math.max(0.5, Math.min(1.5, dist / 100.0));
        animDurationMs = Math.max(30, (long) (baseDuration * distFactor));

        animStartTime = System.nanoTime();
        animating = true;
    }

    private void tickAnimation() {
        if (!weOpenedInventory || !inputLocked) return;
        if (!(mc.currentScreen instanceof InventoryScreen)) return;
        if (!animating) return;

        long elapsed = (System.nanoTime() - animStartTime) / 1_000_000L;
        double t = Math.min(1.0, (double) elapsed / animDurationMs);

        double s = t * t * (3.0 - 2.0 * t);

        double inv = 1.0 - s;
        curX = inv * inv * inv * animStartX
                + 3.0 * inv * inv * s * ctrl1X
                + 3.0 * inv * s * s * ctrl2X
                + s * s * s * animEndX;
        curY = inv * inv * inv * animStartY
                + 3.0 * inv * inv * s * ctrl1Y
                + 3.0 * inv * s * s * ctrl2Y
                + s * s * s * animEndY;

        if (t >= 1.0) {
            curX = animEndX;
            curY = animEndY;
            animating = false;
        }

        pushCursorToScreen();
    }

    // ─── Reset ───

    private void fullReset() {
        if (mc.currentScreen instanceof InventoryScreen && weOpenedInventory) {
            mc.currentScreen.close();
        }
        state = State.IDLE;
        unlockInput();
        retryCount = 0;
        inSwapSequence = false;
        panicInterrupt = false;
        stalledTicks = 0;
        panicPopCount = 0;
        skipAnimation = false;
        restoreHotbarSlot();
        previousSelectedSlot = -1;
        if (mc.player != null) {
            lastTickHadOffhandTotem = offhandHasTotem();
            lastTickHadSlotTotem = slotHasTotem();
        }
    }

    private void restoreHotbarSlot() {
        if (mc.player != null && previousSelectedSlot >= 0 && previousSelectedSlot < 9) {
            if (mc.player.getInventory().getSelectedSlot() != previousSelectedSlot) {
                if (SlotGuard.tryClaim()) {
                    mc.player.getInventory().setSelectedSlot(previousSelectedSlot);
                    InventoryUtil.syncSelectedSlot();
                }
            }
        }
    }

    // ─── PANIC INTERRUPT ───
    private void triggerPanic() {
        panicPopCount++;

        // FIX 4: After N consecutive pops, skip cursor animation to be faster
        skipAnimation = panicPopCount >= SKIP_ANIM_AFTER_POPS;

        // Close any open inventory without delay
        if (mc.currentScreen instanceof InventoryScreen && weOpenedInventory) {
            mc.currentScreen.close();
        }
        unlockInput();
        retryCount = 0;
        inSwapSequence = false;

        if (previousSelectedSlot < 0 && mc.player != null) {
            previousSelectedSlot = mc.player.getInventory().getSelectedSlot();
        }

        // FIX 5: Skip REACTION_WAIT entirely during panic — go straight to OPEN_INV
        // The reaction delay is unnecessary when a pop is confirmed and time is critical.
        state = State.OPEN_INV;
        scheduleImmediate();
        panicInterrupt = false;
    }

    // ─── Main Tick ───

    @EventHandler
    private void onTick(TickEvent event) {
        if (mc.player == null || mc.world == null) return;

        if (mc.player.isDead()) {
            if (state != State.IDLE) fullReset();
            lastTickHadOffhandTotem = false;
            lastTickHadSlotTotem = false;
            return;
        }

        long now = nowMs();

        boolean currentOH = offhandHasTotem();
        boolean currentSlot = slotHasTotem();

        boolean offhandPopped = lastTickHadOffhandTotem && !currentOH;
        boolean slotPopped = lastTickHadSlotTotem && !currentSlot;
        boolean popDetected = offhandPopped || slotPopped;

        boolean hasTotemsToFill = (countAvailableTotems() > 0 || lastTotemIsInHotbarSlot());
        boolean missingTotems = (!currentOH || !currentSlot) && hasTotemsToFill;

        // ─── PANIC: pop detected while already in a swap sequence ───
        if (popDetected && inSwapSequence) {
            panicInterrupt = true;
        }

        // ─── ALSO panic if we're mid-swap and totems are missing even without a clean transition ───
        // This catches the case where a double-pop happens so fast that lastTick was already false,
        // so offhandPopped never fires, but we're still missing totems while supposedly swapping.
        if (!popDetected && inSwapSequence && missingTotems) {
            panicInterrupt = true;
        }

        // Update snapshots AFTER detection.
        // IMPORTANT: Only update the snapshot if the current value is true (totem present).
        // If current is false, keep lastTick as-is so we don't lose the "had totem" memory
        // across multiple ticks of server lag. This prevents missed pop detection when the
        // server is slow to confirm the swap and we briefly see false → false → true.
        if (currentOH) lastTickHadOffhandTotem = true;
        else if (offhandPopped) lastTickHadOffhandTotem = false; // confirmed gone, update

        if (currentSlot) lastTickHadSlotTotem = true;
        else if (slotPopped) lastTickHadSlotTotem = false; // confirmed gone, update

        // Handle panic interrupt
        if (panicInterrupt) {
            triggerPanic();
            // Fall through to process OPEN_INV this same tick
        }

        // Stall detection
        if (state == State.IDLE && missingTotems) {
            stalledTicks++;
            if (stalledTicks >= STALL_THRESHOLD) {
                stalledTicks = 0;
                state = State.REACTION_WAIT;
                if (previousSelectedSlot < 0 && mc.player != null) {
                    previousSelectedSlot = mc.player.getInventory().getSelectedSlot();
                }
                scheduleImmediate();
            }
        } else {
            stalledTicks = 0;
        }

        if (state == State.IDLE) {
            if (popDetected && hasTotemsToFill) {
                state = State.REACTION_WAIT;
                if (mc.player != null) previousSelectedSlot = mc.player.getInventory().getSelectedSlot();
                scheduleDelay(reactionDelayMin.getValueInt(), reactionDelayMax.getValueInt());
            } else if (missingTotems && !popDetected) {
                state = State.REACTION_WAIT;
                if (mc.player != null) previousSelectedSlot = mc.player.getInventory().getSelectedSlot();
                scheduleDelay(reactionDelayMin.getValueInt(), reactionDelayMax.getValueInt());
            }
        }

        // Time gate — skip only if not in panic
        if (now < nextActionAt && !panicInterrupt) return;

        // Safety: inventory state but screen closed unexpectedly
        if (isInventoryState() && !(mc.currentScreen instanceof InventoryScreen)) {
            // FIX 7: Don't fullReset here — instead try to reopen inventory.
            // fullReset was causing us to go IDLE and miss filling the totem.
            if (inSwapSequence) {
                state = State.OPEN_INV;
                scheduleImmediate();
                return;
            }
            fullReset();
            return;
        }

        // FIX 8: During panic, don't block on animation — the panic path already cleared animating via unlockInput()
        // But if somehow animating is still true during a non-panic tick, wait for it.
        if (animating && !panicInterrupt) {
            return;
        }
        // If we somehow get here with animating=true after a panic was handled, force-clear it.
        if (animating) {
            animating = false;
        }

        // When MOVE states finish animating, advance
        if (state == State.MOVE_TO_SLOT_1) {
            state = State.SWAP_1;
        } else if (state == State.MOVE_TO_SLOT_2) {
            state = State.SWAP_2;
        }

        switch (state) {
            case IDLE -> {}
            case REACTION_WAIT -> doReactionWait();
            case OPEN_INV -> doOpenInv();
            case WAIT_INV_OPEN -> doWaitInvOpen();
            case SWAP_1 -> doSwap1();
            case POST_SWAP_1 -> doPostSwap1();
            case SWAP_2 -> doSwap2();
            case POST_SWAP_2 -> doPostSwap2();
            case PRE_CLOSE -> {
                state = State.CLOSING;
                scheduleDelay(closeDelayMin.getValueInt(), closeDelayMax.getValueInt());
            }
            case CLOSING -> doClose();
            default -> {}
        }
    }

    private boolean isInventoryState() {
        return state.ordinal() >= State.WAIT_INV_OPEN.ordinal()
                && state.ordinal() <= State.PRE_CLOSE.ordinal();
    }

    // ─── State handlers ───

    private void doReactionWait() {
        int targetSlot = getHotbarIndex();
        if (mc.player.getInventory().getSelectedSlot() != targetSlot) {
            if (!SlotGuard.tryClaim()) return;
            mc.player.getInventory().setSelectedSlot(targetSlot);
            InventoryUtil.syncSelectedSlot();
        }
        state = State.OPEN_INV;
        // FIX 9: Reduced slot-switch delay from 30-60ms to near-zero.
        // The hotbar switch is instant server-side; there's no reason to wait here.
        scheduleDelay(5, 15);
    }

    private void doOpenInv() {
        if (mc.currentScreen != null) {
            if (mc.currentScreen instanceof InventoryScreen) {
                state = State.WAIT_INV_OPEN;
                scheduleImmediate();
                return;
            }
            mc.currentScreen.close();
            scheduleDelay(50, 80);
            return;
        }

        if (allGood()) {
            state = State.IDLE;
            restoreHotbarSlot();
            previousSelectedSlot = -1;
            inSwapSequence = false;
            // FIX 10: Reset panic pop count and skip-anim flag when all good
            panicPopCount = 0;
            skipAnimation = false;
            return;
        }

        mc.setScreen(new InventoryScreen(mc.player));
        inSwapSequence = true;
        state = State.WAIT_INV_OPEN;
        scheduleDelay(20, 40);
    }

    private void doWaitInvOpen() {
        if (!(mc.currentScreen instanceof InventoryScreen inv)) {
            state = State.OPEN_INV;
            scheduleDelay(30, 50);
            return;
        }

        lockInput();

        needOffhand = !offhandHasTotem();
        needSlot = !slotHasTotem();

        if (!needOffhand && !needSlot) {
            state = State.PRE_CLOSE;
            scheduleImmediate();
            return;
        }

        if (lastTotemIsInHotbarSlot() && needOffhand) {
            sourceSlot1 = getScreenSlotForHotbar();
            slot1IsForOffhand = true;
            startCursorAnimation(sourceSlot1);
            state = animating ? State.MOVE_TO_SLOT_1 : State.SWAP_1;
            return;
        }

        sourceSlot1 = findTotemInScreen(-1);
        if (sourceSlot1 == -1) {
            state = State.PRE_CLOSE;
            scheduleImmediate();
            return;
        }

        slot1IsForOffhand = needOffhand;
        startCursorAnimation(sourceSlot1);
        // FIX 11: If animation was skipped (skipAnimation=true), go straight to SWAP_1
        state = animating ? State.MOVE_TO_SLOT_1 : State.SWAP_1;
    }

    private void doSwap1() {
        if (!(mc.currentScreen instanceof InventoryScreen inv)) {
            fullReset();
            return;
        }

        if (slot1IsForOffhand) {
            mc.interactionManager.clickSlot(
                    inv.getScreenHandler().syncId,
                    sourceSlot1, 40, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(
                    inv.getScreenHandler().syncId,
                    sourceSlot1, getHotbarIndex(), SlotActionType.SWAP, mc.player);
        }

        state = State.POST_SWAP_1;
        scheduleDelay(swapDelayMin.getValueInt(), swapDelayMax.getValueInt());
    }

    private void doPostSwap1() {
        boolean ok;
        if (slot1IsForOffhand) {
            ok = offhandHasTotem();
        } else {
            ok = slotHasTotem();
        }

        if (!ok) {
            retryCount++;
            if (retryCount > MAX_RETRIES) {
                retryCount = 0;
            } else {
                state = State.SWAP_1;
                scheduleDelay(20, 40);
                return;
            }
        }

        retryCount = 0;

        boolean stillNeedOH = !offhandHasTotem();
        boolean stillNeedSlot = !slotHasTotem();

        if (!stillNeedOH && !stillNeedSlot) {
            state = State.PRE_CLOSE;
            scheduleImmediate();
            return;
        }

        if (slot1IsForOffhand && sourceSlot1 == getScreenSlotForHotbar() && lastTotemIsInHotbarSlot()) {
            state = State.PRE_CLOSE;
            scheduleImmediate();
            return;
        }

        sourceSlot2 = findTotemInScreen(sourceSlot1);
        if (sourceSlot2 == -1) {
            state = State.PRE_CLOSE;
            scheduleImmediate();
            return;
        }

        slot2IsForOffhand = stillNeedOH;
        startCursorAnimation(sourceSlot2);
        // FIX 11 continued: same animation-skip check for slot 2
        state = animating ? State.MOVE_TO_SLOT_2 : State.SWAP_2;
    }

    private void doSwap2() {
        if (!(mc.currentScreen instanceof InventoryScreen inv)) {
            fullReset();
            return;
        }

        if (slot2IsForOffhand) {
            mc.interactionManager.clickSlot(
                    inv.getScreenHandler().syncId,
                    sourceSlot2, 40, SlotActionType.SWAP, mc.player);
        } else {
            mc.interactionManager.clickSlot(
                    inv.getScreenHandler().syncId,
                    sourceSlot2, getHotbarIndex(), SlotActionType.SWAP, mc.player);
        }

        state = State.POST_SWAP_2;
        scheduleDelay(swapDelayMin.getValueInt(), swapDelayMax.getValueInt());
    }

    private void doPostSwap2() {
        boolean ok;
        if (slot2IsForOffhand) {
            ok = offhandHasTotem();
        } else {
            ok = slotHasTotem();
        }

        if (!ok) {
            retryCount++;
            if (retryCount > MAX_RETRIES) {
                retryCount = 0;
            } else {
                state = State.SWAP_2;
                scheduleDelay(20, 40);
                return;
            }
        }

        retryCount = 0;
        state = State.PRE_CLOSE;
        scheduleImmediate();
    }

    private void doClose() {
        if (mc.currentScreen != null) {
            mc.currentScreen.close();
        }
        unlockInput();
        restoreHotbarSlot();
        previousSelectedSlot = -1;
        inSwapSequence = false;

        // Don't snapshot here — the server may not have confirmed the swap yet.
        // The next onTick call will read the real values and the sticky-snapshot logic handles it.
        // Snapshotting false right after close is what caused missed pop detection in fast sequences.

        // After close, check if we're still missing totems (e.g. double-popped during swap).
        // If so, immediately re-trigger rather than waiting for stall detection next tick.
        boolean stillMissing = (!offhandHasTotem() || !slotHasTotem()) &&
                (countAvailableTotems() > 0 || lastTotemIsInHotbarSlot());
        if (stillMissing) {
            previousSelectedSlot = mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
            state = State.OPEN_INV;
            scheduleImmediate();
            return;
        }

        retryCount = 0;
        panicPopCount = 0;
        skipAnimation = false;
        state = State.IDLE;
    }
}