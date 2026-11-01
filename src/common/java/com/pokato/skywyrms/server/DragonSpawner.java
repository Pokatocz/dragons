package com.pokato.skywyrms.server;

import com.pokato.skywyrms.config.ModConfig;
import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.entity.DragonGenes;
import com.pokato.skywyrms.entity.DragonKind;
import com.pokato.skywyrms.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
//#if MC >= 12102
import net.minecraft.world.entity.EntitySpawnReason;
//#else
import net.minecraft.world.entity.MobSpawnType;
//#endif
//#if MC < 12111
import net.minecraft.world.level.GameRules;
//#endif
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
//#if MC >= 12111
import net.minecraft.world.level.gamerules.GameRules;
//#endif
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;

public final class DragonSpawner {
   private DragonSpawner() {
   }

   public static void tick(MinecraftServer server) {
      ModConfig config = ModConfig.get();
      if (config.naturalSpawning && server.getTickCount() % config.spawnIntervalTicks == 0) {
         for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isSpectator()) {
               //#if MC >= 12106
               ServerLevel level = player.level();
               //#else
               ServerLevel level = (ServerLevel)player.level();
               //#endif
               if (mobSpawning(level) && !(level.getRandom().nextFloat() >= config.spawnChance)) {
                  trySpawn(level, player, config);
               }
            }
         }
      }
   }

   private static boolean mobSpawning(ServerLevel level) {
      //#if MC >= 12111
      return (Boolean)level.getGameRules().get(GameRules.SPAWN_MOBS);
      //#else
      return level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
      //#endif
   }

   public static DragonEntity trySpawn(ServerLevel level, ServerPlayer player, ModConfig config) {
      RandomSource random = level.getRandom();
      double angle = random.nextDouble() * Math.PI * 2.0;
      double distance = 40.0 + random.nextDouble() * 50.0;
      int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
      int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
      if (!level.hasChunkAt(new BlockPos(x, 0, z))) {
         return null;
      } else {
         int y;
         if (level.dimensionType().hasCeiling()) {
            y = findFloor(level, x, Mth.floor(player.getY()), z);
         } else {
            y = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         }

         //#if MC >= 12102
         if (y > level.getMinY() + 1 && y < level.getMaxY() - 8) {
         //#else
         if (y > level.getMinBuildHeight() + 1 && y < level.getMaxBuildHeight() - 8) {
         //#endif
            BlockPos pos = new BlockPos(x, y, z);
            List<DragonKind> options = kindsFor(level.getBiome(pos));
            if (options.isEmpty()) {
               return null;
            } else {
               DragonKind kind = options.get(random.nextInt(options.size()));
               DragonKind blood = kind;
               if (options.size() > 1 && random.nextFloat() < config.wildHybridChance) {
                  List<DragonKind> others = new ArrayList<>(options);
                  others.remove(kind);
                  blood = others.get(random.nextInt(others.size()));
               }

               if (!level.getFluidState(pos.below()).isEmpty() && kind != DragonKind.TIDE) {
                  return null;
               } else {
                  double spacing = config.spawnSpacing;
                  if (!level.getEntitiesOfClass(DragonEntity.class, new AABB(pos).inflate(spacing, 96.0, spacing)).isEmpty()) {
                     return null;
                  } else {
                     //#if MC >= 12102
                     DragonEntity dragon = (DragonEntity)ModEntities.type(kind).create(level, EntitySpawnReason.NATURAL);
                     //#else
                     DragonEntity dragon = (DragonEntity)ModEntities.type(kind).create(level);
                     //#endif
                     if (dragon == null) {
                        return null;
                     } else {
                        //#if MC >= 12105
                        dragon.snapTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
                        //#else
                        dragon.moveTo(x + 0.5, y, z + 0.5, random.nextFloat() * 360.0F, 0.0F);
                        //#endif
                        if (!level.noCollision(dragon)) {
                           return null;
                        } else {
                           dragon.setGenes(DragonGenes.wild(kind, blood, random), true);
                           //#if MC >= 12102
                           dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
                           //#elif MC >= 12005 && MC < 12102
                           dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
                           //#else
                           dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
                           //#endif
                           return level.addFreshEntity(dragon) ? dragon : null;
                        }
                     }
                  }
               }
            }
         } else {
            return null;
         }
      }
   }

   private static int findFloor(ServerLevel level, int x, int around, int z) {
      MutableBlockPos cursor = new MutableBlockPos();

      //#if MC >= 12102
      for (int y = Math.min(around + 20, level.getMaxY() - 8); y > Math.max(around - 30, level.getMinY() + 2); y--) {
      //#else
      for (int y = Math.min(around + 20, level.getMaxBuildHeight() - 8); y > Math.max(around - 30, level.getMinBuildHeight() + 2); y--) {
      //#endif
         cursor.set(x, y - 1, z);
         if (level.getBlockState(cursor).isSolid()) {
            boolean room = true;

            for (int up = 0; up < 5 && room; up++) {
               cursor.set(x, y + up, z);
               room = level.getBlockState(cursor).isAir();
            }

            if (room) {
               return y;
            }
         }
      }

      return Integer.MIN_VALUE;
   }

   public static DragonKind kindFor(Holder<Biome> biome, RandomSource random) {
      List<DragonKind> options = kindsFor(biome);
      return options.isEmpty() ? null : options.get(random.nextInt(options.size()));
   }

   public static List<DragonKind> kindsFor(Holder<Biome> biome) {
      if (biome.is(BiomeTags.IS_END)) {
         return List.of(DragonKind.VOID);
      } else if (biome.is(BiomeTags.IS_NETHER)) {
         return List.of(DragonKind.EMBER);
      } else {
         List<DragonKind> options = new ArrayList<>(3);
         if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA) || biome.is(Biomes.DESERT)) {
            options.add(DragonKind.EMBER);
         }

         boolean cold = biome.is(BiomeTags.SPAWNS_SNOW_FOXES) || biome.is(BiomeTags.SPAWNS_COLD_VARIANT_FROGS);
         if (cold) {
            options.add(DragonKind.FROST);
         }

         if (biome.is(BiomeTags.IS_MOUNTAIN)
            || biome.is(BiomeTags.IS_HILL)
            || biome.is(Biomes.PLAINS)
            || biome.is(Biomes.SUNFLOWER_PLAINS)
            || biome.is(Biomes.MEADOW)) {
            options.add(DragonKind.STORM);
         }

         if (!cold
            && (
               biome.is(BiomeTags.IS_FOREST)
                  || biome.is(BiomeTags.IS_JUNGLE)
                  || biome.is(BiomeTags.IS_TAIGA)
                  || biome.is(Biomes.SWAMP)
                  || biome.is(Biomes.MANGROVE_SWAMP)
            )) {
            options.add(DragonKind.THORN);
         }

         if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_DEEP_OCEAN) || biome.is(BiomeTags.IS_BEACH) || biome.is(BiomeTags.IS_RIVER)) {
            options.add(DragonKind.TIDE);
         }

         return options;
      }
   }
}
