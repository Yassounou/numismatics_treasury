package dev.yassou.numismaticstreasury.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.client.gui.MoneyDisplay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class ServerShopRenderer implements BlockEntityRenderer<ServerShopBlockEntity> {
    private static final double BASE_TOP = 7.0D / 16.0D;
    private static final float ITEM_SCALE = 0.95F;
    private static final double PRICE_X = 0.5D;
    private static final double PRICE_Y = 0.4279D;
    private static final double PRICE_Z = -0.0195D;
    private static final float MAX_PRICE_SCALE = 0.013F;
    private static final float PRICE_WIDTH = 0.66F;

    private final Font font;

    public ServerShopRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(
            ServerShopBlockEntity shop,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        if (shop.getLevel() == null) return;

        poseStack.pushPose();
        rotateWithBlock(shop, poseStack);

        ItemStack stack = shop.template();
        if (!stack.isEmpty()) {
            renderItem(shop, stack, poseStack, buffers, packedLight, packedOverlay);
        }
        if (shop.configured()) {
            renderPrice(shop, poseStack, buffers);
        }
        poseStack.popPose();
    }

    private static void rotateWithBlock(
            ServerShopBlockEntity shop,
            PoseStack poseStack
    ) {
        if (!shop.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return;
        }
        Direction facing = shop.getBlockState().getValue(
                BlockStateProperties.HORIZONTAL_FACING);
        float modelRotation = switch (facing) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
        if (modelRotation == 0.0F) return;
        poseStack.translate(0.5D, 0.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(-modelRotation));
        poseStack.translate(-0.5D, 0.0D, -0.5D);
    }

    private void renderItem(
            ServerShopBlockEntity shop,
            ItemStack stack,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            int packedOverlay
    ) {
        poseStack.pushPose();
        // Vanilla uses a different GROUND transform for block models and generated items.
        // These two origins make either kind touch the 7/16-high base without bobbing.
        double itemY = stack.getItem() instanceof BlockItem
                ? BASE_TOP - ITEM_SCALE / 16.0D
                : BASE_TOP + ITEM_SCALE / 8.0D;
        poseStack.translate(0.5D, itemY, 0.5D);
        poseStack.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GROUND,
                packedLight,
                packedOverlay,
                poseStack,
                buffers,
                shop.getLevel(),
                (int) shop.getBlockPos().asLong()
        );
        poseStack.popPose();
    }

    private void renderPrice(
            ServerShopBlockEntity shop,
            PoseStack poseStack,
            MultiBufferSource buffers
    ) {
        Component price = Component.translatable(
                "display.numismatics_treasury.shop.price",
                MoneyDisplay.exact(shop.price())
        );
        int textWidth = Math.max(1, font.width(price));
        float scale = Math.min(MAX_PRICE_SCALE, PRICE_WIDTH / textWidth);

        poseStack.pushPose();
        // Centre of the north face of the tilted Blockbench element named "price".
        poseStack.translate(PRICE_X, PRICE_Y, PRICE_Z);
        poseStack.mulPose(Axis.XP.rotationDegrees(45.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(scale, -scale, scale);
        font.drawInBatch(
                price,
                -textWidth / 2.0F,
                -font.lineHeight / 2.0F,
                0xFF111111,
                false,
                poseStack.last().pose(),
                buffers,
                Font.DisplayMode.POLYGON_OFFSET,
                0,
                LightTexture.FULL_BRIGHT
        );
        poseStack.popPose();
    }
}
