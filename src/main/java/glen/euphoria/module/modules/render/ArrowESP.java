package glen.euphoria.module.modules.render;

import glen.euphoria.event.impl.render.Render2DEvent;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.module.setting.BooleanSetting;
import glen.euphoria.module.setting.ColorSetting;
import glen.euphoria.module.setting.NumberSetting;
import glen.euphoria.utils.render.TextureRenderer;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2fStack;

import java.awt.*;

public final class ArrowESP extends Module {
    private static final Identifier ARROW_TEXTURE = Identifier.of("euphoria", "imgs/triangle.png");
    private final BooleanSetting showSelf = new BooleanSetting("Show Self", false);
    private final NumberSetting range = new NumberSetting("Range", 10.0D, 200.0D, 120.0D, 5.0D);
    private final NumberSetting size = new NumberSetting("Size", 8.0D, 64.0D, 24.0D, 1.0D);
    private final NumberSetting offset = new NumberSetting("Offset", 0.0D, 64.0D, 28.0D, 1.0D);
    private final ColorSetting color = new ColorSetting("Color", new Color(255, 255, 255, 200));

    public ArrowESP() {
        super("Arrow ESP", "Displays arrows pointing to players", -1, Category.RENDER);
        addSettings(showSelf, range, size, offset, color);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (event.isContentPass()) return; // DrawContext overlay -> in-HUD pass
        if (isNull()) return;

        DrawContext context = event.getContext();
        Matrix3x2fStack matrices = context.getMatrices();
        int width = event.getWidth();
        int height = event.getHeight();
        float centerX = width * 0.5f;
        float centerY = height * 0.5f;

        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
        Vec3d cameraPos = mc.gameRenderer.getCamera().getCameraPos();
        float cameraYaw = mc.gameRenderer.getCamera().getYaw();

        Color arrowColor = color.getValue();
        int baseR = arrowColor.getRed();
        int baseG = arrowColor.getGreen();
        int baseB = arrowColor.getBlue();
        int baseA = arrowColor.getAlpha();
        float sizeValue = size.getValueFloat();
        float offsetValue = offset.getValueFloat();
        double maxRange = range.getValue();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (!shouldRender(player)) continue;
            double distanceSq = mc.player.squaredDistanceTo(player);
            if (distanceSq > maxRange * maxRange) continue;

            double interpolatedX = player.lastX + (player.getX() - player.lastX) * tickDelta;
            double interpolatedZ = player.lastZ + (player.getZ() - player.lastZ) * tickDelta;

            double dx = interpolatedX - cameraPos.x;
            double dz = interpolatedZ - cameraPos.z;

            double planar = Math.sqrt(dx * dx + dz * dz);
            if (planar < 1.0E-6) continue;

            float yawToPlayer = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(dz, dx)) - 90.0F - cameraYaw);

            double distance = Math.sqrt(distanceSq);
            float distanceFactor = (float) Math.min(distance / maxRange, 1.0D);
            float alphaScale = 1.0f - distanceFactor * 0.6f;
            int arrowAlpha = Math.max(24, Math.min(255, (int) (baseA * alphaScale)));

            matrices.pushMatrix();
            matrices.translate(centerX, centerY);
            matrices.rotate((float) Math.toRadians(yawToPlayer));
            matrices.translate(0.0f, -(offsetValue + distanceFactor * offsetValue));

            int baseColorPacked = (baseA << 24) | (baseR << 16) | (baseG << 8) | baseB;
            int packed = (arrowAlpha << 24) | (baseColorPacked & 0x00FFFFFF);
            TextureRenderer.drawCenteredQuad(context, ARROW_TEXTURE, sizeValue, sizeValue, packed);
            matrices.popMatrix();
        }
    }

    private boolean shouldRender(PlayerEntity player) {
        if (player == mc.player && !showSelf.getValue()) return false;
        return !player.isSpectator();
    }
}
