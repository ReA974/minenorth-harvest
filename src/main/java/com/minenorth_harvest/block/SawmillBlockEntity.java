package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
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

/**
 * Scierie : on pose des bûches sur le chariot, il les fait passer sous la lame (animation côté client),
 * les planches sortent à l'avant. Le cycle est calculé côté serveur, le client anime à partir de {@code cycleStart}.
 */
public class SawmillBlockEntity extends BlockEntity {
    public static final int MAX_INPUT = 16;
    public static final int MAX_OUTPUT = 120;

    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    /** Tick de jeu où le cycle en cours a démarré, -1 si la scie est à l'arrêt. */
    private long cycleStart = -1;
    private int cycleLen = 80;

    public SawmillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SAWMILL.get(), pos, state);
    }

    /** Point du monde d'un point du modèle (en pixels, repère du maître orienté au nord). */
    public net.minecraft.world.phys.Vec3 worldPoint(double px, double py, double pz) {
        net.minecraft.core.Direction f = getBlockState().getValue(SawmillBlock.FACING);
        double dx = px / 16.0 - 0.5, dz = pz / 16.0 - 0.5;
        double rx, rz;
        switch (f) {
            case EAST -> { rx = -dz; rz = dx; }
            case SOUTH -> { rx = -dx; rz = -dz; }
            case WEST -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        return new net.minecraft.world.phys.Vec3(worldPosition.getX() + 0.5 + rx, worldPosition.getY() + py / 16.0, worldPosition.getZ() + 0.5 + rz);
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(4, 3, 4);
    }

    public ItemStack input() { return input; }
    public ItemStack output() { return output; }
    public long cycleStart() { return cycleStart; }
    public int cycleLen() { return cycleLen; }

    /** Ajoute des bûches ; renvoie le nombre réellement acceptées. */
    public int insert(ItemStack stack) {
        MachineRecipes.Recipe r = MachineRecipes.find(stack, HarvestConfig.SAWMILL_RECIPES.get());
        if (r == null) return 0;
        if (!input.isEmpty() && !ItemStack.isSameItemSameTags(input, stack)) return 0;
        int room = MAX_INPUT - input.getCount();
        int moved = Math.min(room, stack.getCount());
        if (moved <= 0) return 0;
        if (input.isEmpty()) input = stack.copyWithCount(moved); else input.grow(moved);
        sync();
        return moved;
    }

    public ItemStack takeOutput() {
        ItemStack out = output;
        output = ItemStack.EMPTY;
        sync();
        return out;
    }

    public ItemStack takeInput() {
        ItemStack in = input;
        input = ItemStack.EMPTY;
        cycleStart = -1;
        sync();
        return in;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SawmillBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        MachineRecipes.Recipe r = be.input.isEmpty() ? null : MachineRecipes.find(be.input, HarvestConfig.SAWMILL_RECIPES.get());
        boolean canRun = r != null && be.input.getCount() >= r.inCount() && be.output.getCount() + r.outCount() * 2 <= MAX_OUTPUT
                && (be.output.isEmpty() || be.output.is(r.output()));
        if (!canRun) {
            if (be.cycleStart >= 0) { be.cycleStart = -1; be.sync(); }
            return;
        }
        long now = level.getGameTime();
        if (be.cycleStart < 0) {
            be.cycleStart = now;
            be.cycleLen = HarvestConfig.SAW_TICKS.get();
            be.sync();
            return;
        }
        long t = now - be.cycleStart;
        // sciure et bruit quand le tronc passe sous la lame (milieu du cycle)
        if (t > be.cycleLen * 0.3 && t < be.cycleLen * 0.75) {
            if (t % 4 == 0) {
                level.playSound(null, pos, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.5f, 1.4f + level.random.nextFloat() * 0.3f);
            }
            Block planks = Block.byItem(r.output());
            var blade = be.worldPoint(16, 19, 16);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, planks.defaultBlockState()),
                    blade.x, blade.y, blade.z, 4, 0.25, 0.12, 0.25, 0.05);
        }
        if (t >= be.cycleLen) {
            be.input.shrink(r.inCount());
            boolean lucky = HarvestConfig.SAWMILL_LUCK.get() > 0 && level.random.nextDouble() < HarvestConfig.SAWMILL_LUCK.get();
            int made = lucky ? r.outCount() * 2 : r.outCount();
            if (be.output.isEmpty()) be.output = new ItemStack(r.output(), made); else be.output.grow(made);
            if (be.input.isEmpty()) be.input = ItemStack.EMPTY;
            be.cycleStart = -1; // le prochain tick relance un cycle
            level.playSound(null, pos, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 0.8f, 0.9f);
            if (lucky) {
                level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.5f);
                var top = be.worldPoint(16, 34, 16);
                server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, 14, 0.4, 0.3, 0.4, 0.2);
            }
            be.sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("in", input.save(new CompoundTag()));
        tag.put("out", output.save(new CompoundTag()));
        tag.putLong("start", cycleStart);
        tag.putInt("len", cycleLen);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        input = ItemStack.of(tag.getCompound("in"));
        output = ItemStack.of(tag.getCompound("out"));
        cycleStart = tag.contains("start") ? tag.getLong("start") : -1;
        cycleLen = tag.contains("len") ? Math.max(1, tag.getInt("len")) : 80;
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
