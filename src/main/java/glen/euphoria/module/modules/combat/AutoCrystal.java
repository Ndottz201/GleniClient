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
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

/**
 * AutoCrystal — sequenced 1-action-per-tick crystal handling.
 */
public final class AutoCrystal extends Module {

    private final KeybindSetting crystalKey = new KeybindSetting("Crystal Key", GLFW.GLFW_MOUSE_BUTTON_4, false);
    private final NumberSetting delay = new NumberSetting("Delay (MS)", 1, 200, 50, 1);
    private final BooleanSetting antiSuicide = new BooleanSetting("Anti Suicide", true);
    private final BooleanSetting autoSwitch = new BooleanSetting("Auto Switch", true);
    private final BooleanSetting switchBack = new BooleanSetting("Switch Back", true);
    private final BooleanSetting antiWeakness = new BooleanSetting("Anti Weakness", true);

    private final TimerUtil timer = new TimerUtil();
    private int originalSlot = -1;

    public AutoCrystal() {
        super("Auto Crystal", "Hold key to spam crystals", -1, Category.COMBAT);
        this.addSettings(crystalKey, delay, antiSuicide, autoSwitch, switchBack, antiWeakness);
        this.getSettings().removeIf(setting -> setting instanceof KeybindSetting && !setting.equals(crystalKey));
    }

    @EventHandler
    private void onTick(TickEvent event) {
        if (isNull() || mc.currentScreen != null) return;
        if (!KeyUtils.isKeyPressed(crystalKey.getKeyCode())) return;

        if (timer.hasElapsedTime(delay.getValueInt())) {
            processCrystal();
            timer.reset();
        }
    }

    private void processCrystal() {
        if (antiSuicide.getValue() && !mc.player.isOnGround()) return;

        // Phase 1: Attack existing crystal (max 1 action this tick)
        if (mc.crosshairTarget instanceof EntityHitResult entityHit) {
            if (entityHit.getEntity() instanceof EndCrystalEntity crystal) {
                if (!crystal.isRemoved() && crystal.isAlive() && mc.world.getEntityById(crystal.getId()) != null) {
                    if (mc.player.getEntityPos().distanceTo(crystal.getEntityPos()) <= 4.5) {
                        if (antiWeakness.getValue() && mc.player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.WEAKNESS)) {
                            if (!InventoryUtil.swapToWeapon(ItemTags.SWORDS)) return;
                        }
                        mc.interactionManager.attackEntity(mc.player, crystal);
                        mc.player.swingHand(Hand.MAIN_HAND);
                        return;
                    }
                }
            }
        }

        // Phase 2: Place crystal on valid block (max 1 action this tick)
        if (mc.crosshairTarget instanceof BlockHitResult blockHit) {
            BlockPos targetBlock = blockHit.getBlockPos();
            BlockPos placementPos = targetBlock.offset(blockHit.getSide());

            if (isObsidianOrBedrock(targetBlock) && isValidCrystalPosition(placementPos)) {
                if (autoSwitch.getValue() && hasItemInHotbar()) {
                    if (!InventoryUtil.swapToSlot(Items.END_CRYSTAL)) return;
                }

                if (mc.player.getMainHandStack().getItem() == Items.END_CRYSTAL) {
                    mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, blockHit);
                    mc.player.swingHand(Hand.MAIN_HAND);
                }
            }
        }
    }

    private boolean hasItemInHotbar() {
        for (int i = 0; i < 9; i++) {
            var stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.END_CRYSTAL) return true;
        }
        return false;
    }

    private boolean isObsidianOrBedrock(BlockPos pos) {
        if (mc.world == null) return false;
        var block = mc.world.getBlockState(pos).getBlock();
        return block == net.minecraft.block.Blocks.OBSIDIAN || block == net.minecraft.block.Blocks.BEDROCK;
    }

    private boolean isValidCrystalPosition(BlockPos pos) {
        if (mc.world == null) return false;
        if (mc.player.getEntityPos().distanceTo(Vec3d.ofCenter(pos)) > 4.5) return false;

        if (!mc.world.getBlockState(pos).isAir()) return false;
        if (!mc.world.getBlockState(pos.up()).isAir()) return false;

        BlockPos playerPos = mc.player.getBlockPos();
        return !pos.equals(playerPos) && !pos.equals(playerPos.up());
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (autoSwitch.getValue()) originalSlot = mc.player.getInventory().getSelectedSlot();
        timer.reset();
    }

    @Override
    public void onDisable() {
        super.onDisable();
        if (switchBack.getValue() && originalSlot != -1 && mc.player != null) {
            mc.player.getInventory().setSelectedSlot(originalSlot);
            InventoryUtil.syncSelectedSlot();
        }
        originalSlot = -1;
    }

    @Override
    public int getKey() {
        return -1;
    }
}