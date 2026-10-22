package com.minenorth_harvest.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * Poses à deux mains pour la tronçonneuse et le marteau-piqueur (vue à la 3e personne et pour les autres joueurs). La pose est appliquée à
 * l'un des bras ; elle règle les deux bras et se réaffecte au bras gauche pour que la pose « vide » du minecraft ne la défasse pas.
 */
public final class ToolPoses {
    private ToolPoses() {}

    /** Marteau-piqueur : les deux mains sur les poignées, devant la taille, l'outil pointé vers le sol. */
    public static final HumanoidModel.ArmPose JACKHAMMER = pose("minenorth_harvest_jackhammer", -0.95f, -0.17f, -0.95f, 0.26f);
    /** Tronçonneuse : une main sur la poignée arrière, l'autre sur la poignée haute, devant soi. */
    public static final HumanoidModel.ArmPose CHAINSAW = pose("minenorth_harvest_chainsaw", -1.2f, -0.15f, -1.25f, 0.55f);

    private static HumanoidModel.ArmPose pose(String name, float rx, float ry, float lx, float ly) {
        HumanoidModel.ArmPose[] self = new HumanoidModel.ArmPose[1];
        self[0] = HumanoidModel.ArmPose.create(name, true, (model, entity, arm) -> {
            boolean mainRight = arm == HumanoidArm.RIGHT;
            // le bras qui tient l'outil et l'autre : mêmes valeurs quel que soit le côté dominant
            ModelPart hold = mainRight ? model.rightArm : model.leftArm;
            ModelPart other = mainRight ? model.leftArm : model.rightArm;
            float side = mainRight ? 1f : -1f;
            hold.xRot = rx; hold.yRot = ry * side; hold.zRot = 0f;
            other.xRot = lx; other.yRot = ly * side; other.zRot = 0f;
            model.rightArmPose = self[0];
            model.leftArmPose = self[0];
        });
        return self[0];
    }

    /** Extension d'objet côté client : la même pose quelle que soit la main. */
    public static IClientItemExtensions extension(HumanoidModel.ArmPose pose) {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) { return pose; }
        };
    }
}
