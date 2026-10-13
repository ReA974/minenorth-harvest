package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Raffinerie : brut + combustible, puis mini-jeu de PRESSION. La chaleur fait monter la pression, une fuite la fait redescendre.
 * Il faut la garder dans la zone verte (pomper au pupitre pour monter, ouvrir la vanne en accroupi pour baisser) :
 * dans la zone verte le lot avance à pleine vitesse, en dessous il avance lentement, au-dessus il s'arrête, et trop haut trop longtemps : explosion.
 */
public class RefineryBlockEntity extends BlockEntity {
    public static final int GREEN_MIN = 40, GREEN_MAX = 70, DANGER = 90;
    public static final int MAX_INPUT = 64, MAX_OUTPUT = 120, MAX_FUEL = 16;

    private ItemStack input = ItemStack.EMPTY;
    private ItemStack fuel = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;
    private int burn;
    private float pressure, progress;
    private int danger;
    private boolean working;
    private long lastPump = -1000, lastVent = -1000;
    private boolean dirty;

    public RefineryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.REFINERY.get(), pos, state);
    }

    public ItemStack input() { return input; }
    public ItemStack fuel() { return fuel; }
    public ItemStack output() { return output; }
    public int burn() { return burn; }
    public float pressure() { return pressure; }
    public float progress() { return progress; }
    public boolean working() { return working; }
    public long lastPump() { return lastPump; }
    public long lastVent() { return lastVent; }
    public Direction facing() { return getBlockState().getValue(RefineryBlock.FACING); }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).inflate(5, 5, 5);
    }

    // ------------------------------------------------------------------ actions des joueurs
    public int insertInput(ItemStack stack) {
        MachineRecipes.Recipe r = MachineRecipes.find(stack, HarvestConfig.REFINERY_RECIPES.get());
        if (r == null) return 0;
        if (!input.isEmpty() && !ItemStack.isSameItemSameTags(input, stack)) return 0;
        int moved = Math.min(MAX_INPUT - input.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        if (input.isEmpty()) input = stack.copyWithCount(moved); else input.grow(moved);
        changed();
        return moved;
    }

    public int insertFuel(ItemStack stack) {
        if (OreFurnaceBlockEntity.burnTimeOf(stack) <= 0) return 0;
        if (!fuel.isEmpty() && !ItemStack.isSameItemSameTags(fuel, stack)) return 0;
        int moved = Math.min(MAX_FUEL - fuel.getCount(), stack.getCount());
        if (moved <= 0) return 0;
        if (fuel.isEmpty()) fuel = stack.copyWithCount(moved); else fuel.grow(moved);
        changed();
        return moved;
    }

    public ItemStack takeOutput() {
        ItemStack out = output;
        output = ItemStack.EMPTY;
        changed();
        return out;
    }

    /** Coup de pompe au pupitre : la pression monte. */
    public boolean pump(Level level) {
        long now = level.getGameTime();
        if (now - lastPump < 5) return false;
        lastPump = now;
        pressure = Math.min(110f, pressure + 9f);
        level.playSound(null, worldPosition, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.5f, 0.6f + pressure / 200f);
        level.playSound(null, worldPosition, SoundEvents.BARREL_CLOSE, SoundSource.BLOCKS, 0.6f, 0.7f);
        changed();
        return true;
    }

    /** Vanne de décharge (accroupi au pupitre) : la pression retombe, avec un jet de vapeur. */
    public boolean vent(Level level) {
        long now = level.getGameTime();
        if (now - lastVent < 8) return false;
        lastVent = now;
        pressure = Math.max(0f, pressure - 16f);
        level.playSound(null, worldPosition, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7f, 1.4f);
        level.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.7f, 0.6f);
        if (level instanceof ServerLevel server) {
            Vec3 v = MultiblockUtil.worldPoint(worldPosition, facing(), 36, 8, 1);
            server.sendParticles(ParticleTypes.CLOUD, v.x, v.y, v.z, 14, 0.15, 0.1, 0.15, 0.05);
        }
        changed();
        return true;
    }

    private void changed() {
        setChanged();
        dirty = true;
    }

    // ------------------------------------------------------------------ simulation
    public static void serverTick(Level level, BlockPos pos, BlockState state, RefineryBlockEntity be) {
        if (!(level instanceof ServerLevel server)) return;
        long now = level.getGameTime();
        MachineRecipes.Recipe r = be.input.isEmpty() ? null : MachineRecipes.find(be.input, HarvestConfig.REFINERY_RECIPES.get());
        boolean canRun = r != null && be.input.getCount() >= r.inCount() && be.output.getCount() + r.outCount() * 2 <= MAX_OUTPUT
                && (be.output.isEmpty() || be.output.is(r.output()));

        if (be.burn > 0) be.burn--;
        if (canRun && be.burn <= 0 && !be.fuel.isEmpty()) {
            int t = OreFurnaceBlockEntity.burnTimeOf(be.fuel);
            if (t > 0) {
                be.burn = t;
                be.fuel.shrink(1);
                if (be.fuel.isEmpty()) be.fuel = ItemStack.EMPTY;
                be.dirty = true;
            }
        }
        boolean wasWorking = be.working;
        be.working = canRun && be.burn > 0;

        float before = be.pressure;
        if (be.working) {
            be.pressure += 0.20f;                                          // chaleur
            if (now % 30 == 0) be.pressure += (level.random.nextFloat() - 0.35f) * 6f;   // à-coups du procédé
            be.pressure -= 0.09f;                                          // fuite
        } else {
            be.pressure -= 0.5f;                                           // refroidissement
        }
        be.pressure = Math.max(0f, be.pressure);

        if (be.working) {
            float step = 100f / Math.max(20, HarvestConfig.REFINERY_BATCH_TICKS.get());
            if (be.pressure >= GREEN_MIN && be.pressure <= GREEN_MAX) be.progress += step;
            else if (be.pressure >= 15 && be.pressure < GREEN_MIN) be.progress += step * 0.25f;
            if (be.progress >= 100f) {
                be.progress = 0f;
                be.input.shrink(r.inCount());
                if (be.input.isEmpty()) be.input = ItemStack.EMPTY;
                boolean lucky = HarvestConfig.REFINERY_LUCK.get() > 0 && level.random.nextDouble() < HarvestConfig.REFINERY_LUCK.get();
                int made = lucky ? r.outCount() * 2 : r.outCount();
                if (be.output.isEmpty()) be.output = new ItemStack(r.output(), made); else be.output.grow(made);
                level.playSound(null, pos, SoundEvents.BREWING_STAND_BREW, SoundSource.BLOCKS, 1.0f, 0.8f);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.6f, 1.3f);
                if (lucky) {
                    level.playSound(null, pos, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.5f, 1.5f);
                    Vec3 top = MultiblockUtil.worldPoint(pos, be.facing(), 28, 66, 16);
                    server.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, top.x, top.y, top.z, 16, 0.4, 0.4, 0.4, 0.2);
                }
                be.dirty = true;
            }
        } else if (be.progress > 0) {
            be.progress = Math.max(0, be.progress - 0.5f);
        }

        // danger : trop de pression trop longtemps
        if (be.pressure >= DANGER) {
            be.danger++;
            if (now % 10 == 0) {
                level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.2f, 2.0f);
                Vec3 v = MultiblockUtil.worldPoint(pos, be.facing(), 36, 8, 1);
                server.sendParticles(ParticleTypes.CLOUD, v.x, v.y, v.z, 6, 0.2, 0.15, 0.2, 0.06);
            }
            if (be.pressure >= 100f && be.danger > 50) { be.explode(server); return; }
        } else {
            be.danger = Math.max(0, be.danger - 2);
        }

        // affichage de la pression aux joueurs proches
        if (be.working || be.pressure > 5) {
            if (now % 6 == 0) {
                Component msg = status(be);
                for (ServerPlayer p : server.players()) {
                    if (p.distanceToSqr(Vec3.atCenterOf(pos)) < 100) p.displayClientMessage(msg, true);
                }
            }
        }
        if (be.working != wasWorking) be.dirty = true;
        if (Math.abs(be.pressure - before) > 0.01f && now % 4 == 0) be.dirty = true;
        if (be.dirty && now % 2 == 0) {
            be.dirty = false;
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
        be.setChanged();
    }

    /** Ligne d'état (barre d'action) : pression, zone verte, avancement. */
    public static Component status(RefineryBlockEntity be) {
        int bars = 20;
        int p = Math.round(Math.min(110, be.pressure));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bars; i++) {
            float v = i * 5f;
            String col = v >= DANGER ? "§c" : (v >= GREEN_MIN && v <= GREEN_MAX) ? "§a" : "§7";
            boolean on = v < be.pressure;
            sb.append(on ? col : "§8").append('|');
        }
        String zone = be.pressure >= DANGER ? "§c§lDANGER !" : be.pressure > GREEN_MAX ? "§6trop haut : ouvrez la vanne" : be.pressure >= GREEN_MIN ? "§aZONE VERTE" : "§epompez !";
        return Component.literal("§6Raffinerie §7Pression §f" + p + " " + sb + " " + zone + " §7Lot §f" + Math.round(be.progress) + "%");
    }

    private void explode(ServerLevel server) {
        Vec3 c = MultiblockUtil.worldPoint(worldPosition, facing(), 28, 20, 16);
        float power = HarvestConfig.REFINERY_EXPLOSION.get().floatValue();
        BlockPos mp = worldPosition;
        server.removeBlock(mp, false);   // retire toute la machine (les cellules suivent)
        if (power > 0) {
            server.explode(null, c.x, c.y, c.z, power, true,
                    HarvestConfig.REFINERY_BREAKS_BLOCKS.get() ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        }
    }

    // ------------------------------------------------------------------ client : flammes, fumée, vapeur
    public static void clientTick(Level level, BlockPos pos, BlockState state, RefineryBlockEntity be) {
        if (!level.isClientSide) return;
        var rnd = level.random;
        Direction f = state.getValue(RefineryBlock.FACING);
        if (be.working || be.burn > 0) {
            if (rnd.nextInt(2) == 0) {
                Vec3 t = MultiblockUtil.worldPoint(pos, f, 42, 60, 16);
                level.addParticle(ParticleTypes.FLAME, t.x + (rnd.nextDouble() - 0.5) * 0.15, t.y + 0.1, t.z + (rnd.nextDouble() - 0.5) * 0.15, 0, 0.04, 0);
                if (rnd.nextInt(2) == 0) level.addParticle(ParticleTypes.LARGE_SMOKE, t.x, t.y + 0.5, t.z, 0, 0.05, 0);
            }
            if (rnd.nextInt(5) == 0) {
                Vec3 c = MultiblockUtil.worldPoint(pos, f, 28, 65, 16);
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, c.x, c.y, c.z, 0, 0.07, 0);
            }
        }
        if (be.pressure >= 80 && rnd.nextInt(2) == 0) {
            Vec3 v = MultiblockUtil.worldPoint(pos, f, 36, 8, 1);
            level.addParticle(ParticleTypes.CLOUD, v.x, v.y, v.z, (rnd.nextDouble() - 0.5) * 0.05, 0.03, -0.03);
        }
    }

    // ------------------------------------------------------------------ sauvegarde
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("in", input.save(new CompoundTag()));
        tag.put("fuel", fuel.save(new CompoundTag()));
        tag.put("out", output.save(new CompoundTag()));
        tag.putInt("burn", burn);
        tag.putFloat("pressure", pressure);
        tag.putFloat("progress", progress);
        tag.putBoolean("working", working);
        tag.putLong("pump", lastPump);
        tag.putLong("vent", lastVent);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        input = ItemStack.of(tag.getCompound("in"));
        fuel = ItemStack.of(tag.getCompound("fuel"));
        output = ItemStack.of(tag.getCompound("out"));
        burn = tag.getInt("burn");
        pressure = tag.getFloat("pressure");
        progress = tag.getFloat("progress");
        working = tag.getBoolean("working");
        lastPump = tag.contains("pump") ? tag.getLong("pump") : -1000;
        lastVent = tag.contains("vent") ? tag.getLong("vent") : -1000;
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
