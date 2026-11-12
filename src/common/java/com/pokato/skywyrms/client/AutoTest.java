//#autotest-only
package com.pokato.skywyrms.client;

import com.pokato.skywyrms.Skywyrms;
import com.pokato.skywyrms.config.ModConfig;
import com.pokato.skywyrms.entity.DragonEggEntity;
import com.pokato.skywyrms.entity.DragonEntity;
import com.pokato.skywyrms.entity.DragonKind;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;

/**
 * In-game self test, only in jars built with --define AUTOTEST and started with -Dskywyrms.autotest=main.
 * Runs as a list of steps on the client tick once the player is in the (flat, creative) world, checks the results on
 * the integrated server and writes autotest_done.txt ("OK"/"FAIL" + one line per check) into the game directory.
 * "[autotest] SHOT name" log lines ask the launcher to grab the screen.
 */
public final class AutoTest {
   private static final String SCENARIO = System.getProperty("skywyrms.autotest");
   private static final List<Step> STEPS = new ArrayList<>();
   private static final StringBuilder REPORT = new StringBuilder();
   private static boolean ok = true;
   private static boolean finished;
   private static int index;
   private static int wait;
   private static int stepTicks;
   private static int joinedTicks;

   private interface Action {
      boolean run(Minecraft minecraft) throws Exception;
   }

   private record Step(String name, int after, Action action) {
   }

   private AutoTest() {
   }

   public static void tick(Minecraft minecraft) {
      if (SCENARIO == null || finished) {
         return;
      }
      if (minecraft.player == null || minecraft.level == null || minecraft.getSingleplayerServer() == null) {
         joinedTicks = 0;
         return;
      }
      //#if MC >= 260200
      boolean screenOpen = minecraft.gui.screen() != null;
      //#else
      boolean screenOpen = minecraft.screen != null;
      //#endif
      if (screenOpen) {
         joinedTicks = Math.min(joinedTicks, 20); // "Loading terrain" etc.: keys do nothing while a screen is open
         return;
      }
      if (++joinedTicks < 60) {
         return; // let the world settle
      }
      if (STEPS.isEmpty()) {
         build();
      }
      if (wait > 0) {
         wait--;
         return;
      }
      Step step = STEPS.get(index);
      boolean done;
      try {
         done = step.action.run(minecraft);
      } catch (Throwable t) {
         Skywyrms.LOGGER.error("[autotest] step '{}' failed", step.name, t);
         check("step_" + step.name, false, t.toString());
         done = true;
      }
      stepTicks++;
      if (done || stepTicks > 600) {
         if (!done) {
            check("step_" + step.name, false, "timed out");
         }
         Skywyrms.LOGGER.info("[autotest] step '{}' done", step.name);
         stepTicks = 0;
         wait = step.after;
         index++;
         if (index >= STEPS.size()) {
            finish(minecraft);
         }
      }
   }

