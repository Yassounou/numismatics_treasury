package dev.yassou.numismaticstreasury.block;

import com.mojang.serialization.MapCodec;
import dev.yassou.numismaticstreasury.block.entity.ServerShopBlockEntity;
import dev.yassou.numismaticstreasury.config.TreasuryConfig;
import dev.yassou.numismaticstreasury.network.TreasuryNetwork;
import dev.yassou.numismaticstreasury.server.BankService;
import dev.yassou.numismaticstreasury.server.history.HistoryService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

public final class PlayerShopBlock extends BaseEntityBlock {
    public static final MapCodec<PlayerShopBlock> CODEC = simpleCodec(PlayerShopBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 7, 16);

    public PlayerShopBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(
                FACING,
                context.getHorizontalDirection().getOpposite()
        );
    }

    public static void forceManagementInteraction(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()
                || !(event.getLevel().getBlockState(event.getPos()).getBlock()
                instanceof PlayerShopBlock)
                || !(event.getLevel().getBlockEntity(event.getPos())
                instanceof ServerShopBlockEntity shop)
                || !shop.canManage(player)) {
            return;
        }
        event.setUseBlock(TriState.TRUE);
        event.setUseItem(TriState.FALSE);
    }

    public static void protectStockAndOwnership(BlockEvent.BreakEvent event) {
        if (!(event.getState().getBlock() instanceof PlayerShopBlock)
                || !(event.getLevel().getBlockEntity(event.getPos())
                instanceof ServerShopBlockEntity shop)) {
            return;
        }
        Player player = event.getPlayer();
        String message = null;
        if (shop.stock() > 0L) {
            message = "message.numismatics_treasury.player_shop.stock_not_empty";
        } else if (!shop.canManage(player)) {
            message = "message.numismatics_treasury.player_shop.not_owner";
        }
        if (message != null) {
            event.setCanceled(true);
            if (!event.getLevel().isClientSide()) {
                player.sendSystemMessage(Component.translatable(message));
            }
        } else if (!event.getLevel().isClientSide()) {
            HistoryService.removeShop(shop);
        }
    }

    @Override
    public void setPlacedBy(
            Level level,
            BlockPos pos,
            BlockState state,
            @Nullable LivingEntity placer,
            ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player
                && level.getBlockEntity(pos) instanceof ServerShopBlockEntity shop) {
            shop.setOwner(player.getUUID(), player.getGameProfile().getName());
            if (player instanceof ServerPlayer serverPlayer) {
                BankService.account(serverPlayer);
            }
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        return interact(level, pos, player)
                ? ItemInteractionResult.sidedSuccess(level.isClientSide)
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hit
    ) {
        return interact(level, pos, player)
                ? InteractionResult.sidedSuccess(level.isClientSide)
                : InteractionResult.PASS;
    }

    private boolean interact(Level level, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof ServerShopBlockEntity shop)) return false;
        if (level.isClientSide) return true;
        if (!TreasuryConfig.get().modules.playerShop) {
            player.sendSystemMessage(Component.translatable(
                    "message.numismatics_treasury.player_shop.disabled"));
            return true;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return true;
        if (player.isShiftKeyDown() && shop.canManage(player)) {
            serverPlayer.openMenu(shop);
        } else if (!shop.configured()) {
            player.sendSystemMessage(Component.translatable(
                    "message.numismatics_treasury.player_shop.not_configured"));
        } else if (shop.stock() <= 0L) {
            player.sendSystemMessage(Component.translatable(
                    "message.numismatics_treasury.player_shop.out_of_stock"));
        } else if (player.getUUID().equals(shop.ownerUuid())) {
            player.sendSystemMessage(Component.translatable(
                    "message.numismatics_treasury.player_shop.use_sneak"));
        } else {
            TreasuryNetwork.openPlayerShop(serverPlayer, shop);
        }
        return true;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(
            BlockState state,
            BlockGetter level,
            BlockPos pos,
            CollisionContext context
    ) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ServerShopBlockEntity(pos, state);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected void createBlockStateDefinition(
            StateDefinition.Builder<Block, BlockState> builder
    ) {
        builder.add(FACING);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
