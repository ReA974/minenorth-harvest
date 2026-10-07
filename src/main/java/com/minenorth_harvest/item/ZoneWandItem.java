package com.minenorth_harvest.item;

import com.minenorth_harvest.zone.ZoneSelection;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Baguette de zone : clic gauche sur un bloc = point 1, clic droit = point 2.
 * Puis /recolte zone creer <nom> <bucheron|verger|chasse>.
 * (Le clic gauche est géré dans CommonEvents via LeftClickBlock.)
 */
public class ZoneWandItem extends Item {
    public ZoneWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!ctx.getLevel().isClientSide && ctx.getPlayer() instanceof ServerPlayer sp && sp.hasPermissions(2)) {
            ZoneSelection.set(sp, ctx.getClickedPos(), false);
        }
        return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
    }

    /** Empêche de casser des blocs avec la baguette (y compris en créatif). */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.minenorth_harvest.zone_wand.tip1"));
        tooltip.add(Component.translatable("item.minenorth_harvest.zone_wand.tip2"));
        tooltip.add(Component.translatable("item.minenorth_harvest.zone_wand.tip3"));
    }
}