   private static void build() {
      if (System.getProperty("skywyrms.autotest.reload") != null) {
         // Forge 1.21.1 in this test setup (test/batch.sh sets the property): the world is joined straight from the
         // launcher before Forge's event bus runs, so Forge misses the first data load and its loot hook then throws for
         // every mob that dies, a vanilla pig too ("Can not retrieve LootModifierManager..."). A /reload repairs it.
         // On a dedicated Forge 1.21.1 server and with 1.2.7 it is the same story: not the mod (tools/audit).
         step("reload", 160, m -> {
            command(m, "reload");
            return true;
         });
      }
      step("setup", 40, AutoTest::setup);
      if ("owner".equals(SCENARIO)) {
         ownerSteps();
         return;
      }
      if ("breedstay".equals(SCENARIO)) {
         breedStaySteps();
         return;
      }
      if ("spawn".equals(SCENARIO)) {
         spawnSteps();
         eggShareSteps();
         return;
      }
      if ("killdebug".equals(SCENARIO)) {
         killDebugSteps();
         return;
      }
      if ("audit".equals(SCENARIO)) {
         auditSteps();
         return;
      }
      if ("sneak".equals(SCENARIO)) {
         sneakSteps();
         return;
      }
      if ("spawnstat".equals(SCENARIO) || "spawnnatural".equals(SCENARIO)) {
         spawnStatSteps("spawnstat".equals(SCENARIO));
         return;
      }
      step("summon", 60, AutoTest::summonAll);
      step("count", 1, AutoTest::countDragons);
      shot("lineup");
      step("mount", 1, AutoTest::mount);
      step("wait_ride", 10, m -> {
         boolean riding = m.player.getVehicle() instanceof DragonEntity;
         if (riding || stepTicks > 60) {
            check("client_riding", riding, "after " + stepTicks + " ticks");
            return true;
         }
         return false;
      });
      step("third_person", 10, m -> {
         m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         return true;
      });
      step("takeoff", 70, m -> {
         m.options.keyJump.setDown(true);
         m.options.keyUp.setDown(true);
         m.player.setXRot(-10.0F);
         return true;
      });
      step("check_flying", 1, m -> {
         boolean[] flying = new boolean[1];
         onServer(m, () -> flying[0] = ember(m) != null && ember(m).isFlying());
         check("flight_takeoff", flying[0], "");
         m.options.keyJump.setDown(false);
         return true;
      });
      shot("flight");
      step("breath", 30, m -> {
         ModKeys.ABILITY.setDown(true);
         return true;
      });
      step("check_breath", 1, m -> {
         boolean[] b = new boolean[1];
         onServer(m, () -> b[0] = ember(m) != null && ember(m).isBreathing());
         check("ability_breath", b[0], "");
         return true;
      });
      shot("breath");
      step("descend", 1, m -> {
         ModKeys.ABILITY.setDown(false);
         m.options.keyUp.setDown(false);
         ModKeys.DESCEND.setDown(true);
         return true;
      });
      step("landing", 10, m -> {
         boolean[] landed = new boolean[1];
         onServer(m, () -> {
            DragonEntity d = ember(m);
            landed[0] = d != null && !d.isFlying() && d.onGround();
         });
         if (landed[0] || stepTicks > 400) {
            ModKeys.DESCEND.setDown(false);
            check("flight_landed", landed[0], "after " + stepTicks + " ticks");
            return true;
         }
         return false;
      });
      shot("landed");
      step("dismount", 20, m -> {
         onServer(m, () -> serverPlayer(m).stopRiding());
         m.options.setCameraType(CameraType.FIRST_PERSON);
         command(m, "tp @a 0 -60 0 0 10");
         return true;
      });
      step("abilities", 40, m -> {
         int[] cast = new int[1];
         onServer(m, () -> {
            for (DragonEntity d : dragons(m)) {
               if (d.getKind() == DragonKind.EMBER) {
                  continue;
               }
               d.setNoAi(false);
               boolean r = d.getKind().ability().held() ? d.startBreath() : d.castAbility();
               if (r) {
                  cast[0]++;
               }
            }
         });
         check("abilities_cast", cast[0] >= 4, cast[0] + " of 5");
         return true;
      });
      shot("abilities");
      step("stop_abilities", 20, m -> {
         onServer(m, () -> {
            for (DragonEntity d : dragons(m)) {
               d.stopBreath();
            }
         });
         return true;
      });
      step("egg", 40, m -> {
         command(m, "summon skywyrms:dragon_egg 1 -60 6");
         return true;
      });
      step("check_egg", 1, m -> {
         int[] n = new int[1];
         onServer(m, () -> n[0] = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 32)).size());
         check("egg_summoned", n[0] >= 1, "eggs=" + n[0]);
         return true;
      });
      shot("egg");
      breedingSteps();
      breedStaySteps();
      configSteps();
   }

   /** 1.2.4 config: parsing, taming items, stat multipliers, switched-off abilities, the rider's see-through view */
   private static void configSteps() {
      step("cfg_parse", 2, m -> {
         ModConfig p = ModConfig.fromJson(com.google.gson.JsonParser.parseString(
               "{\"tameItems\":[\"diamond\",\"#minecraft:gold_ores\"],\"tameChance\":1.0,\"healthMultiplier\":2.5,"
                     + "\"attackDamageMultiplier\":1.5,\"abilities\":{\"fire_breath\":false},\"riderSeeThrough\":\"first_person\","
                     + "\"wingSoundPitch\":0.7,\"breedItems\":[]}").getAsJsonObject());
         check("config_parse", p.tameItems.size() == 2 && p.tameChance == 1.0F && p.healthMultiplier == 2.5F && p.attackDamageMultiplier == 1.5F
               && !p.abilityEnabled("fire_breath") && p.abilityEnabled("lightning") && p.seeThroughMode() == 1
               && Math.abs(p.wingSoundPitch - 0.7F) < 1.0E-6F && p.breedItems.isEmpty(), "");
         check("config_roundtrip", ModConfig.fromJson(p.toJson()).toJson().equals(p.toJson()), "");
         check("config_item_match", ModConfig.matches(p.tameItems, "minecraft:diamond", java.util.stream.Stream.empty())
               && ModConfig.matches(p.tameItems, "minecraft:gold_ore", java.util.stream.Stream.of("minecraft:gold_ores"))
               && !ModConfig.matches(p.tameItems, "minecraft:beef", java.util.stream.Stream.of("minecraft:meat")), "");
         ModConfig def = ModConfig.fromJson(new com.google.gson.JsonObject());
         check("config_defaults", def.tameItems.isEmpty() && def.healthMultiplier == 1.0F && def.seeThroughMode() == 0
               && def.abilityEnabled("fire_breath") && Math.abs(def.tameChance - 1.0F / 3.0F) < 1.0E-6F && def.wingSoundVolume == 1.0F, "");
         return true;
      });
      step("cfg_spawn", 60, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "summon skywyrms:ember_wyrm 0 -60 8 {Rotation:[180f,0f]}");
         return true;
      });
      step("cfg_rules", 5, m -> {
         onServer(m, () -> {
            ModConfig c = ModConfig.get();
            ServerPlayer player = serverPlayer(m);
            InteractionHand hand = InteractionHand.MAIN_HAND;
            DragonEntity d = ember(m);
            // stats: the multiplier scales the dragon's own (gene) value
            d.setGenes(d.genes(), true);
            float h1 = d.getMaxHealth();
            c.healthMultiplier = 2.0F;
            d.setGenes(d.genes(), true);
            float h2 = d.getMaxHealth();
            c.healthMultiplier = 1.0F;
            check("config_health_multiplier", Math.abs(h2 - 2.0F * h1) < 0.01F, h1 + " -> " + h2);
            // taming: only the configured item works (survival, so the item is used up)
            c.tameItems = new ArrayList<>(List.of("diamond"));
            c.tameChance = 1.0F;
            serverCommand(m, "gamemode survival @a");
            player.setItemInHand(hand, new ItemStack(Items.BEEF, 64));
            for (int i = 0; i < 30; i++) {
               d.mobInteract(player, hand);
            }
            boolean meatTamed = d.isTame();
            player.setItemInHand(hand, new ItemStack(Items.DIAMOND, 1));
            d.mobInteract(player, hand);
            check("config_tame_item", !meatTamed && d.isTame() && d.isOwnedBy(player) && player.getMainHandItem().isEmpty(),
                  "meat tamed=" + meatTamed + " diamond tamed=" + d.isTame());
            serverCommand(m, "gamemode creative @a");
            c.tameItems = new ArrayList<>();
            c.tameChance = 1.0F / 3.0F;
            // a switched-off ability does nothing
            c.abilities.put("fire_breath", false);
            boolean off = d.startBreath();
            c.abilities.put("fire_breath", true);
            boolean on = d.startBreath();
            d.stopBreath();
            check("config_ability_switch", !off && on, "off=" + off + " on=" + on);
            // ride it for the see-through view
            d.setSaddled(true);
            player.setItemInHand(hand, ItemStack.EMPTY);
            d.mobInteract(player, hand);
            c.riderSeeThrough = "first_person";
         });
         m.options.setCameraType(CameraType.FIRST_PERSON);
         return true;
      });
      step("cfg_view", 40, m -> {
         m.player.setYRot(180.0F);
         m.player.setXRot(10.0F);
         Entity vehicle = m.player.getVehicle();
         check("config_see_through_first_person", vehicle instanceof DragonEntity d && com.pokato.skywyrms.client.render.DragonRenderer.seeThrough(d), "");
         return true;
      });
      shot("cfg_1_first_person_see_through");
      step("cfg_view_off", 5, m -> {
         ModConfig.get().riderSeeThrough = "off";
         return true;
      });
      shot("cfg_2_first_person_normal");
      step("cfg_view_back", 10, m -> {
         ModConfig.get().riderSeeThrough = "always";
         m.options.setCameraType(CameraType.THIRD_PERSON_BACK);
         return true;
      });
      shot("cfg_3_third_person_always");
      step("cfg_done", 10, m -> {
         ModConfig.get().riderSeeThrough = "off";
         return true;
      });
      shot("cfg_4_third_person_normal");
   }

   private static void step(String name, int after, Action action) {
      STEPS.add(new Step(name, after, action));
   }

   private static void shot(String name) {
      step("shot_" + name, 25, m -> {
         Skywyrms.LOGGER.info("[autotest] SHOT {}", name);
         return true;
      });
   }

   // ---------------------------------------------------------------- steps
   private static boolean setup(Minecraft m) {
      // no wild dragon may walk into a test: since 1.2.6 the tamed test dragons no longer keep them away, and on
      // 1.21.11+ the old gamerule names below are rejected (the scenario "spawnnatural" switches this on again)
      ModConfig.get().naturalSpawning = false;
      for (String c : new String[] {"time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false", "gamerule spawn_mobs false",
            "kill @e[type=!minecraft:player]", "gamemode creative @a", "tp @a 0 -60 0 0 0"}) {
         command(m, c);
      }
      return true;
   }

   private static boolean summonAll(Minecraft m) {
      int i = 0;
      for (DragonKind kind : DragonKind.values()) {
         double x = -15 + i * 6;
         command(m, String.format(Locale.ROOT, "summon skywyrms:%s %.1f -60 18 {NoAI:1b,Rotation:[180f,0f]}", kind.id(), x));
         i++;
      }
      return true;
   }

   private static boolean countDragons(Minecraft m) {
      int[] server = new int[1];
      onServer(m, () -> server[0] = dragons(m).size());
      int client = 0;
      for (Entity e : m.level.entitiesForRendering()) {
         if (e instanceof DragonEntity) {
            client++;
         }
      }
      check("summoned_all_six", server[0] == 6 && client == 6, "server=" + server[0] + " client=" + client);
      return true;
   }

   /**
    * Two tamed adult dragons fed golden apples must find each other, lay an egg and keep ticking afterwards - six
    * times, because the 1.2.3 crash (breed goal ticking once more without a partner) hit about every second breeding.
    */
   private static void breedingSteps() {
      int[] eggsBefore = new int[1];
      int[] laid = new int[1];
      step("breed_setup", 60, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "tp @a 0 -60 0 0 10");
         command(m, "summon skywyrms:ember_wyrm -10 -60 14 {Rotation:[90f,0f]}");
         command(m, "summon skywyrms:ember_wyrm 10 -60 14 {Rotation:[-90f,0f]}");
         return true;
      });
      for (int round = 1; round <= 6; round++) {
         int r0 = round;
         step("breed_love_" + round, 1, m -> {
            int[] inLove = new int[1];
            onServer(m, () -> {
               ServerPlayer player = serverPlayer(m);
               InteractionHand hand = InteractionHand.MAIN_HAND;
               int side = 0;
               for (DragonEntity d : dragons(m)) {
                  if (d.isBaby()) {
                     continue;
                  }
                  // 20 blocks apart: they have to walk, so the egg is laid on whichever tick they arrive
                  d.teleportTo(side++ == 0 ? -10.0 : 10.0, -60.0, 14.0);
                  d.tameTo(player.getUUID());
                  d.setAge(0); // breeding cooldown off, for the next round
                  player.setItemInHand(hand, new ItemStack(Items.GOLDEN_APPLE, 1));
                  d.mobInteract(player, hand);
                  if (d.isInLove()) {
                     inLove[0]++;
                  }
               }
               player.setItemInHand(hand, ItemStack.EMPTY);
               eggsBefore[0] = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 48)).size();
            });
            if (r0 == 1) {
               check("breeding_in_love", inLove[0] == 2, "in love=" + inLove[0]);
            }
            return true;
         });
         step("breed_wait_" + round, 20, m -> {
            int[] eggs = new int[1];
            onServer(m, () -> eggs[0] = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 48)).size());
            if (eggs[0] > eggsBefore[0] || stepTicks > 400) {
               if (eggs[0] > eggsBefore[0]) {
                  laid[0]++;
               }
               return true;
            }
            return false;
         });
      }
      step("breed_check", 1, m -> {
         check("breeding_eggs_laid", laid[0] == 6, laid[0] + " of 6 rounds");
         return true;
      });
      shot("bred_eggs");
      step("breed_after", 60, m -> true);
      step("breed_alive", 1, m -> {
         int[] n = new int[1];
         onServer(m, () -> {
            for (DragonEntity d : dragons(m)) {
               if (!d.isBaby()) {
                  n[0]++;
               }
            }
         });
         check("breeding_parents_fine", n[0] == 2, "adult dragons=" + n[0]);
         return true;
      });
   }

   /**
    * The answer we give players: an operator hands a dragon to someone else with
    * /data modify entity @e[type=skywyrms:ember_wyrm,limit=1,sort=nearest] Owner set from entity <name> UUID
    */
   private static void ownerSteps() {
      String[] before = new String[1];
      step("owner_spawn", 40, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "tp @a 0 -60 0 0 10");
         command(m, "summon skywyrms:ember_wyrm 0 -60 8 {Rotation:[180f,0f]}");
         return true;
      });
      step("owner_other", 5, m -> {
         onServer(m, () -> {
            DragonEntity d = ember(m);
            d.tameTo(java.util.UUID.fromString("0f0f0f0f-1111-2222-3333-444455556666")); // "somebody else"
            before[0] = String.valueOf(DragonEntity.ownerUuid(d)) + " ownedByTester=" + d.isOwnedBy(serverPlayer(m));
         });
         return true;
      });
      step("owner_command", 20, m -> {
         // exactly what a player types (run as Tester, at Tester's position)
         command(m, "execute as Tester at @s run data modify entity @e[type=skywyrms:ember_wyrm,limit=1,sort=nearest] Owner set from entity Tester UUID");
         return true;
      });
      step("owner_check", 1, m -> {
         String[] after = new String[1];
         boolean[] owned = new boolean[1];
         onServer(m, () -> {
            DragonEntity d = ember(m);
            owned[0] = d != null && d.isTame() && d.isOwnedBy(serverPlayer(m));
            after[0] = d == null ? "no dragon" : String.valueOf(DragonEntity.ownerUuid(d));
         });
         check("owner_transfer_command", owned[0] && !before[0].endsWith("ownedByTester=true"), "before " + before[0] + " | after " + after[0]);
         return true;
      });
      step("owner_obeys", 20, m -> {
         // the new owner can saddle and ride it (only the owner can)
         String[] r = new String[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            DragonEntity d = ember(m);
            InteractionHand hand = InteractionHand.MAIN_HAND;
            player.setItemInHand(hand, new ItemStack(Items.SADDLE));
            d.mobInteract(player, hand);
            player.setItemInHand(hand, ItemStack.EMPTY);
            d.mobInteract(player, hand);
            r[0] = "saddled=" + d.isSaddled() + " riding=" + (player.getVehicle() == d);
         });
         check("owner_new_owner_rides", r[0].contains("riding=true"), r[0]);
         return true;
      });
   }

   /** Two tamed dragons told to stay (sneak + right-click), fed golden apples: they must still breed. */
   private static void breedStaySteps() {
      int[] eggsBefore = new int[1];
      step("stay_setup", 60, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "tp @a 0 -60 0 0 10");
         command(m, "summon skywyrms:ember_wyrm -3 -60 12 {Rotation:[90f,0f]}");
         command(m, "summon skywyrms:frost_wyrm 3 -60 12 {Rotation:[-90f,0f]}");
         return true;
      });
      step("stay_order", 40, m -> {
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            InteractionHand hand = InteractionHand.MAIN_HAND;
            for (DragonEntity d : dragons(m)) {
               d.tameTo(player.getUUID());
               d.setSaddled(true);
               // sneak + right-click with an empty hand, like a player: "will stay here"
               player.setItemInHand(hand, ItemStack.EMPTY);
               player.setShiftKeyDown(true);
               d.mobInteract(player, hand);
               player.setShiftKeyDown(false);
            }
         });
         return true;
      });
      step("stay_love", 1, m -> {
         String[] r = new String[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            InteractionHand hand = InteractionHand.MAIN_HAND;
            int staying = 0;
            int inLove = 0;
            for (DragonEntity d : dragons(m)) {
               if (d.isOrderedToSit()) {
                  staying++;
               }
               player.setItemInHand(hand, new ItemStack(Items.GOLDEN_APPLE, 1));
               d.mobInteract(player, hand);
               if (d.isInLove()) {
                  inLove++;
               }
            }
            player.setItemInHand(hand, ItemStack.EMPTY);
            eggsBefore[0] = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 48)).size();
            r[0] = "staying=" + staying + " inLove=" + inLove;
         });
         check("stay_dragons_staying_in_love", r[0].equals("staying=2 inLove=2"), r[0]);
         return true;
      });
      step("stay_wait", 1, m -> {
         int[] eggs = new int[1];
         onServer(m, () -> eggs[0] = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 48)).size());
         if (stepTicks % 40 == 1) {
            onServer(m, () -> {
               for (DragonEntity d : dragons(m)) {
                  Skywyrms.LOGGER.info("[autotest] DIAG {} pos={} love={} sitPose={} ordered={} navDone={} flying={} onGround={} age={}", d.getKind(),
                        String.format(Locale.ROOT, "%.1f,%.1f,%.1f", d.getX(), d.getY(), d.getZ()), d.isInLove(), d.isInSittingPose(),
                        d.isOrderedToSit(), d.getNavigation().isDone(), d.isFlying(), d.onGround(), d.getAge());
               }
            });
         }
         if (eggs[0] > eggsBefore[0] || stepTicks > 500) {
            check("stay_breeding_egg_laid", eggs[0] > eggsBefore[0], "after " + stepTicks + " ticks");
            return true;
         }
         return false;
      });
      shot("stay_bred");
      step("stay_after", 60, m -> true);
      step("stay_still_staying", 1, m -> {
         int[] staying = new int[1];
         onServer(m, () -> {
            for (DragonEntity d : dragons(m)) {
               if (!d.isBaby() && d.isOrderedToSit()) {
                  staying[0]++;
               }
            }
         });
         check("stay_order_kept_after_breeding", staying[0] == 2, "staying=" + staying[0]);
         return true;
      });
   }

   /** Natural spawns: a tamed dragon next to the player must not block them, and under a leaf canopy they use the treetops. */
   private static void spawnSteps() {
      step("spawn_tamed_setup", 40, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "tp @a 0 -60 0 0 10");
         command(m, "summon skywyrms:tide_wyrm 3 -60 3");
         return true;
      });
      step("spawn_tamed_near", 5, m -> {
         int[] ok = new int[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            for (DragonEntity d : dragons(m)) {
               d.tameTo(player.getUUID());
            }
            for (int i = 0; i < 40; i++) {
               DragonEntity d = com.pokato.skywyrms.server.DragonSpawner.trySpawn(serverOverworld(m), player, ModConfig.get());
               if (d != null) {
                  ok[0]++;
                  d.discard();
               }
            }
         });
         check("spawn_with_own_dragon_nearby", ok[0] >= 10, ok[0] + " of 40 attempts spawned");
         return true;
      });
      step("spawn_canopy_setup", 40, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         for (int y : new int[] {-58, -57}) {
            for (int[] q : new int[][] {{-100, -100}, {0, -100}, {-100, 0}, {0, 0}}) {
               command(m, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:oak_leaves[persistent=true]", q[0], y, q[1], q[0] + 99, y, q[1] + 99));
            }
         }
         command(m, "tp @a 0 -56 0 0 10");
         return true;
      });
      step("spawn_canopy", 5, m -> {
         int[] ok = new int[1];
         int[] top = new int[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            for (int i = 0; i < 40; i++) {
               DragonEntity d = com.pokato.skywyrms.server.DragonSpawner.trySpawn(serverOverworld(m), player, ModConfig.get());
               if (d != null) {
                  ok[0]++;
                  if (d.getY() > -57.5) {
                     top[0]++;
                  }
                  d.discard();
               }
            }
         });
         check("spawn_under_leaf_canopy", ok[0] >= 10 && top[0] == ok[0], ok[0] + " of 40 spawned, " + top[0] + " on the treetops");
         return true;
      });
      step("spawn_canopy_clear", 20, m -> {
         for (int y : new int[] {-58, -57}) {
            for (int[] q : new int[][] {{-100, -100}, {0, -100}, {-100, 0}, {0, 0}}) {
               command(m, String.format(Locale.ROOT, "fill %d %d %d %d %d %d minecraft:air", q[0], y, q[1], q[0] + 99, y, q[1] + 99));
            }
         }
         command(m, "tp @a 0 -60 0 0 10");
         return true;
      });
   }

   // ---------------------------------------------------------------- what does killing each thing throw?
   private static void killDebugSteps() {
      step("kd_summon", 40, m -> {
         command(m, "summon skywyrms:ember_wyrm 0 -60 8 {NoAI:1b}");
         command(m, "summon skywyrms:storm_wyrm 6 -60 8 {NoAI:1b}");
         command(m, "summon skywyrms:dragon_egg 3 -60 4");
         command(m, "summon minecraft:pig -4 -60 4 {NoAI:1b}");
         return true;
      });
      step("kd_kill", 40, m -> {
         onServer(m, () -> {
            boolean first = true;
            for (net.minecraft.world.entity.LivingEntity e : serverOverworld(m).getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, around(m, 32), x -> !(x instanceof net.minecraft.world.entity.player.Player))) {
               if (e instanceof DragonEntity d && first) {
                  first = false;
                  d.tameTo(serverPlayer(m).getUUID());
                  d.setSaddled(true);
               }
               String what = e.getType().toString() + (e instanceof DragonEntity d ? " saddled=" + d.isSaddled() + " tame=" + d.isTame() : "");
               try {
                  //#if MC >= 12102
                  e.kill(serverOverworld(m));
                  //#else
                  e.kill();
                  //#endif
                  check("kill " + what, true, "removed=" + e.isRemoved() + " dead=" + e.isDeadOrDying());
               } catch (Throwable t) {
                  java.io.StringWriter w = new java.io.StringWriter();
                  t.printStackTrace(new java.io.PrintWriter(w));
                  Skywyrms.LOGGER.error("[autotest] KILL of {} threw: {}", what, w);
                  check("kill " + what, false, t.toString());
               }
            }
         });
         return true;
      });
   }

   // ---------------------------------------------------------------- 1.2.8: checks from the bug hunt
   private static String dragonState(DragonEntity d) {
      return "tame=" + d.isTame() + " owner=" + DragonEntity.ownerUuid(d) + " saddled=" + d.isSaddled() + " stay=" + d.isOrderedToSit() + " body=" + d.getKind()
         + " blood=" + d.blood() + " baby=" + d.isBaby() + " maxHealth=" + Math.round(d.getMaxHealth()) + " health=" + Math.round(d.getHealth())
         + " name=" + (d.hasCustomName() ? d.getCustomName().getString() : "-");
   }

   private static String eggState(com.pokato.skywyrms.entity.DragonEggEntity e) {
      return "owner=" + e.owner() + " coOwner=" + e.coOwner() + " body=" + e.body() + " blood=" + e.blood();
   }

   /**
    * "audit": a dragon, a hatchling and an egg must come back unchanged after their chunk was unloaded and loaded again;
    * a tamed dragon's venom cloud must not poison its owner or slow the dragon; an egg nobody owns must not hatch into
    * a "tamed" dragon without an owner; a saddled dragon that dies must drop its saddle.
    */
   private static void auditSteps() {
      java.util.UUID[] ids = new java.util.UUID[3];
      String[] before = new String[3];
      int[] eggProgress = new int[2];
      int[] growth = new int[2];
      java.util.UUID stranger = java.util.UUID.fromString("11111111-2222-3333-4444-555555555555");
      // far from the world spawn: the chunks around it never unload
      step("persist_go", 60, m -> {
         command(m, "tp @a 1000 -60 1000 0 10");
         return true;
      });
      step("persist_setup", 40, m -> {
         command(m, "summon skywyrms:ember_wyrm 1000 -60 1008");
         command(m, "summon skywyrms:tide_wyrm 1006 -60 1008");
         return true;
      });
      step("persist_prepare", 20, m -> {
         onServer(m, () -> {
            net.minecraft.server.level.ServerLevel level = serverOverworld(m);
            ServerPlayer player = serverPlayer(m);
            for (DragonEntity d : dragons(m)) {
               if (d.getKind() == DragonKind.EMBER) {
                  d.setGenes(com.pokato.skywyrms.entity.DragonGenes.wild(DragonKind.EMBER, DragonKind.FROST, d.getRandom()), true);
                  d.tameTo(player.getUUID());
                  d.setSaddled(true);
                  d.setOrderedToSit(true);
                  d.setHealth(d.getMaxHealth() * 0.5F);
                  d.setCustomName(net.minecraft.network.chat.Component.literal("Smaug"));
                  ids[0] = d.getUUID();
                  before[0] = dragonState(d);
               } else if (d.getKind() == DragonKind.TIDE) {
                  d.tameTo(player.getUUID());
                  d.makeHatchling(24000);
                  d.setOrderedToSit(true);
                  ids[1] = d.getUUID();
                  before[1] = dragonState(d);
                  growth[0] = Math.round(d.growth() * 100.0F);
               }
            }
            com.pokato.skywyrms.entity.DragonEggEntity egg = com.pokato.skywyrms.entity.DragonEggEntity.create(
               level, com.pokato.skywyrms.entity.DragonGenes.wild(DragonKind.STORM, DragonKind.VOID, level.getRandom()), player.getUUID(), 1234);
            if (egg != null) {
               egg.setCoOwner(stranger);
               egg.placeNear(level, new net.minecraft.world.phys.Vec3(994.0, -60.0, 1008.0));
               level.addFreshEntity(egg);
               ids[2] = egg.getUUID();
               before[2] = eggState(egg);
               eggProgress[0] = egg.progressTicks();
            }
         });
         return true;
      });
      shot("persist_before");
      step("persist_leave", 20, m -> {
         command(m, "tp @a 3000 -60 3000");
         return true;
      });
      boolean[] unloaded = new boolean[1];
      step("persist_unloaded", 20, m -> {
         boolean[] gone = new boolean[1];
         onServer(m, () -> {
            net.minecraft.server.level.ServerLevel level = serverOverworld(m);
            gone[0] = ids[0] != null && ids[1] != null && ids[2] != null && level.getEntity(ids[0]) == null && level.getEntity(ids[1]) == null && level.getEntity(ids[2]) == null;
         });
         if (gone[0] || stepTicks > 580) {
            unloaded[0] = gone[0];
            return true;
         }
         return false;
      });
      step("persist_back", 20, m -> {
         command(m, "tp @a 1000 -60 1000 0 10");
         return true;
      });
      step("persist_check", 20, m -> {
         String[] after = new String[3];
         onServer(m, () -> {
            net.minecraft.server.level.ServerLevel level = serverOverworld(m);
            if (ids[0] != null && level.getEntity(ids[0]) instanceof DragonEntity d) {
               after[0] = dragonState(d);
            }
            if (ids[1] != null && level.getEntity(ids[1]) instanceof DragonEntity d) {
               after[1] = dragonState(d);
               growth[1] = Math.round(d.growth() * 100.0F);
            }
            if (ids[2] != null && level.getEntity(ids[2]) instanceof com.pokato.skywyrms.entity.DragonEggEntity e) {
               after[2] = eggState(e);
               eggProgress[1] = e.progressTicks();
            }
         });
         if (after[0] == null || after[1] == null || after[2] == null) {
            if (stepTicks < 400) {
               return false;
            }
         }
         check("persist_chunk_was_unloaded", unloaded[0], "");
         check("persist_dragon", before[0] != null && before[0].equals(after[0]), "before: " + before[0] + " | after: " + after[0]);
         check("persist_hatchling", before[1] != null && before[1].equals(after[1]) && Math.abs(growth[1] - growth[0]) <= 3,
            "before: " + before[1] + " growth " + growth[0] + "% | after: " + after[1] + " growth " + growth[1] + "%");
         check("persist_egg", before[2] != null && before[2].equals(after[2]) && eggProgress[1] >= eggProgress[0] && eggProgress[1] < eggProgress[0] + 3000,
            "before: " + before[2] + " progress " + eggProgress[0] + " | after: " + after[2] + " progress " + eggProgress[1]);
         return true;
      });
      shot("persist_after");
      // ---- a tamed dragon's venom cloud next to its owner
      step("venom_setup", 40, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "tp @a 0 -60 0 0 0");
         command(m, "summon skywyrms:thorn_wyrm 0 -60 7 {NoAI:1b,Rotation:[180f,0f]}");
         command(m, "summon minecraft:pig 0 -60 3 {NoAI:1b}");
         command(m, "summon minecraft:horse 2 -60 3 {NoAI:1b}");
         command(m, "effect clear @a");
         return true;
      });
      boolean[] cast = new boolean[1];
      step("venom_cast", 50, m -> {
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            for (DragonEntity d : dragons(m)) {
               if (d.getKind() == DragonKind.THORN) {
                  d.tameTo(player.getUUID());
                  for (net.minecraft.world.entity.animal.Animal animal : serverOverworld(m).getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, around(m, 16), a -> !(a instanceof DragonEntity))) {
                     if (animal instanceof net.minecraft.world.entity.OwnableEntity) {
                        //#if MC >= 12111
                        ((net.minecraft.world.entity.animal.equine.AbstractHorse)animal).tameWithName(player);
                        //#else
                        ((net.minecraft.world.entity.animal.horse.AbstractHorse)animal).tameWithName(player);
                        //#endif
                     } else {
                        d.setTarget(animal);
                     }
                  }
                  cast[0] = d.getTarget() != null && d.castAbility();
               }
            }
         });
         return true;
      });
      shot("venom_cloud");
      step("venom_check", 5, m -> {
         String[] r = new String[1];
         boolean[] ok = new boolean[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            boolean ownerPoisoned = player.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
            int dragonEffects = -1;
            boolean targetPoisoned = false;
            boolean horsePoisoned = false;
            boolean horseOwned = false;
            for (DragonEntity d : dragons(m)) {
               if (d.getKind() == DragonKind.THORN) {
                  dragonEffects = d.getActiveEffects().size();
               }
            }
            for (net.minecraft.world.entity.animal.Animal animal : serverOverworld(m).getEntitiesOfClass(net.minecraft.world.entity.animal.Animal.class, around(m, 16), a -> !(a instanceof DragonEntity))) {
               if (animal instanceof net.minecraft.world.entity.OwnableEntity owned) {
                  horsePoisoned = animal.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
                  horseOwned = owned.getOwner() == player;
               } else {
                  targetPoisoned = animal.hasEffect(net.minecraft.world.effect.MobEffects.POISON);
               }
            }
            r[0] = "cast=" + cast[0] + " target poisoned=" + targetPoisoned + " owner poisoned=" + ownerPoisoned + " owner's horse poisoned=" + horsePoisoned
               + " (horse is the owner's=" + horseOwned + ") effects on the dragon=" + dragonEffects;
            ok[0] = cast[0] && targetPoisoned && !ownerPoisoned && horseOwned && !horsePoisoned && dragonEffects == 0;
         });
         check("venom_spares_owner_owners_horse_and_dragon", ok[0], r[0]);
         return true;
      });
      // ---- an egg nobody owns
      step("ownerless_egg", 40, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "effect clear @a");
         command(m, "summon skywyrms:dragon_egg 4 -60 6");
         return true;
      });
      step("ownerless_egg_hatch", 40, m -> {
         onServer(m, () -> {
            for (com.pokato.skywyrms.entity.DragonEggEntity e : serverOverworld(m).getEntitiesOfClass(com.pokato.skywyrms.entity.DragonEggEntity.class, around(m, 32))) {
               e.setProgressTicks(ModConfig.get().eggHatchTicks() - 5);
            }
         });
         return true;
      });
      step("ownerless_egg_check", 5, m -> {
         String[] r = {"no hatchling"};
         boolean[] ok = new boolean[1];
         onServer(m, () -> {
            for (DragonEntity d : dragons(m)) {
               r[0] = "hatchling baby=" + d.isBaby() + " tame=" + d.isTame() + " owner=" + DragonEntity.ownerUuid(d);
               ok[0] = d.isBaby() && !(d.isTame() && DragonEntity.ownerUuid(d) == null);
            }
         });
         check("ownerless_egg_hatchling_is_not_tame_without_owner", ok[0], r[0]);
         return true;
      });
      // ---- the saddle of a dragon that dies
      step("saddle_setup", 40, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "summon skywyrms:storm_wyrm 0 -60 8 {NoAI:1b}");
         return true;
      });
      step("saddle_kill", 30, m -> {
         onServer(m, () -> {
            for (DragonEntity d : dragons(m)) {
               d.tameTo(serverPlayer(m).getUUID());
               d.setSaddled(true);
            }
            serverCommand(m, "kill @e[type=minecraft:item]");
            serverCommand(m, "kill @e[type=skywyrms:storm_wyrm]");
         });
         return true;
      });
      step("saddle_check", 5, m -> {
         int[] saddles = new int[1];
         int[] items = new int[1];
         onServer(m, () -> {
            for (net.minecraft.world.entity.item.ItemEntity item : serverOverworld(m).getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around(m, 32))) {
               items[0]++;
               if (item.getItem().is(Items.SADDLE)) {
                  saddles[0] += item.getItem().getCount();
               }
            }
         });
         check("dead_dragon_drops_its_saddle", saddles[0] == 1, "saddles on the ground=" + saddles[0] + " (item stacks=" + items[0] + ")");
         return true;
      });
   }

   // ---------------------------------------------------------------- 1.2.8: the sneak key while riding
   /**
    * The real keys, as a player presses them. With the config option sneakFliesDown the sneak key must fly the dragon
    * down in the air and keep the rider on, and dismount on the ground. Without it (the default) it must dismount in the
    * air, as it always did.
    */
   private static void sneakSteps() {
      double[] startY = new double[1];
      boolean[] offAtTouchdown = new boolean[1];
      step("sneak_option_on", 5, m -> {
         ModConfig fresh = ModConfig.fromJson(new com.google.gson.JsonObject());
         check("config_sneak_flies_down_is_off_by_default", !fresh.sneakFliesDown && !ModConfig.get().sneakFliesDown && fresh.toJson().has("sneakFliesDown"), "");
         ModConfig.get().sneakFliesDown = true;
         return true;
      });
      step("summon", 60, AutoTest::summonAll);
      step("mount", 1, AutoTest::mount);
      step("wait_ride", 10, m -> m.player.getVehicle() instanceof DragonEntity || stepTicks > 60);
      step("takeoff", 90, m -> {
         m.options.keyJump.setDown(true);
         m.player.setXRot(0.0F);
         return true;
      });
      step("hover", 15, m -> {
         m.options.keyJump.setDown(false);
         return true;
      });
      step("sneak_press", 40, m -> {
         onServer(m, () -> startY[0] = ember(m) == null ? Double.NaN : ember(m).getY());
         m.options.keyShift.setDown(true);
         return true;
      });
      step("sneak_in_air", 1, m -> {
         String[] r = new String[1];
         boolean[] ok = new boolean[1];
         onServer(m, () -> {
            DragonEntity d = ember(m);
            if (d == null) {
               r[0] = "no ember";
               return;
            }
            boolean riding = serverPlayer(m).getVehicle() == d;
            double dropped = startY[0] - d.getY();
            r[0] = String.format(Locale.ROOT, "riding=%s after 2 s of sneak, started %.1f blocks up, flew down %.1f, on ground=%s", riding, startY[0] - (-60.0), dropped, d.onGround());
            ok[0] = riding && startY[0] > -52.0 && dropped > 2.0;
         });
         check("option_on_sneak_in_air_flies_down_and_keeps_rider", ok[0], r[0]);
         return true;
      });
      shot("sneak_down");
      step("sneak_landing", 12, m -> {
         boolean[] st = new boolean[2];
         onServer(m, () -> {
            DragonEntity d = ember(m);
            st[0] = d == null || d.onGround();
            st[1] = d != null && serverPlayer(m).getVehicle() == d;
         });
         if (st[0] || !st[1] || stepTicks > 400) {
            offAtTouchdown[0] = !st[1];
            return true;
         }
         return false;
      });
      step("sneak_seen_on_ground", 10, m -> {
         boolean[] riding = new boolean[1];
         onServer(m, () -> riding[0] = ember(m) != null && serverPlayer(m).getVehicle() == ember(m));
         offAtTouchdown[0] = !riding[0];
         m.options.keyShift.setDown(false);
         return true;
      });
      step("sneak_again_on_ground", 15, m -> {
         m.options.keyShift.setDown(true);
         return true;
      });
      step("sneak_on_ground", 10, m -> {
         m.options.keyShift.setDown(false);
         boolean[] st = new boolean[2];
         onServer(m, () -> {
            DragonEntity d = ember(m);
            st[0] = d != null && serverPlayer(m).getVehicle() == d;
            st[1] = d != null && d.onGround();
         });
         check("option_on_sneak_on_ground_dismounts", !st[0] && st[1], "riding=" + st[0] + " dragon on ground=" + st[1] + " (already off while the key was still held at touchdown=" + offAtTouchdown[0] + ")");
         return true;
      });
      // the default: as it always was
      step("classic_mount", 20, m -> {
         onServer(m, () -> {
            ModConfig.get().sneakFliesDown = false;
            serverPlayer(m).setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            if (ember(m) != null) {
               ember(m).mobInteract(serverPlayer(m), InteractionHand.MAIN_HAND);
            }
         });
         return true;
      });
      step("classic_takeoff", 90, m -> {
         m.options.keyJump.setDown(true);
         return true;
      });
      step("classic_sneak", 15, m -> {
         m.options.keyJump.setDown(false);
         onServer(m, () -> startY[0] = ember(m) == null || serverPlayer(m).getVehicle() != ember(m) ? Double.NaN : ember(m).getY());
         m.options.keyShift.setDown(true);
         return true;
      });
      step("classic_check", 5, m -> {
         m.options.keyShift.setDown(false);
         boolean[] riding = new boolean[1];
         onServer(m, () -> {
            riding[0] = ember(m) != null && serverPlayer(m).getVehicle() == ember(m);
         });
         check("default_sneak_in_air_dismounts", !riding[0] && startY[0] > -52.0, String.format(Locale.ROOT, "riding=%s, was %.1f blocks up", riding[0], startY[0] - (-60.0)));
         return true;
      });
   }

   // ---------------------------------------------------------------- spawning on real terrain (launcher: --world normal)
   /**
    * "spawnstat": goes to one place per biome in a normal world and tries the same 600 spawn points with the placement
    * of 1.2.5, of 1.2.6 and the current one (nothing is added to the world), then lets the real server tick spawn
    * dragons on a plain. "spawnnatural": only that last part.
    */
   @SuppressWarnings("unchecked")
   private static void spawnStatSteps(boolean stats) {
      Object[][] biomes = {
         {"plains", net.minecraft.world.level.biome.Biomes.PLAINS},
         {"meadow", net.minecraft.world.level.biome.Biomes.MEADOW},
         {"windswept_hills", net.minecraft.world.level.biome.Biomes.WINDSWEPT_HILLS},
         {"jagged_peaks", net.minecraft.world.level.biome.Biomes.JAGGED_PEAKS},
         {"forest", net.minecraft.world.level.biome.Biomes.FOREST},
         {"dark_forest", net.minecraft.world.level.biome.Biomes.DARK_FOREST},
         {"jungle", net.minecraft.world.level.biome.Biomes.JUNGLE},
         {"taiga", net.minecraft.world.level.biome.Biomes.TAIGA},
         {"swamp", net.minecraft.world.level.biome.Biomes.SWAMP},
         {"savanna", net.minecraft.world.level.biome.Biomes.SAVANNA},
         {"badlands", net.minecraft.world.level.biome.Biomes.BADLANDS},
         {"desert", net.minecraft.world.level.biome.Biomes.DESERT},
         {"snowy_plains", net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS},
         {"snowy_taiga", net.minecraft.world.level.biome.Biomes.SNOWY_TAIGA},
         {"ocean", net.minecraft.world.level.biome.Biomes.OCEAN},
         {"beach", net.minecraft.world.level.biome.Biomes.BEACH},
         {"river", net.minecraft.world.level.biome.Biomes.RIVER}};
      int[] plains = new int[3];
      step("stat_setup", 20, m -> {
         m.options.renderDistance().set(8);
         command(m, "gamemode spectator @a");
         return true;
      });
      for (Object[] b : biomes) {
         String name = (String)b[0];
         net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome> key = (net.minecraft.resources.ResourceKey<net.minecraft.world.level.biome.Biome>)b[1];
         int[] at = name.equals("plains") ? plains : new int[3]; // x, z, found
         if (!stats && at != plains) {
            continue;
         }
         step("stat_go_" + name, 20, m -> {
            onServer(m, () -> {
               var found = serverOverworld(m).findClosestBiome3d(h -> h.is(key), new net.minecraft.core.BlockPos(0, 70, 0), 6400, 32, 64);
               if (found != null) {
                  // the nearest point is on the biome's edge: go 100 blocks further in
                  double x = found.getFirst().getX();
                  double z = found.getFirst().getZ();
                  double len = Math.max(1.0, Math.sqrt(x * x + z * z));
                  at[0] = (int)(x + x / len * 100.0);
                  at[1] = (int)(z + z / len * 100.0);
                  at[2] = 1;
                  serverCommand(m, String.format(Locale.ROOT, "tp @a %d 160 %d", at[0], at[1]));
               }
            });
            return true;
         });
         step("stat_load_" + name, 10, m -> {
            boolean[] loaded = {at[2] == 0};
            if (!loaded[0]) {
               onServer(m, () -> {
                  boolean all = true;
                  for (int i = 0; i < 16 && all; i++) {
                     double a = i * Math.PI / 8.0;
                     all = serverOverworld(m).hasChunkAt(new net.minecraft.core.BlockPos(at[0] + (int)(Math.cos(a) * 106.0), 0, at[1] + (int)(Math.sin(a) * 106.0)));
                  }
                  loaded[0] = all;
               });
            }
            return loaded[0] || stepTicks > 560;
         });
         if (!stats) {
            continue;
         }
         step("stat_probe_" + name, 5, m -> {
            if (at[2] == 0) {
               check("stat_" + name, false, "biome not found");
               return true;
            }
            String[] line = new String[1];
            onServer(m, () -> line[0] = spawnStats(serverOverworld(m), serverPlayer(m), 600));
            Skywyrms.LOGGER.info("[autotest] STAT {} at {} {}: {}", name, at[0], at[1], line[0]);
            REPORT.append("STAT ").append(name).append(" at ").append(at[0]).append(' ').append(at[1]).append(": ").append(line[0]).append('\n');
            return true;
         });
      }
      // the real thing: a player standing on a plain, the server tick hook does the spawning
      int[] before = new int[1];
      step("natural_go", 20, m -> {
         command(m, "gamemode spectator @a");
         command(m, String.format(Locale.ROOT, "tp @a %d 160 %d", plains[0], plains[1]));
         return true;
      });
      step("natural_load", 10, m -> {
         boolean[] loaded = new boolean[1];
         onServer(m, () -> {
            boolean all = true;
            for (int i = 0; i < 16 && all; i++) {
               double a = i * Math.PI / 8.0;
               all = serverOverworld(m).hasChunkAt(new net.minecraft.core.BlockPos(plains[0] + (int)(Math.cos(a) * 106.0), 0, plains[1] + (int)(Math.sin(a) * 106.0)));
            }
            loaded[0] = all && serverOverworld(m).hasChunkAt(new net.minecraft.core.BlockPos(plains[0], 0, plains[1]));
         });
         return loaded[0] || stepTicks > 560;
      });
      step("natural_setup", 40, m -> {
         onServer(m, () -> {
            net.minecraft.server.level.ServerLevel level = serverOverworld(m);
            int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, plains[0], plains[1]);
            serverCommand(m, "gamemode creative @a");
            serverCommand(m, String.format(Locale.ROOT, "tp @a %d %d %d", plains[0], y + 1, plains[1]));
            serverCommand(m, "kill @e[type=!minecraft:player]");
         });
         return true;
      });
      step("natural_start", 320, m -> {
         onServer(m, () -> {
            ModConfig config = ModConfig.get();
            config.naturalSpawning = true;
            config.spawnIntervalTicks = 20;
            config.spawnChance = 1.0F;
            config.spawnSpacing = 0;
            serverCommand(m, "gamerule doMobSpawning true");
            serverCommand(m, "gamerule spawn_mobs true");
            before[0] = serverOverworld(m).getEntitiesOfClass(DragonEntity.class, around(m, 140).inflate(0.0, 400.0, 0.0), d -> d.isAlive() && !d.isTame()).size();
         });
         return true;
      });
      step("natural_count", 5, m -> {
         int[] now = new int[1];
         StringBuilder kinds = new StringBuilder();
         onServer(m, () -> {
            for (DragonEntity d : serverOverworld(m).getEntitiesOfClass(DragonEntity.class, around(m, 140).inflate(0.0, 400.0, 0.0), w -> w.isAlive() && !w.isTame())) {
               now[0]++;
               kinds.append(' ').append(d.getKind()).append('@').append((int)Math.sqrt(d.distanceToSqr(serverPlayer(m))));
            }
            ModConfig config = ModConfig.get();
            config.naturalSpawning = false;
            config.spawnIntervalTicks = 400;
            config.spawnChance = 0.12F;
            config.spawnSpacing = 96;
            serverCommand(m, "gamerule doMobSpawning false");
            serverCommand(m, "gamerule spawn_mobs false");
         });
         check("natural_spawn_by_server_tick", now[0] - before[0] >= 3, (now[0] - before[0]) + " wild dragons in 16 s with a spawn attempt every second:" + kinds);
         return true;
      });
      shot("natural_spawn");
   }

   /** The same n spawn points for all three placements; a line of counts. */
   private static String spawnStats(net.minecraft.server.level.ServerLevel level, ServerPlayer player, int n) {
      net.minecraft.util.RandomSource random = net.minecraft.util.RandomSource.create(4711L);
      int aroundY = net.minecraft.util.Mth.floor(player.getY());
      int unloaded = 0;
      int noBiome = 0;
      int fit = 0;
      int[] ok = new int[3];
      int treetop126 = 0;
      int[] lift = new int[4]; // current placement: on the ground at the middle column's height, 1-2 higher, 3+ higher, on the treetops
      java.util.EnumMap<DragonKind, int[]> perKind = new java.util.EnumMap<>(DragonKind.class);
      java.util.EnumMap<DragonKind, DragonEntity> probes = new java.util.EnumMap<>(DragonKind.class);
      for (int i = 0; i < n; i++) {
         double angle = random.nextDouble() * Math.PI * 2.0;
         double distance = 40.0 + random.nextDouble() * 50.0;
         int x0 = net.minecraft.util.Mth.floor(player.getX() + Math.cos(angle) * distance);
         int z0 = net.minecraft.util.Mth.floor(player.getZ() + Math.sin(angle) * distance);
         if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x0, 0, z0))) {
            unloaded++;
            continue;
         }
         List<DragonKind> options = com.pokato.skywyrms.server.DragonSpawner.kindsFor(level.getBiome(com.pokato.skywyrms.server.DragonSpawner.biomePos(level, x0, z0, aroundY)));
         if (options.isEmpty()) {
            noBiome++;
            continue;
         }
         DragonKind kind = options.get(random.nextInt(options.size()));
         fit++;
         DragonEntity dragon = probes.computeIfAbsent(kind, k -> com.pokato.skywyrms.server.DragonSpawner.newDragon(level, k));
         int[] k = perKind.computeIfAbsent(kind, q -> new int[4]);
         k[0]++;
         long seed = random.nextLong();
         if (spot125(level, dragon, kind, x0, z0, aroundY)) {
            ok[0]++;
            k[1]++;
         }
         int where126 = spot126(level, net.minecraft.util.RandomSource.create(seed), dragon, kind, x0, z0, aroundY);
         if (where126 != 0) {
            ok[1]++;
            k[2]++;
            if (where126 == 2) {
               treetop126++;
            }
         }
         if (com.pokato.skywyrms.server.DragonSpawner.findSpot(level, net.minecraft.util.RandomSource.create(seed), dragon, kind, x0, z0, aroundY)) {
            ok[2]++;
            k[3]++;
            int bx = net.minecraft.util.Mth.floor(dragon.getX());
            int bz = net.minecraft.util.Mth.floor(dragon.getZ());
            int by = net.minecraft.util.Mth.floor(dragon.getY());
            int reach = net.minecraft.util.Mth.ceil(dragon.getBbWidth() / 2.0F - 0.5F);
            int ground = Integer.MIN_VALUE;
            for (int dx = -reach; dx <= reach; dx++) {
               for (int dz = -reach; dz <= reach; dz++) {
                  ground = Math.max(ground, level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx + dx, bz + dz));
               }
            }
            int up = by - level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
            lift[by > ground ? 3 : up <= 0 ? 0 : up <= 2 ? 1 : 2]++;
         }
      }
      StringBuilder out = new StringBuilder();
      out.append("points=").append(n).append(" unloaded=").append(unloaded).append(" no_dragon_biome=").append(noBiome).append(" fit=").append(fit);
      out.append(" | spawned 1.2.5=").append(ok[0]).append(" 1.2.6=").append(ok[1]).append(" (treetop ").append(treetop126).append(") now=").append(ok[2]);
      out.append(" | now: level=").append(lift[0]).append(" up1-2=").append(lift[1]).append(" up3+=").append(lift[2]).append(" treetop=").append(lift[3]).append(" |");
      for (java.util.Map.Entry<DragonKind, int[]> e : perKind.entrySet()) {
         int[] v = e.getValue();
         out.append(' ').append(e.getKey()).append('=').append(v[0]).append(':').append(v[1]).append('/').append(v[2]).append('/').append(v[3]);
      }
      return out.toString();
   }

   /** where 1.2.5 put a wild dragon: the middle column's height, one try */
   private static boolean spot125(net.minecraft.server.level.ServerLevel level, DragonEntity dragon, DragonKind kind, int x, int z, int aroundY) {
      int y = level.dimensionType().hasCeiling()
         ? com.pokato.skywyrms.server.DragonSpawner.findFloor(level, x, aroundY, z)
         : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
      if (!com.pokato.skywyrms.server.DragonSpawner.inHeightRange(level, y)) {
         return false;
      }
      if (!level.getFluidState(new net.minecraft.core.BlockPos(x, y - 1, z)).isEmpty() && kind != DragonKind.TIDE) {
         return false;
      }
      return com.pokato.skywyrms.server.DragonSpawner.fits(level, dragon, x, y, z);
   }

   /** where 1.2.6 put a wild dragon: the middle column's height or its treetop, six tries within 12 blocks; 0 = nowhere, 1 = ground, 2 = treetop */
   private static int spot126(net.minecraft.server.level.ServerLevel level, net.minecraft.util.RandomSource random, DragonEntity dragon, DragonKind kind, int x0, int z0, int aroundY) {
      boolean ceiling = level.dimensionType().hasCeiling();
      for (int attempt = 0; attempt < 6; attempt++) {
         int x = attempt == 0 ? x0 : x0 + random.nextInt(25) - 12;
         int z = attempt == 0 ? z0 : z0 + random.nextInt(25) - 12;
         if (!level.hasChunkAt(new net.minecraft.core.BlockPos(x, 0, z))) {
            if (attempt == 0) {
               return 0;
            }
            continue;
         }
         int ground = ceiling
            ? com.pokato.skywyrms.server.DragonSpawner.findFloor(level, x, aroundY, z)
            : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
         if (!com.pokato.skywyrms.server.DragonSpawner.inHeightRange(level, ground)) {
            if (attempt == 0) {
               return 0;
            }
            continue;
         }
         int canopy = ceiling ? ground : level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
         for (int y : canopy > ground ? new int[] {ground, canopy} : new int[] {ground}) {
            if (!com.pokato.skywyrms.server.DragonSpawner.inHeightRange(level, y)) {
               continue;
            }
            if (y == ground && !level.getFluidState(new net.minecraft.core.BlockPos(x, y - 1, z)).isEmpty() && kind != DragonKind.TIDE) {
               continue;
            }
            if (com.pokato.skywyrms.server.DragonSpawner.fits(level, dragon, x, y, z)) {
               return y == ground ? 1 : 2;
            }
         }
      }
      return 0;
   }

   /** Eggs shared between players: co-owner pickup, a placed egg belongs to whoever placed it, breeding two players' dragons. */
   private static void eggShareSteps() {
      java.util.UUID friend = java.util.UUID.fromString("0f0f0f0f-1111-2222-3333-444455556666");
      java.util.UUID stranger = java.util.UUID.fromString("0e0e0e0e-9999-8888-7777-666655554444");
      step("egg_share_setup", 20, m -> {
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "gamemode survival @a");
         command(m, "clear @a");
         return true;
      });
      step("egg_coowner_pickup", 5, m -> {
         String[] r = new String[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            net.minecraft.server.level.ServerLevel level = serverOverworld(m);
            com.pokato.skywyrms.entity.DragonGenes g = com.pokato.skywyrms.entity.DragonGenes.wild(DragonKind.FROST, DragonKind.FROST, level.getRandom());
            DragonEggEntity mine = DragonEggEntity.create(level, g, friend, 0);
            mine.setCoOwner(player.getUUID());
            mine.placeNear(level, new net.minecraft.world.phys.Vec3(2, -60, 3));
            level.addFreshEntity(mine);
            DragonEggEntity notMine = DragonEggEntity.create(level, g, friend, 0);
            notMine.setCoOwner(stranger);
            notMine.placeNear(level, new net.minecraft.world.phys.Vec3(-2, -60, 3));
            level.addFreshEntity(notMine);
            player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            interact(mine, player);
            interact(notMine, player);
            int eggsInInventory = 0;
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
               if (player.getInventory().getItem(i).getItem() instanceof com.pokato.skywyrms.item.DragonEggItem) {
                  eggsInInventory++;
               }
            }
            r[0] = "coOwnerTook=" + mine.isRemoved() + " strangerEggStays=" + !notMine.isRemoved() + " inInventory=" + eggsInInventory;
         });
         check("egg_coowner_can_claim", r[0].equals("coOwnerTook=true strangerEggStays=true inInventory=1"), r[0]);
         return true;
      });
      step("egg_gift_place", 10, m -> {
         String[] r = new String[1];
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            net.minecraft.server.level.ServerLevel level = serverOverworld(m);
            com.pokato.skywyrms.entity.DragonGenes g = com.pokato.skywyrms.entity.DragonGenes.wild(DragonKind.THORN, DragonKind.THORN, level.getRandom());
            ItemStack gift = com.pokato.skywyrms.registry.ModItems.eggStack(g, friend, 0); // somebody else's egg, handed over
            player.setItemInHand(InteractionHand.MAIN_HAND, gift);
            net.minecraft.core.BlockPos ground = new net.minecraft.core.BlockPos(0, -61, 6);
            net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
                  net.minecraft.world.phys.Vec3.atCenterOf(ground).add(0, 0.5, 0), net.minecraft.core.Direction.UP, ground, false);
            gift.useOn(new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            DragonEggEntity placed = null;
            for (DragonEggEntity e : level.getEntitiesOfClass(DragonEggEntity.class, new AABB(ground).inflate(3))) {
               placed = e;
            }
            r[0] = placed == null ? "no egg placed" : "owner is placer=" + player.getUUID().equals(placed.owner());
         });
         check("egg_placed_belongs_to_placer", r[0].equals("owner is placer=true"), r[0]);
         return true;
      });
      int[] eggsBefore = new int[1];
      step("egg_two_players_setup", 40, m -> {
         command(m, "gamemode creative @a");
         command(m, "kill @e[type=!minecraft:player]");
         command(m, "summon skywyrms:ember_wyrm -3 -60 12 {Rotation:[90f,0f]}");
         command(m, "summon skywyrms:storm_wyrm 3 -60 12 {Rotation:[-90f,0f]}");
         return true;
      });
      step("egg_two_players_love", 1, m -> {
         onServer(m, () -> {
            ServerPlayer player = serverPlayer(m);
            InteractionHand hand = InteractionHand.MAIN_HAND;
            for (DragonEntity d : dragons(m)) {
               if (d.getKind() == DragonKind.EMBER) {
                  d.tameTo(friend);   // the friend's dragon, fed by the friend (offline here: no love cause)
                  d.setInLove(null);
               } else {
                  d.tameTo(player.getUUID());
                  player.setItemInHand(hand, new ItemStack(Items.GOLDEN_APPLE, 1));
                  d.mobInteract(player, hand);
               }
            }
            player.setItemInHand(hand, ItemStack.EMPTY);
            eggsBefore[0] = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 48)).size();
         });
         return true;
      });
      step("egg_two_players_wait", 1, m -> {
         String[] r = new String[1];
         onServer(m, () -> {
            List<DragonEggEntity> eggs = serverOverworld(m).getEntitiesOfClass(DragonEggEntity.class, around(m, 48));
            if (eggs.size() > eggsBefore[0]) {
               DragonEggEntity e = eggs.get(eggs.size() - 1);
               java.util.UUID me = serverPlayer(m).getUUID();
               boolean both = (me.equals(e.owner()) && friend.equals(e.coOwner())) || (friend.equals(e.owner()) && me.equals(e.coOwner()));
               r[0] = "both players can claim=" + both;
            }
         });
         if (r[0] != null || stepTicks > 500) {
            check("egg_from_two_players_dragons", "both players can claim=true".equals(r[0]), r[0] == null ? "no egg" : r[0]);
            return true;
         }
         return false;
      });
   }

   /** right-click with the main hand, the public way (Mob.mobInteract is protected) */
   private static void interact(net.minecraft.world.entity.Mob mob, ServerPlayer player) {
      //#if MC >= 260000
      mob.interact(player, InteractionHand.MAIN_HAND, mob.position());
      //#else
      mob.interact(player, InteractionHand.MAIN_HAND);
      //#endif
   }

   private static boolean mount(Minecraft m) {
      String[] result = new String[1];
      onServer(m, () -> {
         ServerPlayer player = serverPlayer(m);
         DragonEntity d = ember(m);
         if (d == null) {
            result[0] = "no ember";
            return;
         }
         d.setNoAi(false);
         InteractionHand hand = InteractionHand.MAIN_HAND;
         player.setItemInHand(hand, new ItemStack(Items.BEEF, 64));
         int feeds = 0;
         while (!d.isTame() && feeds < 80) {
            d.mobInteract(player, hand);
            feeds++;
         }
         player.setItemInHand(hand, new ItemStack(Items.SADDLE));
         d.mobInteract(player, hand);
         player.setItemInHand(hand, ItemStack.EMPTY);
         d.mobInteract(player, hand);
         result[0] = "tame=" + d.isTame() + " saddled=" + d.isSaddled() + " feeds=" + feeds + " riding=" + (player.getVehicle() == d);
      });
      check("mount_tame_saddle_ride", result[0] != null && result[0].contains("riding=true"), result[0]);
      return true;
   }

   // ---------------------------------------------------------------- helpers
   private static void command(Minecraft m, String cmd) {
      onServer(m, () -> serverCommand(m, cmd));
   }

   /** only on the server thread (inside onServer) */
   private static void serverCommand(Minecraft m, String cmd) {
      MinecraftServer server = m.getSingleplayerServer();
      server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd);
   }

   private static void onServer(Minecraft m, Runnable r) {
      AtomicReference<Throwable> err = new AtomicReference<>();
      try {
         m.getSingleplayerServer().submit(() -> {
            try {
               r.run();
            } catch (Throwable t) {
               err.set(t);
            }
         }).get();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
      if (err.get() != null) {
         throw new RuntimeException(err.get());
      }
   }

   private static ServerPlayer serverPlayer(Minecraft m) {
      return m.getSingleplayerServer().getPlayerList().getPlayers().get(0);
   }

   private static net.minecraft.server.level.ServerLevel serverOverworld(Minecraft m) {
      return m.getSingleplayerServer().overworld();
   }

   private static AABB around(Minecraft m, double r) {
      return serverPlayer(m).getBoundingBox().inflate(r);
   }

   private static List<DragonEntity> dragons(Minecraft m) {
      return serverOverworld(m).getEntitiesOfClass(DragonEntity.class, around(m, 96), Entity::isAlive); // dying dragons linger for their death animation
   }

   private static DragonEntity ember(Minecraft m) {
      for (DragonEntity d : dragons(m)) {
         if (d.getKind() == DragonKind.EMBER) {
            return d;
         }
      }
      return null;
   }

   private static void check(String name, boolean pass, String detail) {
      ok &= pass;
      REPORT.append(name).append(": ").append(pass).append(detail.isEmpty() ? "" : " (" + detail + ")").append('\n');
      Skywyrms.LOGGER.info("[autotest] CHECK {} = {} {}", name, pass, detail);
   }

   private static void finish(Minecraft m) {
      finished = true;
      try {
         File out = new File(m.gameDirectory, "autotest_done.txt");
         Files.write(out.toPath(), ((ok ? "OK" : "FAIL") + "\n" + REPORT).getBytes(StandardCharsets.UTF_8));
      } catch (Exception e) {
         Skywyrms.LOGGER.error("[autotest] cannot write report", e);
      }
      Skywyrms.LOGGER.info("[autotest] finished: {}", ok ? "OK" : "FAIL");
   }
}
