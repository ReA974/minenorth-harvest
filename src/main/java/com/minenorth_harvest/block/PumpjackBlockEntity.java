package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
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

/**
 * Cheval à bascule : réserve de pétrole du puits (se vide au fil des parties, se remplit de nouveau après oilRegenDelaySeconds) et état d'animation
 * (le balancier travaille pendant une partie de mini-jeu).
 */
public class PumpjackBlockEntity extends BlockEntity {
    /** -1 = jamais utilisé : réserve pleine. */
    private int reserve = -1;
    /** Instant (tick de jeu) où le puits à sec se remplit de nouveau, 0 = pas à sec. */
    private long dryUntil;
    private boolean working;
    private long workingUntil;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMPJACK.get(), pos, state);
    }

    public boolean working() { return working; }
    public Direction facing() { return getBlockState().getValue(PumpjackBlock.FACING); }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(4, 3, 4);
    }

    public int reserve() {
        int max = HarvestConfig.PUMP_RESERVE.get();
        return reserve < 0 ? max : Math.min(reserve, max);
    }

    /** Puits à sec à cet instant ? (le remplit de nouveau si le délai est passé) */
    public boolean isDry(long now) {
        if (dryUntil == 0) return false;
        if (now >= dryUntil) {
            dryUntil = 0;
            reserve = -1;
            sync();
            return false;
        }
        return true;
    }

    public long dryRemainingTicks(long now) { return Math.max(0, dryUntil - now); }

    /** Retire du pétrole de la réserve ; à zéro, le puits est à sec jusqu'à la reconstitution. */
    public void consume(int n, long now) {
        reserve = Math.max(0, reserve() - n);
        if (reserve == 0) dryUntil = now + HarvestConfig.OIL_REGEN_DELAY.get() * 20L;
        sync();
    }

    /** Le balancier travaille jusqu'à cet instant (tick de jeu). */
    public void workUntil(long tick) {
        workingUntil = Math.max(workingUntil, tick);
        if (!working) { working = true; sync(); }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity be) {
        if (be.working && level.getGameTime() >= be.workingUntil) { be.working = false; be.sync(); }
        if (be.dryUntil != 0 && level.getGameTime() >= be.dryUntil) { be.dryUntil = 0; be.reserve = -1; be.sync(); }
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("reserve", reserve);
        tag.putLong("dryUntil", dryUntil);
        tag.putBoolean("working", working);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        reserve = tag.contains("reserve") ? tag.getInt("reserve") : -1;
        dryUntil = tag.getLong("dryUntil");
        working = tag.getBoolean("working");
        workingUntil = 0;
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
