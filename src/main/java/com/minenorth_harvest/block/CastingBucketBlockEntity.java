package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Seau de coulée : reçoit le métal en fusion du four, refroidit, puis se vide en lingots. */
public class CastingBucketBlockEntity extends BlockEntity {
    private ItemStack contents = ItemStack.EMPTY;
    /** Ticks restants avant que le métal soit solide (0 = solide). */
    private int melt;
    private int meltTotal = 1;

    public CastingBucketBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CASTING_BUCKET.get(), pos, state);
    }

    public ItemStack contents() { return contents; }
    public boolean isEmpty() { return contents.isEmpty(); }
    public boolean isMolten() { return !contents.isEmpty() && melt > 0; }
    public int melt() { return melt; }
    public int meltTotal() { return meltTotal; }

    /** Versement du four : le métal arrive brûlant. */
    public void fill(ItemStack ingots) {
        contents = ingots.copy();
        meltTotal = melt = Math.max(1, HarvestConfig.COOL_TICKS.get());
        sync();
    }

    /** Trempe à l'eau : solidifie tout de suite. */
    public void quench() {
        if (melt <= 0) return;
        melt = 0;
        if (level instanceof ServerLevel server) {
            level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.8f, 1.0f);
            server.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, 12, 0.15, 0.1, 0.15, 0.03);
        }
        sync();
    }

    public ItemStack take() {
        ItemStack out = contents;
        contents = ItemStack.EMPTY;
        melt = 0;
        sync();
        return out;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CastingBucketBlockEntity be) {
        if (be.contents.isEmpty() || be.melt <= 0) return;
        be.melt--;
        if (level instanceof ServerLevel server && level.getGameTime() % 10 == 0) {
            server.sendParticles(ParticleTypes.SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.1, 0.05, 0.1, 0.01);
        }
        if (be.melt <= 0) {
            level.playSound(null, pos, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.4f, 1.3f);
            be.sync();
        } else {
            be.setChanged();
        }
    }

    /** Couleur (RGB) du métal liquide selon le lingot contenu. */
    public static int metalColor(ItemStack stack) {
        Item item = stack.getItem();
        if (item == Items.IRON_INGOT) return 0xFFE2B8;
        if (item == Items.COPPER_INGOT) return 0xFF8A55;
        if (item == Items.GOLD_INGOT) return 0xFFD84A;
        return 0xFFA040;
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("c", contents.save(new CompoundTag()));
        tag.putInt("melt", melt);
        tag.putInt("total", meltTotal);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        contents = ItemStack.of(tag.getCompound("c"));
        melt = tag.getInt("melt");
        meltTotal = Math.max(1, tag.getInt("total"));
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
