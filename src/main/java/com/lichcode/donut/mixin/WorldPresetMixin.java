package com.lichcode.donut.mixin;

import com.lichcode.donut.DonutWorld;
import com.lichcode.donut.world.gen.DonutChunkGenerator;
import com.mojang.serialization.DataResult;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.WorldPreset;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldPresets.Registrar.class)
public abstract class WorldPresetMixin {
    static {
        DonutWorld.LOGGER.info("WorldPresetMixin class loaded!");
    }
    // defining our registry key. this key provides an Identifier for our preset, that we can use for our lang files and data elements.
    private static final RegistryKey<WorldPreset> DONUT_WORLD = RegistryKey.of(RegistryKeys.WORLD_PRESET, Identifier.of(DonutWorld.MOD_ID, "donut"));
    @Shadow
    private RegistryEntryLookup<ChunkGeneratorSettings> chunkGeneratorSettingsLookup;

    @Shadow
    protected abstract void register(RegistryKey<WorldPreset> key, DimensionOptions dimensionOptions);

    @Shadow
    protected abstract DimensionOptions createOverworldOptions(ChunkGenerator chunkGenerator);

    @Inject(method = "bootstrap", at = @At("RETURN"))
    private void addPresets(BiomeSource biomeSource, CallbackInfo callbackInfo) {
        // the register() method is shadowed from the target class

        DonutWorld.LOGGER.info("HERE BTW");
        RegistryEntry<ChunkGeneratorSettings> registryEntry = this.chunkGeneratorSettingsLookup.getOrThrow(ChunkGeneratorSettings.OVERWORLD);
        this.register(DONUT_WORLD, this.createOverworldOptions(new DonutChunkGenerator(biomeSource, registryEntry)));
    }
}
