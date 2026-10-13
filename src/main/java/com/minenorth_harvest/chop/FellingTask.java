package com.minenorth_harvest.chop;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlocks;
import com.minenorth_harvest.zone.ZoneData;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.registries.ForgeRegistries;
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
    /** Drops de toutes les bûches, regroupés puis lâchés au pied de l'arbre (évite des centaines d'entités). */
    private final List<ItemStack> drops = new ArrayList<>();

    private Phase phase = Phase.LOGS;
    private int index;
    private int wait;
    private final List<BlockPos> leafQueue = new ArrayList<>();
    private int minX, minY, minZ, maxX, maxY, maxZ;

    private boolean stumpPlaced;
    private final List<BlockPos> broken = new ArrayList<>();

    private final boolean bypassProtection;
    @Nullable private final String regrowSapling;
    @Nullable private final String dropSapling;

    public FellingTask(ServerLevel level, @Nullable ServerPlayer player, List<BlockPos> logs, BlockPos base, int bonusLogs,
                       boolean bypassProtection, @Nullable String regrowSapling, @Nullable String dropSapling) {
        this.dropSapling = dropSapling;
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

            collectDrops(state, pos);
            level.destroyBlock(pos, false, player);
            broken.add(pos);

            if (pos.equals(base)) placeStump(pos);
        }
        if (index >= logs.size()) {
            if (!stumpPlaced) placeLowestStump();
            dropCollected();
            if (bonusLogs > 0 && !bonusItem.isEmpty()) {
                ItemStack drop = bonusItem.copy();
                drop.setCount(bonusLogs);
                Block.popResource(level, base.above(), drop);
            }
            dropSapling();
            level.playSound(null, base, SoundEvents.WOOD_BREAK, SoundSource.BLOCKS, 1.2f, 0.5f);
            if (HarvestConfig.FAST_LEAF_DECAY.get()) {
                phase = Phase.WAIT;
                wait = 30; // laisse le temps aux feuilles de recalculer leur distance
            } else {
                phase = Phase.DONE;
            }
        }
    }

    /** Pose la souche (et programme la repousse en zone) à la place de la bûche de pied abattue. */
    private boolean placeStump(BlockPos pos) {
        if (stumpPlaced || !HarvestConfig.LEAVE_STUMP.get()) return false;
        if (!level.getBlockState(pos).isAir() || !canHoldStump(pos.below())) return false;
        level.setBlock(pos, ModBlocks.TREE_STUMP.get().defaultBlockState(), Block.UPDATE_ALL);
        stumpPlaced = true;
        if (regrowSapling != null) {
            ZoneData.get(level).addRegrow(pos, regrowSapling, level.getGameTime() + HarvestConfig.REGROW_DELAY.get() * 20L);
        }
        return true;
    }

    /** Filet de sécurité : si le pied « officiel » n'a pas pu porter la souche, on prend la bûche abattue la plus basse. */
    private void placeLowestStump() {
        if (!HarvestConfig.LEAVE_STUMP.get()) return;
        List<BlockPos> candidates = new ArrayList<>(broken);
        candidates.sort(java.util.Comparator.<BlockPos>comparingInt(BlockPos::getY)
                .thenComparingDouble(b -> b.distSqr(base)));
        for (BlockPos p : candidates) if (placeStump(p)) return;
        com.mojang.logging.LogUtils.getLogger().info("Abattage en {} : aucune souche posée ({} bûches cassées sur {}, pied {}, sous le pied : {}).",
                base, broken.size(), logs.size(), level.getBlockState(base), level.getBlockState(base.below()));
    }

    /**
     * Une souche tient sur de la terre, mais aussi sur n'importe quel sol plein (herbe de construction, terre
     * modifiée, dalle...) : seuls le vide, les liquides, les bûches et les feuilles sont refusés.
     */
    private boolean canHoldStump(BlockPos below) {
        BlockState s = level.getBlockState(below);
        if (s.is(BlockTags.DIRT)) return true;
        return !s.isAir() && s.getFluidState().isEmpty() && !s.is(BlockTags.LOGS) && !s.is(BlockTags.LEAVES)
                && s.isFaceSturdy(level, below, net.minecraft.core.Direction.UP);
    }

    private void collectDrops(BlockState state, BlockPos pos) {
        ItemStack tool = player != null ? player.getMainHandItem() : ItemStack.EMPTY;
        for (ItemStack stack : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tool)) {
            if (stack.isEmpty()) continue;
            ItemStack rest = stack.copy();
            for (ItemStack have : drops) {
                if (rest.isEmpty()) break;
                if (!ItemStack.isSameItemSameTags(have, rest)) continue;
                int moved = Math.min(rest.getCount(), have.getMaxStackSize() - have.getCount());
                if (moved <= 0) continue;
                have.grow(moved);
                rest.shrink(moved);
            }
            if (!rest.isEmpty()) drops.add(rest);
        }
    }

    private void dropCollected() {
        for (ItemStack stack : drops) Block.popResource(level, base.above(), stack);
        drops.clear();
    }

    /** Un arbre abattu rend toujours une pousse de son essence (fruitière ou vanilla). */
    private void dropSapling() {
        if (dropSapling == null) return;
        ResourceLocation id = ResourceLocation.tryParse(dropSapling);
        if (id == null) return;
        Item item = ForgeRegistries.ITEMS.getValue(id);
        if (item == null || item == Items.AIR) return;
        Block.popResource(level, base.above(), new ItemStack(item));
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
