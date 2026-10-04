package com.minenorth_harvest.chop;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlocks;
import com.minenorth_harvest.zone.ZoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Chute progressive d'un arbre après un abattage réussi. */
public class FellingTask {
    private enum Phase { LOGS, WAIT, LEAVES, DONE }

    private final ServerLevel level;
    @Nullable private final ServerPlayer player;
    private final List<BlockPos> logs;
    private final BlockPos base;
    private final int bonusLogs;
    private ItemStack bonusItem = ItemStack.EMPTY;

    private Phase phase = Phase.LOGS;
    private int index;
    private int wait;
    private final List<BlockPos> leafQueue = new ArrayList<>();
    private int minX, minY, minZ, maxX, maxY, maxZ;

    private final boolean bypassProtection;
    @Nullable private final String regrowSapling;

    public FellingTask(ServerLevel level, @Nullable ServerPlayer player, List<BlockPos> logs, BlockPos base, int bonusLogs,
                       boolean bypassProtection, @Nullable String regrowSapling) {
        this.level = level;
        this.bypassProtection = bypassProtection;
        this.regrowSapling = regrowSapling;
        this.player = player;
        this.logs = logs;
        this.base = base;
        this.bonusLogs = bonusLogs;
        minX = minY = minZ = Integer.MAX_VALUE;
        maxX = maxY = maxZ = Integer.MIN_VALUE;
        for (BlockPos p : logs) {
            minX = Math.min(minX, p.getX()); maxX = Math.max(maxX, p.getX());
            minY = Math.min(minY, p.getY()); maxY = Math.max(maxY, p.getY());
            minZ = Math.min(minZ, p.getZ()); maxZ = Math.max(maxZ, p.getZ());
        }
        BlockState baseState = level.getBlockState(base);
        if (baseState.is(BlockTags.LOGS)) bonusItem = new ItemStack(baseState.getBlock().asItem());
    }

    public boolean contains(BlockPos pos) {
        return phase == Phase.LOGS && logs.contains(pos);
    }

    /** @return true quand la tâche est terminée. */
    public boolean tick() {
        switch (phase) {
            case LOGS -> tickLogs();
            case WAIT -> {
                if (--wait <= 0) collectLeaves();
            }
            case LEAVES -> tickLeaves();
            case DONE -> { return true; }
        }
        return phase == Phase.DONE;
    }

    private void tickLogs() {
        int perTick = HarvestConfig.LOGS_PER_TICK.get();
        for (int i = 0; i < perTick && index < logs.size(); i++) {
            BlockPos pos = logs.get(index++);
            if (!level.isLoaded(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (!state.is(BlockTags.LOGS)) continue;
            if (!canBreak(pos, state)) continue;

            level.destroyBlock(pos, true, player);

            if (pos.equals(base) && HarvestConfig.LEAVE_STUMP.get()
                    && level.getBlockState(pos.below()).is(BlockTags.DIRT)
                    && level.getBlockState(pos).isAir()) {
                level.setBlock(pos, ModBlocks.TREE_STUMP.get().defaultBlockState(), Block.UPDATE_ALL);
                if (regrowSapling != null) {
                    ZoneData.get(level).addRegrow(pos, regrowSapling,
                            level.getGameTime() + HarvestConfig.REGROW_DELAY.get() * 20L);
                }
            }
        }
        if (index >= logs.size()) {
            if (bonusLogs > 0 && !bonusItem.isEmpty()) {
                ItemStack drop = bonusItem.copy();
                drop.setCount(bonusLogs);
                Block.popResource(level, base.above(), drop);
            }
            level.playSound(null, base, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
            if (HarvestConfig.FAST_LEAF_DECAY.get()) {
                phase = Phase.WAIT;
                wait = 30; // laisse le temps aux feuilles de recalculer leur distance
            } else {
                phase = Phase.DONE;
            }
        }
    }

    private void collectLeaves() {
        int r = 4;
        for (BlockPos p : BlockPos.betweenClosed(minX - r, minY, minZ - r, maxX + r, maxY + r, maxZ + r)) {
            if (!level.isLoaded(p)) continue;
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT)
                    && !s.getValue(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.DISTANCE) == 7) {
                leafQueue.add(p.immutable());
            }
        }
        index = 0;
        phase = leafQueue.isEmpty() ? Phase.DONE : Phase.LEAVES;
    }

    private void tickLeaves() {
        for (int i = 0; i < 24 && index < leafQueue.size(); i++) {
            BlockPos p = leafQueue.get(index++);
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.DISTANCE) == 7) {
                level.destroyBlock(p, true);
            }
        }
        if (index >= leafQueue.size()) phase = Phase.DONE;
    }

    /** Respecte les protections (claims, etc.) qui écoutent BlockEvent.BreakEvent. */
    private boolean canBreak(BlockPos pos, BlockState state) {
        if (bypassProtection || player == null || player.isRemoved()) return true;
        return !MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, state, player));
    }
}
