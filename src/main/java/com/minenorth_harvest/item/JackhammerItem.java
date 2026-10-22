package com.minenorth_harvest.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
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

/** Marteau-piqueur : pioche à moteur (essence), très rapide sur la roche et les minerais. Accroupi + clic droit = faire le plein. */
public class JackhammerItem extends PickaxeItem {
    public JackhammerItem(Properties props) { super(Tiers.DIAMOND, 1, -2.6f, props.stacksTo(1)); }

    @Override
    public float getDestroySpeed(ItemStack st, BlockState state) {
        float s = super.getDestroySpeed(st, state);
        return PowerTool.fuel(st) > 0 ? s * 1.8f : Math.min(s, 1.0f);
    }

    @Override
    public boolean mineBlock(ItemStack st, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer p && state.getDestroySpeed(level, pos) != 0) PowerTool.consume(p, st, PowerTool.PER_BLOCK);
        return super.mineBlock(st, level, state, pos, entity);
    }

    /** Perçage en cours par joueur : bloc visé et progression (1.0 = cassé). */
    private static final class Drill {
        BlockPos pos; float progress; int stage = -1;
        Drill(BlockPos pos) { this.pos = pos; }
    }
    private static final Map<UUID, Drill> DRILLS = new HashMap<>();
    private static final double REACH = 4.5;

    /** Déconnexion : le perçage en cours n'a plus de joueur. */
    public static void forget(UUID id) { DRILLS.remove(id); }

    @Nullable
    private static BlockPos aimedBlock(Player p) {
        Vec3 eye = p.getEyePosition();
        BlockHitResult hit = p.level().clip(new ClipContext(eye, eye.add(p.getViewVector(1f).scale(REACH)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    /**
     * Clic droit MAINTENU : perce le bloc visé (vitesse de l'outil), puis enchaîne sur le suivant tant que le clic est maintenu.
     * Les protections et zones passent par la casse normale. Accroupi + clic droit = faire le plein.
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
        if (!level.isClientSide) DRILLS.remove(player.getUUID());
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override public int getUseDuration(ItemStack st) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.NONE; }

    @Override
    public void releaseUsing(ItemStack st, Level level, LivingEntity entity, int timeLeft) {
        if (level.isClientSide) return;
        Drill d = DRILLS.remove(entity.getUUID());
        if (d != null && level instanceof ServerLevel sl) sl.destroyBlockProgress(entity.getId(), d.pos, -1);
    }

    /** Moteur en marche : martèlement et poussière, 1 unité d'essence par seconde, progression sur le bloc visé. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack st, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p) || !(level instanceof ServerLevel sl)) return;
        PowerTool.fx(p, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundEvents.STONE_HIT, 0.8f, ParticleTypes.CRIT);
        if (level.getGameTime() % 20 == 0 && !PowerTool.consume(p, st, PowerTool.PER_SECOND)) { p.stopUsingItem(); return; }
        BlockPos pos = aimedBlock(p);
        Drill d = DRILLS.get(p.getUUID());
        if (pos == null || level.getBlockState(pos).isAir() || level.getBlockState(pos).getDestroySpeed(level, pos) < 0) {
            if (d != null) { sl.destroyBlockProgress(p.getId(), d.pos, -1); DRILLS.remove(p.getUUID()); }
            return;
        }
        if (d == null || !d.pos.equals(pos)) {
            if (d != null) sl.destroyBlockProgress(p.getId(), d.pos, -1);
            d = new Drill(pos.immutable());
            DRILLS.put(p.getUUID(), d);
        }
        BlockState state = level.getBlockState(pos);
        // Vitesse propre au marteau-piqueur (indépendante de la main, des effets et des autres mods) : pioche = rapide, reste = lent.
        float hardness = state.getDestroySpeed(level, pos);
        float speed = state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) ? 14f : 3f;
        d.progress += speed / Math.max(0.2f, hardness) / 30f;
        int stage = Math.min(9, (int) (d.progress * 10f));
        if (stage != d.stage) { d.stage = stage; sl.destroyBlockProgress(p.getId(), pos, stage); }
        if (d.progress >= 1f) {
            sl.destroyBlockProgress(p.getId(), pos, -1);
            DRILLS.remove(p.getUUID());
            if (!breakWithDrops(sl, p, st, pos)) {
                p.displayClientMessage(Component.literal("§cCe bloc est protégé ici."), true);
                p.stopUsingItem();
            }
        }
    }

    /**
     * Casse le bloc comme un joueur : événement de casse (protections, zones), consommation d'essence (mineBlock), puis drops calculés avec le marteau
     * comme outil et déposés explicitement. Le chemin vanilla (gameMode.destroyBlock) cassait le bloc sans rien donner quand l'outil est en cours d'utilisation.
     */
    private static boolean breakWithDrops(ServerLevel sl, ServerPlayer p, ItemStack tool, BlockPos pos) {
        BlockState state = sl.getBlockState(pos);
        if (com.minenorth_harvest.zone.MineBreak.isMineOre(sl, pos, state)) return com.minenorth_harvest.zone.MineBreak.handle(sl, p, pos, state, tool, "marteau-piqueur");
        if (net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.level.BlockEvent.BreakEvent(sl, pos, state, p))) return sl.getBlockState(pos).isAir();   // annulé mais bloc déjà cassé (minerai de zone mine) = traité
        net.minecraft.world.level.block.entity.BlockEntity be = sl.getBlockEntity(pos);
        boolean drop = !p.isCreative() && (!state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state));
        List<ItemStack> drops = drop ? net.minecraft.world.level.block.Block.getDrops(state, sl, pos, be, p, tool) : List.of();
        tool.mineBlock(sl, state, pos, p);
        if (!sl.removeBlock(pos, false)) return true;
        state.getBlock().destroy(sl, pos, state);
        for (ItemStack d : drops) net.minecraft.world.level.block.Block.popResource(sl, pos, d);
        state.spawnAfterBreak(sl, pos, tool, true);
        sl.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
        return true;
    }

    /** Pendant qu'on perce (clic gauche maintenu, bras qui balance) : martèlement, poussière, 1 unité d'essence par seconde. */
    @Override
    public void inventoryTick(ItemStack st, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !selected || !(entity instanceof ServerPlayer p) || !p.swinging || PowerTool.fuel(st) <= 0) return;
        PowerTool.fx(p, SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundEvents.STONE_HIT, 0.8f, ParticleTypes.CRIT);
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
        tip.add(Component.literal("§8Casse la roche et les minerais très vite"));
        tip.add(Component.literal("§8Clic droit MAINTENU sur un bloc : il perce, puis enchaîne"));
        tip.add(Component.literal("§8Accroupi + clic droit : plein d'essence"));
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.minecraftforge.client.extensions.common.IClientItemExtensions> consumer) {
        consumer.accept(com.minenorth_harvest.client.ToolPoses.extension(com.minenorth_harvest.client.ToolPoses.JACKHAMMER));
    }
}
