package com.minenorth_harvest.registry;

import com.minenorth_harvest.MineNorthHarvest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Sons des outils à moteur (synthétisés par tools/gen_tool_sounds.py) ; joués côté client par ToolSounds. */
public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MineNorthHarvest.MODID);

    private static RegistryObject<SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MineNorthHarvest.MODID, name)));
    }

    public static final RegistryObject<SoundEvent> JACKHAMMER_START = reg("jackhammer_start"), JACKHAMMER_LOOP = reg("jackhammer_loop"), JACKHAMMER_END = reg("jackhammer_end"),
            CHAINSAW_START = reg("chainsaw_start"), CHAINSAW_IDLE = reg("chainsaw_idle"), CHAINSAW_CUT = reg("chainsaw_cut"), CHAINSAW_STOP = reg("chainsaw_stop");
}
