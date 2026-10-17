package glen.euphoria.mixin;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.modules.combat.AutoInventoryTotem;
import glen.euphoria.module.modules.render.ContainerSlots;
import glen.euphoria.utils.render.font.FontManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static glen.euphoria.EuphoriaClient.mc;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {

    // ─── ContainerSlots rendering ───

    @Inject(method = "drawSlot", at = @At("TAIL"))
    public void postDrawSlot(DrawContext context, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!EuphoriaClient.INSTANCE.moduleManager.getModule(ContainerSlots.class).get().isEnabled()) return;

        if (ContainerSlots.highlightTotem.getValue() && slot.hasStack()) {
            if (slot.getStack().getItem() == Items.TOTEM_OF_UNDYING) {
                context.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, ContainerSlots.highlightColor.getValue().getRGB());
            }
        }

        if (ContainerSlots.disableText.getValue()) return;

        if (ContainerSlots.fontMode.isMode("Inter")) {
            EuphoriaClient.INSTANCE.fontManager
                    .getSize(10, FontManager.Type.Inter)
                    .drawString(new net.minecraft.client.util.math.MatrixStack(), String.valueOf(slot.getIndex()), slot.x, slot.y, ContainerSlots.color.getValue());
        } else {
            context.drawText(
                    mc.textRenderer,
                    String.valueOf(slot.getIndex()),
                    slot.x,
                    slot.y,
                    ContainerSlots.color.getValue().getRGB(),
                    false
            );
        }
    }

    // ─── AutoInventoryTotem input blocking ───

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockKeysWhenLocked(net.minecraft.client.input.KeyInput input,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockClicksWhenLocked(net.minecraft.client.gui.Click click, boolean doubled,
                                             CallbackInfoReturnable<Boolean> cir) {
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void euphoria$blockReleaseWhenLocked(net.minecraft.client.gui.Click click,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (AutoInventoryTotem.isInputLocked()) {
            cir.setReturnValue(true);
        }
    }
}