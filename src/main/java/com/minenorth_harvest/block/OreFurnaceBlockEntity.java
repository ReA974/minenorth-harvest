package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Four à minerai : minerai + combustible, le soufflet (clic droit à main nue) accélère la fonte.
 * Le métal fondu coule par le bec dans un seau de coulée posé devant (sinon il attend dans le four).
 */
public class OreFurnaceBlockEntity extends BlockEntity {
    public static final int MAX_STACK = 16;
    public static final int POUR_TICKS = 40;

    private ItemStack ore = ItemStack.EMPTY;
    private ItemStack fuel = ItemStack.EMPTY;
    private int burn, progress, boost;
    /** Lingots prêts à être coulés. */
    private ItemStack pending = ItemStack.EMPTY;
    /** Tick de jeu où la coulée a commencé, -1 si le bec est fermé. */
    private long pourStart = -1;
    private long lastPump;

    public OreFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORE_FURNACE.get(), pos, state);
    }

    public ItemStack ore() { return ore; }
    public ItemStack fuel() { return fuel; }
    public ItemStack pending() { return pending; }
    public long pourStart() { return pourStart; }
    public int progress() { return progress; }

    public static int burnTimeOf(ItemStack stack) {
        if (stack.isEmpty() || stack.hasCraftingRemainingItem()) return 0;
        return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING);
    }

    public int insertOre(ItemStack stack) {
        if (!ore.isEmpty() && !ItemStack.isSameItemSameTags(ore, stack)) return 0;
        int moved = Math.min(MAX_STACK - ore.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        if (ore.isEmpty()) ore = stack.copyWithCount(moved); else ore.grow(moved);
        sync();
        return moved;
    }

    public int insertFuel(ItemStack stack) {
        if (!fuel.isEmpty() && !ItemStack.isSameItemSameTags(fuel, stack)) return 0;
        int moved = Math.min(MAX_STACK - fuel.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        if (fuel.isEmpty()) fuel = stack.copyWithCount(moved); else fuel.grow(moved);
        sync();
        return moved;
    }

    public ItemStack takeOre() {
        ItemStack out = ore;
        ore = ItemStack.EMPTY;
        progress = 0;
        sync();
        return out;
    }

    public ItemStack takeFuel() {
        ItemStack out = fuel;
        fuel = ItemStack.EMPTY;
        sync();
        return out;
    }

    public boolean hasBucket(Level level) {
        return bucket(level) != null;
    }

    @Nullable
    private CastingBucketBlockEntity bucket(Level level) {
        Direction facing = getBlockState().getValue(OreFurnaceBlock.FACING);
        return level.getBlockEntity(worldPosition.relative(facing)) instanceof CastingBucketBlockEntity b ? b : null;
    }

    public boolean isBurning() { return burn > 0; }

    /** Coup de soufflet : fonte et combustion accélérées quelques secondes. */
    public boolean pump(Level level) {
        if (burn <= 0) return false;
        long now = level.getGameTime();
        if (now - lastPump < 6) return true;
        lastPump = now;
        boost = Math.min(HarvestConfig.BELLOWS_MAX_TICKS.get(), boost + HarvestConfig.BELLOWS_BOOST_TICKS.get());
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.35f, 0.6f);
        level.playSound(null, worldPosition, SoundEvents.BLAZE_SHOOT, SoundSource.BLOCKS, 0.15f, 0.5f);
        if (level instanceof ServerLevel server) {
            Direction f = getBlockState().getValue(OreFurnaceBlock.FACING);
            server.sendParticles(ParticleTypes.FLAME, worldPosition.getX() + 0.5 + f.getStepX() * 0.45, worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5 + f.getStepZ() * 0.45, 6, 0.12, 0.1, 0.12, 0.03);
        }
        setChanged();
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, OreFurnaceBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        boolean dirty = false;
        MachineRecipes.Recipe r = be.ore.isEmpty() ? null : MachineRecipes.find(be.ore, HarvestConfig.FOUNDRY_RECIPES.get());
        boolean canSmelt = r != null && be.ore.getCount() >= r.inCount() && be.pending.isEmpty();

        if (be.burn > 0) {
            be.burn = Math.max(0, be.burn - (be.boost > 0 ? 2 : 1));
        }
        if (be.boost > 0) be.boost--;

        if (be.burn <= 0 && canSmelt && !be.fuel.isEmpty()) {
            int time = burnTimeOf(be.fuel);
            if (time > 0) {
                be.burn = time;
                be.fuel.shrink(1);
                if (be.fuel.isEmpty()) be.fuel = ItemStack.EMPTY;
                dirty = true;
            }
        }

        if (be.burn > 0 && canSmelt) {
            be.progress += be.boost > 0 ? 2 : 1;
            if (be.progress >= HarvestConfig.SMELT_TICKS.get()) {
                be.ore.shrink(r.inCount());
                if (be.ore.isEmpty()) be.ore = ItemStack.EMPTY;
                boolean lucky = HarvestConfig.FOUNDRY_LUCK.get() > 0 && level.random.nextDouble() < HarvestConfig.FOUNDRY_LUCK.get();
                be.pending = new ItemStack(r.output(), lucky ? r.outCount() * 2 : r.outCount());
                be.progress = 0;
                if (lucky) {
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.5f);
                    server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5, 12, 0.3, 0.3, 0.3, 0.2);
                }
                dirty = true;
            }
        } else if (be.progress > 0) {
            be.progress = Math.max(0, be.progress - 2);
        }

        // coulée dans le seau de coulée posé devant le bec
        if (!be.pending.isEmpty()) {
            CastingBucketBlockEntity bucket = be.bucket(level);
            long now = level.getGameTime();
            if (bucket != null && bucket.isEmpty()) {
                if (be.pourStart < 0) {
                    be.pourStart = now;
                    dirty = true;
                }
                Direction f = state.getValue(OreFurnaceBlock.FACING);
                if (now % 10 == 0) level.playSound(null, pos, SoundEvents.LAVA_POP, SoundSource.BLOCKS, 0.6f, 0.8f);
                server.sendParticles(ParticleTypes.FLAME, pos.getX() + 0.5 + f.getStepX() * 1.0, pos.getY() + 0.35,
                        pos.getZ() + 0.5 + f.getStepZ() * 1.0, 1, 0.05, 0.02, 0.05, 0.01);
                if (now - be.pourStart >= POUR_TICKS) {
                    bucket.fill(be.pending);
                    be.pending = ItemStack.EMPTY;
                    be.pourStart = -1;
                    dirty = true;
                }
            } else if (be.pourStart >= 0) {
                be.pourStart = -1; // seau retiré ou plein : le bec se ferme
                dirty = true;
            }
        }

        boolean lit = be.burn > 0;
        if (state.getValue(OreFurnaceBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(OreFurnaceBlock.LIT, lit), Block.UPDATE_ALL);
            dirty = true;
        }
        if (dirty) be.sync(); else be.setChanged();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("ore", ore.save(new CompoundTag()));
        tag.put("fuel", fuel.save(new CompoundTag()));
        tag.put("pending", pending.save(new CompoundTag()));
        tag.putInt("burn", burn);
        tag.putInt("progress", progress);
        tag.putLong("pour", pourStart);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ore = ItemStack.of(tag.getCompound("ore"));
        fuel = ItemStack.of(tag.getCompound("fuel"));
        pending = ItemStack.of(tag.getCompound("pending"));
        burn = tag.getInt("burn");
        progress = tag.getInt("progress");
        pourStart = tag.contains("pour") ? tag.getLong("pour") : -1;
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
