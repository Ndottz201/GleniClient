package glen.euphoria.utils.render;

import lombok.experimental.UtilityClass;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexRendering;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix4f;

import java.awt.*;

/**
 * World-space box/line drawing.
 *
 * <p>Ported to the 1.21.5+ render pipeline: the old {@code Tessellator.begin} +
 * {@code BufferRenderer.drawWithGlobalProgram} + {@code RenderSystem.setShader(POSITION_COLOR)}
 * path was deleted by Mojang. Drawing now goes through the immediate
 * {@link VertexConsumerProvider} (the entity buffer source) and the vanilla
 * {@code RenderLayers.LINES} / {@code RenderLayers.debugFilledBox()} layers, with
 * outlines emitted by Mojang's own {@link VertexRendering#drawOutline} helper.</p>
 */
@UtilityClass
public class Render3DEngine {

    private static VertexConsumerProvider.Immediate provider() {
        return MinecraftClient.getInstance().getBufferBuilders().getEntityVertexConsumers();
    }

    public void setup() {
        // Render state is now owned by the render layers; nothing to set up globally.
    }

    public void setupThroughWalls() {
        setup();
    }

    public void end() {
        // Flush any pending immediate geometry.
        provider().draw();
    }

    public void drawBox(MatrixStack stack, Box box, Color color) {
        drawFilledBox(stack, box, color);
    }

    public void drawBox(MatrixStack stack, double x, double y, double z, float width, float height, Color color) {
        float hw = width / 2f;
        drawFilledBox(stack, new Box(x - hw, y, z - hw, x + hw, y + height, z + hw), color);
    }

    public void drawOutlineBox(MatrixStack stack, Box box, Color color) {
        VertexConsumerProvider.Immediate immediate = provider();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.LINES);
        VertexRendering.drawOutline(stack, vc, VoxelShapes.cuboid(box), 0.0, 0.0, 0.0, color.getRGB(), 1.0f);
        immediate.draw(RenderLayers.LINES);
    }

    public void drawOutlineBox(MatrixStack stack, double x, double y, double z, float width, float height, Color color) {
        float hw = width / 2f;
        drawOutlineBox(stack, new Box(x - hw, y, z - hw, x + hw, y + height, z + hw), color);
    }

    public void drawFilledBox(MatrixStack stack, Box box, Color color) {
        Matrix4f matrix = stack.peek().getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = provider();
        VertexConsumer b = immediate.getBuffer(RenderLayers.debugFilledBox());

        int r = color.getRed(), g = color.getGreen(), bl = color.getBlue(), a = color.getAlpha();

        float minX = (float) box.minX, minY = (float) box.minY, minZ = (float) box.minZ;
        float maxX = (float) box.maxX, maxY = (float) box.maxY, maxZ = (float) box.maxZ;

        // Front
        b.vertex(matrix, minX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, minX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, minZ).color(r, g, bl, a);
        // Right
        b.vertex(matrix, maxX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, minZ).color(r, g, bl, a);
        // Back
        b.vertex(matrix, maxX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, maxZ).color(r, g, bl, a);
        // Left
        b.vertex(matrix, minX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, minX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, maxZ).color(r, g, bl, a);
        // Top
        b.vertex(matrix, minX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, maxY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, maxY, maxZ).color(r, g, bl, a);
        // Bottom
        b.vertex(matrix, minX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, minX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, minX, minY, minZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, maxZ).color(r, g, bl, a);
        b.vertex(matrix, maxX, minY, minZ).color(r, g, bl, a);

        immediate.draw(RenderLayers.debugFilledBox());
    }

    public void drawFilledBox(MatrixStack stack, double x, double y, double z, float width, float height, Color color) {
        float hw = width / 2f;
        drawFilledBox(stack, new Box(x - hw, y, z - hw, x + hw, y + height, z + hw), color);
    }

    public void drawLine(MatrixStack stack, Vec3d start, Vec3d end, Color color) {
        MatrixStack.Entry entry = stack.peek();
        Matrix4f matrix = entry.getPositionMatrix();
        VertexConsumerProvider.Immediate immediate = provider();
        VertexConsumer vc = immediate.getBuffer(RenderLayers.LINES);

        int r = color.getRed(), g = color.getGreen(), bl = color.getBlue(), a = color.getAlpha();
        float nx = (float) (end.x - start.x), ny = (float) (end.y - start.y), nz = (float) (end.z - start.z);
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len != 0) { nx /= len; ny /= len; nz /= len; }

        vc.vertex(matrix, (float) start.x, (float) start.y, (float) start.z).color(r, g, bl, a).normal(entry, nx, ny, nz);
        vc.vertex(matrix, (float) end.x, (float) end.y, (float) end.z).color(r, g, bl, a).normal(entry, nx, ny, nz);
        immediate.draw(RenderLayers.LINES);
    }

    public Vec3d getInterpolatedPos(Entity entity, float tickDelta) {
        return new Vec3d(
            entity.lastX + (entity.getX() - entity.lastX) * tickDelta,
            entity.lastY + (entity.getY() - entity.lastY) * tickDelta,
            entity.lastZ + (entity.getZ() - entity.lastZ) * tickDelta
        );
    }
}
