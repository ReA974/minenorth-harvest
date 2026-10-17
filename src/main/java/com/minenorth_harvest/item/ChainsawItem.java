package com.minenorth_harvest.item;

import com.minenorth_harvest.chop.ChopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tronçonneuse : hache à moteur (essence). Sur un arbre d'une zone de bûcheronnage, elle l'abat d'un coup SANS mini-jeu (voir
 * ChopManager.fellWithChainsaw) ; ailleurs elle coupe vite les blocs de bois. Clic droit = lancer le moteur, accroupi + clic droit = faire le plein.
 */
public class ChainsawItem extends AxeItem {
    public ChainsawItem(Properties props) { super(Tiers.DIAMOND, 5.0f, -2.8f, props.stacksTo(1)); }

    @Override
    public float getDestroySpeed(ItemStack st, BlockState state) {
        float s = super.getDestroySpeed(st, state);
        return PowerTool.fuel(st) > 0 ? s * 1.5f : Math.min(s, 1.0f);
    }

    @Override
    public boolean mineBlock(ItemStack st, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer p && state.getDestroySpeed(level, pos) != 0) PowerTool.consume(p, st, PowerTool.PER_BLOCK);
        return super.mineBlock(st, level, state, pos, entity);
    }

    /** Abattage en cours par joueur : bûche visée au départ et durée nécessaire (ticks). */
    private record Job(BlockPos pos, int needed) {}
    private static final Map<UUID, Job> JOBS = new HashMap<>();
    private static final double REACH = 4.5;

    /** Déconnexion : l'abattage en cours n'a plus de joueur. */
    public static void forget(UUID id) { JOBS.remove(id); }

    /** Bûche visée à portée, sinon null. */
    @Nullable
    private static BlockPos aimedLog(Player p) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getViewVector(1f).scale(REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        if (hit.getType() != HitResult.Type.BLOCK) return null;
        return p.level().getBlockState(hit.getBlockPos()).is(BlockTags.LOGS) ? hit.getBlockPos() : null;
    }

    /**
     * Clic droit MAINTENU : face à un arbre naturel d'une zone de bûcheronnage, le moteur attaque le tronc (jauge en %) et l'arbre tombe d'un coup
     * à la fin, sans mini-jeu ; ailleurs le moteur tourne seulement. Accroupi + clic droit = faire le plein.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide && player instanceof ServerPlayer p) PowerTool.refill(p, st);
            return InteractionResultHolder.success(st);
        }
        if (PowerTool.fuel(st) <= 0) {
            if (!level.isClientSide) player.displayClientMessage(Component.literal("§cRéservoir vide : accroupi + clic droit avec de l'essence dans l'inventaire."), true);
            return InteractionResultHolder.fail(st);
        }
        BlockPos log = aimedLog(player);
        if (!level.isClientSide && player instanceof ServerPlayer p) {
            JOBS.remove(p.getUUID());
            if (log != null) {
                int need = ChopManager.chainsawNeeded(p, log);
                if (need < 0) return InteractionResultHolder.fail(st);
                if (need > 0) JOBS.put(p.getUUID(), new Job(log.immutable(), need));
            }
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override public int getUseDuration(ItemStack st) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.NONE; }

    @Override
    public void releaseUsing(ItemStack st, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide) JOBS.remove(entity.getUUID());
    }

    /** Moteur en marche : bruit, fumée, 1 unité d'essence par seconde ; si un tronc est attaqué, progression puis chute de l'arbre. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack st, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) return;
        PowerTool.fx(p, SoundEvents.NOTE_BLOCK_BASS.value(), null, 0.55f, ParticleTypes.SMOKE);
        if (level.getGameTime() % 20 == 0 && !PowerTool.consume(p, st, PowerTool.PER_SECOND)) { JOBS.remove(p.getUUID()); p.stopUsingItem(); return; }
        Job job = JOBS.get(p.getUUID());
        if (job == null) return;
        BlockPos aimed = aimedLog(p);
        if (aimed == null || aimed.distSqr(job.pos()) > 36) {
            JOBS.remove(p.getUUID());
            p.stopUsingItem();
            p.displayClientMessage(Component.literal("§cAbattage interrompu : gardez le tronc en visée."), true);
            return;
        }
        int used = getUseDuration(st) - remaining;
        if (used % 5 == 0) {
            PowerTool.fx(p, SoundEvents.CHAIN_HIT, SoundEvents.AXE_STRIP, 0.8f, ParticleTypes.CRIT);
            p.displayClientMessage(Component.literal("§6Tronçonnage… §f" + Math.min(100, used * 100 / job.needed()) + " %"), true);
        }
        if (used >= job.needed()) {
            JOBS.remove(p.getUUID());
            p.stopUsingItem();
            ChopManager.fellWithChainsaw(p, job.pos());
            p.getCooldowns().addCooldown(this, 20);
        }
    }

    /** Pendant qu'on coupe (clic gauche maintenu, bras qui balance) : même ambiance et même consommation. */
    @Override
    public void inventoryTick(ItemStack st, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !selected || !(entity instanceof ServerPlayer p) || !p.swinging || p.isUsingItem() || PowerTool.fuel(st) <= 0) return;
        PowerTool.fx(p, SoundEvents.NOTE_BLOCK_BASS.value(), SoundEvents.CHAIN_HIT, 0.5f, ParticleTypes.SMOKE);
        if (level.getGameTime() % 20 == 0) PowerTool.consume(p, st, PowerTool.PER_SECOND);
    }

    @Override
    public boolean isBarVisible(ItemStack st) { return true; }

    @Override
    public int getBarWidth(ItemStack st) { return Math.round(13f * PowerTool.fuel(st) / PowerTool.CAPACITY); }

    @Override
    public int getBarColor(ItemStack st) { return 0xFF9A1E; }

    @Override
    public void appendHoverText(ItemStack st, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.literal("§7Essence : §f" + PowerTool.fuel(st) + " / " + PowerTool.CAPACITY));
        tip.add(Component.literal("§8Clic droit MAINTENU sur un arbre (zone de bûcheronnage) : il tombe d'un coup, sans mini-jeu"));
        tip.add(Component.literal("§8Clic droit ailleurs : moteur · accroupi + clic droit : plein d'essence"));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.minenorth_harvest.client.ToolPoses.extension(com.minenorth_harvest.client.ToolPoses.CHAINSAW));
    }
}
