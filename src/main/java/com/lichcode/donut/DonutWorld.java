package com.lichcode.donut;

import com.lichcode.donut.world.gen.DonutChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.impl.biome.modification.BuiltInRegistryKeys;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.Identifier;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DonutWorld implements ModInitializer {
	public static final String MOD_ID = "donut-world";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		LOGGER.info("Hello Fabric world!");
		Registry.register(
				Registries.CHUNK_GENERATOR,
				Identifier.of(MOD_ID, "donut"),
				DonutChunkGenerator.CODEC
		);
	}
}