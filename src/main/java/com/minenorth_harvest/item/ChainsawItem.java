package com.minenorth_harvest.item;

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
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

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
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(st);
    }

    @Override public int getUseDuration(ItemStack st) { return 72000; }
    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.NONE; }

    /** Moteur lancé (clic droit maintenu) : bruit de moteur, fumée, 1 unité d'essence par seconde. */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack st, int remaining) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p)) return;
        PowerTool.fx(p, SoundEvents.NOTE_BLOCK_BASS.value(), null, 0.55f, ParticleTypes.SMOKE);
        if (level.getGameTime() % 20 == 0 && !PowerTool.consume(p, st, PowerTool.PER_SECOND)) p.stopUsingItem();
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
        tip.add(Component.literal("§8Coupe un arbre d'un coup en zone de bûcheronnage (pas de mini-jeu)"));
        tip.add(Component.literal("§8Clic droit : moteur · accroupi + clic droit : plein d'essence"));
    }
}
