package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Moulin : blé (ou autre entrée de la config) en main, maintenir clic droit pour tourner la meule.
 * Après X tours, l'entrée est consommée et le produit (farine…) va dans l'inventaire.
 * Recettes dans la config, section [mill] : "minecraft:wheat*3=minenorth_harvest:flour*1".
 */
public class MillBlock extends Block {
    public static final VoxelShape MILL_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 8, 16),
            Block.box(2, 8, 2, 14, 14, 14),
            Block.box(7, 14, 7, 9, 16, 9));

    /** Paramètres propres à chaque machine (moulin, raffinerie). */
    public record Machine(VoxelShape shape, Supplier<List<? extends String>> recipes, Supplier<Integer> turns,
                          String hintKey, String progressKey, SoundEvent sound, ParticleOptions doneParticle) {}

    private final Machine machine;

    /** Tours de meule en cours, un par joueur (repart de zéro s'il change de moulin ou s'arrête 2 s). */
    private static final Map<UUID, Crank> CRANKS = new HashMap<>();

    private static final class Crank {
        final BlockPos pos;
        int turns;
        long last;
        Crank(BlockPos pos) { this.pos = pos; }
    }

    private record Recipe(Item input, int inCount, Item output, int outCount) {}

    public MillBlock(Properties properties) {
        this(properties, new Machine(MILL_SHAPE, HarvestConfig.MILL_RECIPES::get, HarvestConfig.MILL_TURNS::get,
                "message.minenorth_harvest.mill_hint", "message.minenorth_harvest.mill_progress", SoundEvents.GRINDSTONE_USE, ParticleTypes.WHITE_ASH));
    }

    public MillBlock(Properties properties, Machine machine) {
        super(properties);
        this.machine = machine;
    }

    /** Se ramasse tel quel (pas de loot table : le dossier serait trop profond pour l'outil de synchro). */
    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return machine.shape();
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        ItemStack held = player.getItemInHand(hand);
        Recipe recipe = find(held, machine.recipes().get());
        if (recipe == null) {
            if (!held.isEmpty()) return InteractionResult.PASS;
            if (!level.isClientSide) player.displayClientMessage(Component.translatable(machine.hintKey()), true);
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        if (held.getCount() < recipe.inCount()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.mill_need",
                    recipe.inCount(), recipe.input().getDescription()), true);
            return InteractionResult.CONSUME;
        }

        long now = level.getGameTime();
        Crank c = CRANKS.get(player.getUUID());
        if (c == null || !c.pos.equals(pos) || now - c.last > 40) {
            c = new Crank(pos.immutable());
            CRANKS.put(player.getUUID(), c);
        } else if (now - c.last < HarvestConfig.MILL_COOLDOWN.get()) {
            return InteractionResult.CONSUME;
        }
        c.last = now;
        c.turns++;

        ServerLevel server = (ServerLevel) level;
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, held.copyWithCount(1)),
                pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 3, 0.25, 0.05, 0.25, 0.03);
        if (c.turns % 3 == 1) {
            level.playSound(null, pos, machine.sound(), SoundSource.BLOCKS, 0.5f, 0.8f + level.getRandom().nextFloat() * 0.3f);
        }

        int need = machine.turns().get();
        if (c.turns < need) {
            player.displayClientMessage(Component.translatable(machine.progressKey(), bar(c.turns, need)), true);
            return InteractionResult.CONSUME;
        }

        c.turns = 0;
        if (!player.getAbilities().instabuild) held.shrink(recipe.inCount());
        ItemStack out = new ItemStack(recipe.output(), recipe.outCount());
        Component name = out.getHoverName();
        if (!player.getInventory().add(out)) player.drop(out, false);
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.2f);
        server.sendParticles(machine.doneParticle(), pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 12, 0.3, 0.2, 0.3, 0.01);
        player.displayClientMessage(Component.translatable("message.minenorth_harvest.mill_done", recipe.outCount(), name), true);
        return InteractionResult.CONSUME;
    }

    private static String bar(int turns, int need) {
        StringBuilder sb = new StringBuilder("§a");
        int size = 10, filled = Math.min(size, turns * size / Math.max(1, need));
        for (int i = 0; i < size; i++) {
            if (i == filled) sb.append("§7");
            sb.append('■');
        }
        return sb.toString();
    }

    /** Recette de la config qui correspond à l'objet en main, ou null. */
    private static Recipe find(ItemStack held, List<? extends String> lines) {
        if (held.isEmpty()) return null;
        for (String line : lines) {
            Recipe r = parse(line);
            if (r != null && held.is(r.input())) return r;
        }
        return null;
    }

    /** "minecraft:wheat*3=minenorth_harvest:flour*1" (les "*n" sont facultatifs, 1 par défaut). */
    private static Recipe parse(String line) {
        String[] sides = line.split("=");
        if (sides.length != 2) return null;
        Object[] in = side(sides[0]), out = side(sides[1]);
        if (in == null || out == null) return null;
        return new Recipe((Item) in[0], (int) in[1], (Item) out[0], (int) out[1]);
    }

    private static Object[] side(String s) {
        String[] parts = s.trim().split("\\*");
        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return null;
        int count = 1;
        if (parts.length > 1) {
            try { count = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim()))); } catch (NumberFormatException e) { return null; }
        }
        return new Object[]{ForgeRegistries.ITEMS.getValue(id), count};
    }
}
