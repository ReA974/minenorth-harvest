package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Moulin : ne garde que l'instant du dernier tour de meule (les ailes tournent plus vite quand on moud) et l'angle côté client. */
public class WindmillBlockEntity extends BlockEntity {
    private long lastTurn = -1000;

    /** Animation côté client (non sauvegardée). */
    public float angle, stoneAngle;
    public float lastRender = -1;

    public WindmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MILL.get(), pos, state);
    }

    public long lastTurn() { return lastTurn; }
    public Direction facing() { return getBlockState().getValue(WindmillBlock.FACING); }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(5, 4, 5);
    }

    public void turned(Level level) {
        lastTurn = level.getGameTime();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("turn", lastTurn);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        lastTurn = tag.contains("turn") ? tag.getLong("turn") : -1000;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
