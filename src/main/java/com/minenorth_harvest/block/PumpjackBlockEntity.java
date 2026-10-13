package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlockEntities;
import com.minenorth_harvest.registry.ModBlocks;
import com.minenorth_harvest.registry.ModItems;
import com.minenorth_harvest.zone.ZoneData;
import com.minenorth_harvest.zone.ZoneManager;
import com.minenorth_harvest.zone.ZoneType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Chevalet de pompage : posé sur un gisement de pétrole, il pompe du brut tout seul (animation côté client tant qu'il travaille). */
public class PumpjackBlockEntity extends BlockEntity {
    public static final int MAX_BUFFER = 64;

    private int stored;
    private int progress;
    private boolean working;
    private boolean dry;

    public PumpjackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PUMPJACK.get(), pos, state);
    }

    public int stored() { return stored; }
    public boolean working() { return working; }
    public boolean dry() { return dry; }
    public Direction facing() { return getBlockState().getValue(PumpjackBlock.FACING); }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(4, 3, 4);
    }

    public ItemStack take() {
        ItemStack out = stored > 0 ? new ItemStack(ModItems.CRUDE_OIL.get(), stored) : ItemStack.EMPTY;
        stored = 0;
        sync();
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PumpjackBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean deposit = level.getBlockState(pos.below()).is(ModBlocks.OIL_DEPOSIT.get());
        boolean work = deposit && be.stored < MAX_BUFFER;
        boolean dry = !deposit;
        boolean changed = false;
        if (work != be.working || dry != be.dry) { be.working = work; be.dry = dry; changed = true; }
        if (work) {
            be.progress++;
            if (be.progress % 30 == 0) level.playSound(null, pos, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.35f, 0.55f);
            if (be.progress >= HarvestConfig.PUMPJACK_TICKS.get()) {
                be.progress = 0;
                be.stored++;
                changed = true;
                if (level.random.nextDouble() < HarvestConfig.PUMPJACK_DEPLETE.get()) {
                    BlockPos well = pos.below();
                    level.setBlock(well, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);   // puits tari
                    ZoneData data = ZoneData.get(server);
                    var zone = data.zoneAt(well, ZoneType.PETROLE);
                    if (zone != null && zone.regrow) {
                        data.addRegrow(well, ZoneManager.OIL_REGEN, level.getGameTime() + HarvestConfig.OIL_REGEN_DELAY.get() * 20L);
                    }
                }
            }
        }
        if (changed) be.sync();
        be.setChanged();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("stored", stored);
        tag.putInt("progress", progress);
        tag.putBoolean("working", working);
        tag.putBoolean("dry", dry);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        stored = tag.getInt("stored");
        progress = tag.getInt("progress");
        working = tag.getBoolean("working");
        dry = tag.getBoolean("dry");
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
