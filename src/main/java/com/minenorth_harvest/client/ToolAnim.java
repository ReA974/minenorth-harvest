package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.item.PowerTool;
import com.minenorth_harvest.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;

/**
 * Animation des outils à moteur : la propriété d'objet « minenorth_harvest:anim » vaut 0 au repos, puis 0.25 / 0.5 / 0.75 / 1.0 selon l'image
 * (change toutes les 2 ticks) tant que le moteur tourne. Les modèles chainsaw / jackhammer choisissent l'image correspondante (overrides) :
 * chaîne qui défile et corps qui vibre, piston qui martèle. Joueur local : clic gauche maintenu sur un bloc ou clic droit maintenu ;
 * autres joueurs : bras qui balance ou objet en cours d'utilisation.
 */
public final class ToolAnim {
    private ToolAnim() {}

    public static final ResourceLocation KEY = new ResourceLocation(MineNorthHarvest.MODID, "anim");

    public static void register() {
        ItemProperties.register(ModItems.CHAINSAW.get(), KEY, ToolAnim::value);
        ItemProperties.register(ModItems.JACKHAMMER.get(), KEY, ToolAnim::value);
    }

    private static float value(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        if (entity == null || PowerTool.fuel(stack) <= 0) return 0f;
        boolean holding = entity.getMainHandItem().getItem() == stack.getItem() || entity.getOffhandItem().getItem() == stack.getItem();
        if (!holding) return 0f;
        boolean active = entity.isUsingItem() && entity.getUseItem().getItem() == stack.getItem();
        if (!active && entity.swinging) active = true;
        Minecraft mc = Minecraft.getInstance();
        if (!active && entity == mc.player && mc.options.keyAttack.isDown() && mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) active = true;
        if (!active) return 0f;
        int frame = (entity.tickCount / 2) % 4;
        return (frame + 1) * 0.25f;
    }
}
