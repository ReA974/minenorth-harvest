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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

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

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack st = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer p) {
            if (player.isShiftKeyDown()) PowerTool.refill(p, st);
            else p.displayClientMessage(Component.literal("§7Essence : §f" + PowerTool.fuel(st) + " / " + PowerTool.CAPACITY), true);
        }
        return InteractionResultHolder.success(st);
    }

    @Override public UseAnim getUseAnimation(ItemStack st) { return UseAnim.NONE; }

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
        tip.add(Component.literal("§8Accroupi + clic droit : plein d'essence"));
    }
}
