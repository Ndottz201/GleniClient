package glen.euphoria.module.modules.player;

import glen.euphoria.event.impl.player.TickEvent;
import glen.euphoria.mixin.MinecraftClientAccessor;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.mc.InventoryUtil;
import glen.euphoria.utils.mc.SlotGuard;
import glen.euphoria.utils.rotation.RotationController;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class CoverUp extends Module {

    private final NumberSetting placeDelay = new NumberSetting("Place Delay", 0, 100, 20, 5);

    private boolean isPlacing = false;
    private int placementStep = 0;
    private int savedSlot = -1;
    private float savedYaw = 0;
    private float savedPitch = 0;
    private long lastPlaceTime = 0;
    private boolean hasRotated = false;

    public CoverUp() {
        super("Cover Up", "Places cobwebs around your feet", -1, Category.PLAYER);
        this.addSettings(placeDelay);
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            this.setEnabled(false);
            return;
        }

        int cobwebSlot = getCobwebSlot();
        if (cobwebSlot == -1) {
            this.setEnabled(false);
            return;
        }

        savedSlot = mc.player.getInventory().getSelectedSlot();
        savedYaw = mc.player.getYaw();
        savedPitch = mc.player.getPitch();

        isPlacing = true;
        placementStep = 0;
        lastPlaceTime = System.currentTimeMillis();
        hasRotated = false;
    }

    @Override
    public void onDisable() {
        if (!isNull() && savedSlot != -1 && mc.player.getInventory().getSelectedSlot() != savedSlot) {
            if (SlotGuard.tryClaim()) {
                mc.player.getInventory().setSelectedSlot(savedSlot);
                InventoryUtil.syncSelectedSlot();
            }
        }

        isPlacing = false;
        placementStep = 0;
        savedSlot = -1;
        hasRotated = false;
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || !isPlacing)
            return;

        RotationController.request(savedYaw, 89.5f, 10);

        if (!hasRotated) {
            hasRotated = true;
            return;
        }

        if (System.currentTimeMillis() - lastPlaceTime < placeDelay.getValue()) {
            return;
        }

        if (placementStep < 2) {
            placeCobweb();
            placementStep++;
            lastPlaceTime = System.currentTimeMillis();
        } else {
            this.setEnabled(false);
        }
    }

    private void placeCobweb() {
        if (!InventoryUtil.swapToSlot(Items.COBWEB)) {
            return;
        }

        if (mc.player.getMainHandStack().isOf(Items.COBWEB)) {
            ((MinecraftClientAccessor) mc).invokeDoItemUse();
        }
    }

    private int getCobwebSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == Items.COBWEB) {
                return i;
            }
        }
        return -1;
    }
}
