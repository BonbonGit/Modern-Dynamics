/*
 * Modern Dynamics
 * Copyright (C) 2021 shartte & Technici4n
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package dev.technici4n.moderndynamics.pipe;

import com.google.common.base.Preconditions;
import dev.technici4n.moderndynamics.network.NodeHost;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.Waterloggable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContext;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;

public class PipeBlock extends Block implements BlockEntityProvider, Waterloggable {
    private static final BooleanProperty WATERLOGGED = Properties.WATERLOGGED;

    public final String id;
    private PipeItem item;
    private BlockEntityType<PipeBlockEntity> blockEntityType;

    public PipeBlock(String id) {
        super(Settings.of(Material.METAL).nonOpaque().solidBlock((state, world, pos) -> false));
        this.id = id;
        this.setDefaultState(this.getDefaultState().with(WATERLOGGED, false));
    }

    public PipeItem getItem() {
        Preconditions.checkState(this.item != null, "Item has not been set on %s", this);
        return this.item;
    }

    public void setItem(PipeItem item) {
        Preconditions.checkState(this.item == null, "Item has already been set on %s", this);
        this.item = item;
    }

    @Nullable
    public BlockEntityType<PipeBlockEntity> getBlockEntityTypeNullable() {
        return this.blockEntityType;
    }

    public BlockEntityType<PipeBlockEntity> getBlockEntityType() {
        Preconditions.checkState(this.blockEntityType != null, "Block entity type has not been set on %s", this);
        return this.blockEntityType;
    }

    public void setBlockEntityProvider(BlockEntityType<PipeBlockEntity> blockEntityType) {
        Preconditions.checkState(this.blockEntityType == null, "blockEntityType has already been set on %s", this);
        this.blockEntityType = blockEntityType;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(WATERLOGGED);
    }

    @Nullable
    @Override
    public BlockState getPlacementState(ItemPlacementContext context) {
        BlockPos pos = context.getBlockPos();
        FluidState fluidState = context.getWorld().getFluidState(pos);
        return this.getDefaultState().with(WATERLOGGED, fluidState.getFluid() == Fluids.WATER);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.get(WATERLOGGED) ? Fluids.WATER.getStill(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState, WorldAccess level, BlockPos currentPos,
            BlockPos neighborPos) {
        if (state.get(WATERLOGGED)) {
            level.scheduleFluidTick(currentPos, Fluids.WATER, Fluids.WATER.getTickRate(level));
        }

        return super.getStateForNeighborUpdate(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Override
    public List<ItemStack> getDroppedStacks(BlockState state, LootContext.Builder builder) {
        var drops = super.getDroppedStacks(state, builder);
        if (builder.getNullable(LootContextParameters.BLOCK_ENTITY) instanceof PipeBlockEntity pipe) {
            for (var host : pipe.getHosts()) {
                host.addDrops(drops);
            }
        }
        return drops;
    }

    @SuppressWarnings("deprecation")
    @Override
    public int getOpacity(BlockState state, BlockView blockView, BlockPos pos) {
        return 0;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block param4, BlockPos param5, boolean param6) {
        if (world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            pipe.scheduleHostUpdates();
        }
    }

    @Override
    public boolean hasDynamicBounds() {
        return true;
    }

    @SuppressWarnings("deprecation")
    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext ctx) {
        if (world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            return pipe.getCachedShape();
        } else {
            return PipeBoundingBoxes.CORE_SHAPE;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hitResult) {
        if (world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
            return pipe.onUse(player, hand, hitResult);
        } else {
            return ActionResult.PASS;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            if (world.getBlockEntity(pos) instanceof PipeBlockEntity pipe) {
                pipe.onRemoved();
            }
        }
        super.onStateReplaced(state, world, pos, newState, moved);
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return blockEntityType.instantiate(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClient()) {
            return (l, p, s, pipeBe) -> ((PipeBlockEntity) pipeBe).clientTick();
        }
        return null;
    }
}