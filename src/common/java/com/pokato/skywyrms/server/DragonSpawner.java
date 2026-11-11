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

   /**
    * One natural spawn attempt 40-90 blocks from the player. The biome at that point picks the kind. Only wild dragons
    * count for spawnSpacing (1.2.6). Where exactly the dragon is put is {@link #findSpot}.
    */
   public static DragonEntity trySpawn(ServerLevel level, ServerPlayer player, ModConfig config) {
      RandomSource random = level.getRandom();
      double angle = random.nextDouble() * Math.PI * 2.0;
      double distance = 40.0 + random.nextDouble() * 50.0;
      int x0 = Mth.floor(player.getX() + Math.cos(angle) * distance);
      int z0 = Mth.floor(player.getZ() + Math.sin(angle) * distance);
      if (!level.hasChunkAt(new BlockPos(x0, 0, z0))) {
         return null;
      }

      int aroundY = Mth.floor(player.getY());
      BlockPos at = biomePos(level, x0, z0, aroundY);
      List<DragonKind> options = kindsFor(level.getBiome(at));
      if (options.isEmpty()) {
         return null;
      }

      DragonKind kind = options.get(random.nextInt(options.size()));
      DragonKind blood = kind;
      if (options.size() > 1 && random.nextFloat() < config.wildHybridChance) {
         List<DragonKind> others = new ArrayList<>(options);
         others.remove(kind);
         blood = others.get(random.nextInt(others.size()));
      }

      double spacing = config.spawnSpacing;
      if (!level.getEntitiesOfClass(DragonEntity.class, new AABB(at).inflate(spacing, 96.0, spacing), d -> !d.isTame()).isEmpty()) {
         return null;
      }

      DragonEntity dragon = newDragon(level, kind);
      if (dragon == null || !findSpot(level, random, dragon, kind, x0, z0, aroundY)) {
         return null;
      }

      //#if MC >= 12105
      dragon.snapTo(dragon.getX(), dragon.getY(), dragon.getZ(), random.nextFloat() * 360.0F, 0.0F);
      //#else
      dragon.moveTo(dragon.getX(), dragon.getY(), dragon.getZ(), random.nextFloat() * 360.0F, 0.0F);
      //#endif
      dragon.setGenes(DragonGenes.wild(kind, blood, random), true);
      BlockPos pos = dragon.blockPosition();
      //#if MC >= 12102
      dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), EntitySpawnReason.NATURAL, null);
      //#elif MC >= 12005 && MC < 12102
      dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null);
      //#else
      dragon.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
      //#endif
      return level.addFreshEntity(dragon) ? dragon : null;
   }

   /** A dragon of that kind that is not in the world yet. */
   public static DragonEntity newDragon(ServerLevel level, DragonKind kind) {
      //#if MC >= 12102
      return (DragonEntity)ModEntities.type(kind).create(level, EntitySpawnReason.NATURAL);
      //#else
      return (DragonEntity)ModEntities.type(kind).create(level);
      //#endif
   }

   /** Where the biome is looked up for a spawn at that column: on the surface (in the Nether on the floor near the player's height). */
   public static BlockPos biomePos(ServerLevel level, int x, int z, int aroundY) {
      int ground = level.dimensionType().hasCeiling() ? findFloor(level, x, aroundY, z) : level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      return new BlockPos(x, inHeightRange(level, ground) ? ground : aroundY, z);
   }

   /**
    * Looks for a place where this dragon fits: at (x0, z0) and at five more points within 12 blocks. Leaves the dragon
    * standing there and returns true, or returns false.
    * <p>
    * A dragon's hitbox is about 3 x 3 blocks (ember and void: a bit over 3, so it touches 5 x 5 columns). Up to 1.2.6 it
    * was put at the height of the middle column only, so on a slope, where a neighbouring column is higher, the hitbox
    * was inside that block and the try failed as "no room". Now it stands on the highest block under its whole
    * footprint. All six points are tried on the ground first and only then on the treetops (MOTION_BLOCKING counts
    * leaves), so that in a forest a dragon sits up in the canopy, where it is hard to see, only when there is no
    * clearing. Land dragons are not put on water or lava.
    * <p>
    * Measured with the AutoTest scenario "spawnstat" (17 places in a normal world, 600 spawn points each, all in a
    * dragon biome): 1.2.5 placed 45 % of them, 1.2.6 89 %, this 99 % (the rest: land dragons over water).
    */
   public static boolean findSpot(ServerLevel level, RandomSource random, DragonEntity dragon, DragonKind kind, int x0, int z0, int aroundY) {
      boolean ceiling = level.dimensionType().hasCeiling();
      int reach = Mth.ceil(dragon.getBbWidth() / 2.0F - 0.5F); // columns the hitbox covers on each side of the middle one
      int[] xs = new int[6];
      int[] zs = new int[6];
      int[] canopies = new int[6];

      for (int i = 0; i < 6; i++) {
         int x = xs[i] = i == 0 ? x0 : x0 + random.nextInt(25) - 12;
         int z = zs[i] = i == 0 ? z0 : z0 + random.nextInt(25) - 12;
         canopies[i] = Integer.MIN_VALUE;
         if (!level.hasChunkAt(new BlockPos(x - reach, 0, z - reach)) || !level.hasChunkAt(new BlockPos(x + reach, 0, z + reach))) {
            continue;
         }

         if (ceiling) {
            // the Nether: the floor near the player's height, lifted a little when the ground around is uneven
            int floor = findFloor(level, x, aroundY, z);
            for (int lift = 0; lift <= 3 && inHeightRange(level, floor); lift++) {
               if (fits(level, dragon, x, floor + lift, z)) {
                  return true;
               }
            }
            continue;
         }

         int own = level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         if (kind != DragonKind.TIDE && !level.getFluidState(new BlockPos(x, own - 1, z)).isEmpty()) {
            continue;
         }

         int ground = own;
         int canopy = level.getHeight(Types.MOTION_BLOCKING, x, z);
         for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
               ground = Math.max(ground, level.getHeight(Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz));
               canopy = Math.max(canopy, level.getHeight(Types.MOTION_BLOCKING, x + dx, z + dz));
            }
         }

         if (inHeightRange(level, ground) && fits(level, dragon, x, ground, z)) {
            return true;
         }

         if (canopy > ground && inHeightRange(level, canopy)) {
            canopies[i] = canopy;
         }
      }

      for (int i = 0; i < 6; i++) {
         if (canopies[i] != Integer.MIN_VALUE && fits(level, dragon, xs[i], canopies[i], zs[i])) {
            return true;
         }
      }

      return false;
   }

   /** Puts the dragon (not in the world yet) at that block and says whether its hitbox is free there. */
   public static boolean fits(ServerLevel level, DragonEntity dragon, int x, int y, int z) {
      //#if MC >= 12105
      dragon.snapTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
      //#else
      dragon.moveTo(x + 0.5, y, z + 0.5, 0.0F, 0.0F);
      //#endif
      return level.noCollision(dragon);
   }

   public static boolean inHeightRange(ServerLevel level, int y) {
      //#if MC >= 12102
      return y > level.getMinY() + 1 && y < level.getMaxY() - 8;
      //#else
      return y > level.getMinBuildHeight() + 1 && y < level.getMaxBuildHeight() - 8;
      //#endif
   }

   public static int findFloor(ServerLevel level, int x, int around, int z) {
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
