package com.lichcode.donut.world.gen;


import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LadderBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class DonutChunkGenerator extends NoiseChunkGenerator {
    public static final MapCodec<DonutChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(generator -> generator.biomeSource),
                            ChunkGeneratorSettings.REGISTRY_CODEC.fieldOf("settings").forGetter(generator -> generator.settings)
                    )
                    .apply(instance, instance.stable(DonutChunkGenerator::new))
    );
    private static final BlockState AIR = Blocks.AIR.getDefaultState();
    private final RegistryEntry<ChunkGeneratorSettings> settings;

    public DonutChunkGenerator(BiomeSource biomeSource, RegistryEntry<ChunkGeneratorSettings> settings) {
        super(biomeSource, settings);
        this.settings = settings;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> getCodec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
        GenerationShapeConfig generationShapeConfig = this.settings.value().generationShapeConfig().trimHeight(chunk.getHeightLimitView());
        int i = generationShapeConfig.minimumY();
        int j = MathHelper.floorDiv(i, generationShapeConfig.verticalCellBlockCount());
        int k = MathHelper.floorDiv(generationShapeConfig.height(), generationShapeConfig.verticalCellBlockCount());
        return k <= 0 ? CompletableFuture.completedFuture(chunk) : CompletableFuture.supplyAsync(() -> {
            int l = chunk.getSectionIndex(k * generationShapeConfig.verticalCellBlockCount() - 1 + i);
            int m = chunk.getSectionIndex(i);
            Set<ChunkSection> set = Sets.<ChunkSection>newHashSet();

            for (int n = l; n >= m; n--) {
                ChunkSection chunkSection = chunk.getSection(n);
                chunkSection.lock();
                set.add(chunkSection);
            }

            Chunk var20;
            try {
                var20 = this.populateNoise(blender, structureAccessor, noiseConfig, chunk, j, k);
            } finally {
                for (ChunkSection chunkSection3 : set) {
                    chunkSection3.unlock();
                }
            }

            return var20;
        }, Util.getMainWorkerExecutor().named("wgen_fill_noise"));
    }

    private Chunk populateNoise(Blender blender, StructureAccessor structureAccessor, NoiseConfig noiseConfig, Chunk chunk, int minimumCellY, int cellHeight) {
        // Get heightmaps for tracking
        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG);
        Heightmap heightmap2 = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        ChunkPos chunkPos = chunk.getPos();
        ChunkNoiseSampler chunkNoiseSampler = chunk.getOrCreateChunkNoiseSampler(
                chunkx -> this.createChunkNoiseSampler(chunkx, structureAccessor, blender, noiseConfig)
        );
        int chunkStartX = chunkPos.getStartX();
        int chunkStartZ = chunkPos.getStartZ();

        BlockPos.Mutable mutable = new BlockPos.Mutable();
        AquiferSampler aquiferSampler = chunkNoiseSampler.getAquiferSampler();
        chunkNoiseSampler.sampleStartDensity();

        // Get cell dimensions (from original code)
        int k = chunkNoiseSampler.getHorizontalCellBlockCount();
        int l = chunkNoiseSampler.getVerticalCellBlockCount();
        int m = 16 / k; // number of horizontal cells
        int n = 16 / k;

        for(int o = 0; o < m; ++o) {
            chunkNoiseSampler.sampleEndDensity(o);

            for(int p = 0; p < n; ++p) {
                int q = chunk.countVerticalSections() - 1;
                ChunkSection chunkSection = chunk.getSection(q);

                for(int r = cellHeight - 1; r >= 0; --r) {
                    chunkNoiseSampler.onSampledCellCorners(r, p);

                    for(int s = l - 1; s >= 0; --s) {
                        int worldY = (minimumCellY + r) * l + s;
                        if (worldY > 300) continue;
                        int localY = worldY & 15; // Y coordinate within the section
                        int sectionIndex = chunk.getSectionIndex(worldY);

                        // Update chunk section if we moved to a different one
                        if (q != sectionIndex) {
                            q = sectionIndex;
                            chunkSection = chunk.getSection(sectionIndex);
                        }

                        double d = (double)s / l;
                        chunkNoiseSampler.interpolateY(worldY, d);

                        for(int w = 0; w < k; ++w) {
                            int worldX = chunkStartX + o * k + w;

                            int localX = worldX & 15; // X coordinate within the chunk (0-15)
                            double e = (double)w / k;
                            chunkNoiseSampler.interpolateX(worldX, e);

                            for(int z = 0; z < k; ++z) {
                                int worldZ = chunkStartZ + p * k + z;
                                int localZ = worldZ & 15; // Z coordinate within the chunk (0-15)
                                double f = (double)z / k;
                                chunkNoiseSampler.interpolateZ(worldZ, f);
                                BlockState blockState = chunkNoiseSampler.sampleBlockState();
                                if (blockState == null) {
                                    blockState = this.settings.value().defaultBlock();
                                }

                                BlockPos blockPos = new BlockPos(worldX, worldY, worldZ);

                                BlockState customBlockstate = getBlockState(blockState, worldX, worldY, worldZ, noiseConfig);
                                if (customBlockstate != AIR) {
                                    // Place the block using local coordinates
                                    chunkSection.setBlockState(localX, localY, localZ, customBlockstate, false);
                                    heightmap.trackUpdate(localX, worldY, localZ, customBlockstate);
                                    heightmap2.trackUpdate(localX, worldY, localZ, customBlockstate);
                                } else if (blockState != AIR && structureAccessor.hasStructureReferences(blockPos)) {
                                    Map<Structure, LongSet> structures = structureAccessor.getStructureReferences(blockPos);
                                    for (Structure structure : structures.keySet()) {
                                        ChunkSectionPos sectionPos = ChunkSectionPos.from(blockPos);
                                        StructureStart structureStart = structureAccessor.getStructureStarts(sectionPos, structure).getFirst();
                                        if (structureStart != null && structureAccessor.structureContains(blockPos, structureStart)) {
                                            chunkSection.setBlockState(localX, localY, localZ, blockState, false);
                                            heightmap.trackUpdate(localX, worldY, localZ, blockState);
                                            heightmap2.trackUpdate(localX, worldY, localZ, blockState);
                                            if (aquiferSampler.needsFluidTick() && !blockState.getFluidState().isEmpty()) {
                                                mutable.set(worldX, worldY, worldZ);
                                                chunk.markBlockForPostProcessing(mutable);
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return chunk;
   }

   public BlockState getBlockState(BlockState defaultBlockState, int worldX, int worldY, int worldZ, NoiseConfig noiseConfig) {
       double outterRadius = 40;
       int initX = worldX + (int) (outterRadius * 1.5);
       int initY = worldY;
       int initZ = worldZ + (int) (outterRadius * 1.5);
       int spacingX = 150;
       int x = Math.abs(initX) % spacingX - spacingX/2;

       int spacingY = 115;
       int y = Math.abs(initY) % spacingY - spacingY/2;

       int spacingZ = 150;
       int z = Math.abs(initZ) % spacingZ - spacingZ/2;

       int indexX = Math.floorDiv(initX, spacingX);
       int indexY = Math.floorDiv(initY, spacingY);
       int indexZ = Math.floorDiv(initZ, spacingZ);

       // Calculate center positions
       int centerX = indexX * spacingX;
       int centerY = indexY * spacingY;
       int centerZ = indexZ * spacingZ;

       Random source = noiseConfig.getOrCreateRandomDeriver(Identifier.ofVanilla("terrain")).split(centerX, centerY, centerZ);
       int angleX = source.nextBetween(0, 180);
       int angleY = source.nextBetween(0, 180);
       int angleZ = source.nextBetween(0, 180);

       Vec3i newPos = rotatePoint(x, y, z, angleX, angleY, angleZ);
       x = newPos.getX();
       y = newPos.getY();
       z = newPos.getZ();
       double innerRadius = 20;

       double distanceFromZ = Math.sqrt(x*x + y*y);
       double f = Math.pow(distanceFromZ - outterRadius, 2) + z*z - innerRadius*innerRadius;

       boolean bridgeX = centerX == worldX-15;
       boolean bridgeZ = centerZ == worldZ-15;
       boolean bridgeY = centerY == initY - spacingY/2;

       boolean isBridge = ((bridgeX || bridgeZ) && bridgeY) || (bridgeX && bridgeZ);
       boolean shouldPlace = f <= 0 || isBridge;

       boolean ladder = (centerX + 1 == worldX-15 && bridgeZ);

       boolean xTorch = centerX - 2 == worldX-15 || centerX + 2 == worldX-15;
       boolean zTorch = centerZ - 2 == worldZ-15 || centerZ + 2 == worldZ-15;
       boolean torch = ((xTorch && bridgeZ) || (zTorch && bridgeX)) && centerY+1 == initY - spacingY/2;

       if (ladder) {
           return Blocks.LADDER.getDefaultState().with(LadderBlock.FACING, Direction.EAST);
       } else if (shouldPlace) {
           if (defaultBlockState == AIR) {
               return this.settings.value().defaultBlock();
           }

           return defaultBlockState;
       } else if (torch) {
           return Blocks.TORCH.getDefaultState();
       }

       return AIR;
   }

    private Vec3i rotatePoint(int x, int y, int z, int angleX, int angleY, int angleZ)  {
        // Rotate around X
        double cosX = Math.cos(Math.toRadians(angleX));
        double sinX = Math.sin(Math.toRadians(angleX));
        double newY = y*cosX - z*sinX;
        double newZ = y*sinX + z*cosX;

        // Rotate around Y
        double cosY = Math.cos(Math.toRadians(angleY));
        double sinY =  Math.sin(Math.toRadians(angleY));
        double newX = x*cosY + newZ*sinY;
        newZ = -x*sinY + newZ*cosY;

        // Rotate around Z
        double cosZ = Math.cos(Math.toRadians(angleZ));
        double sinZ = Math.sin(Math.toRadians(angleZ));
        double prevX = newX;
        newX = newX*cosZ - newY*sinZ;
        newY = prevX*sinZ + newY*cosZ;

        return new Vec3i((int) newX, (int) newY, (int) newZ);
    }
}
