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
      step("setup", 40, AutoTest::setup);
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
      for (String c : new String[] {"time set noon", "weather clear", "gamerule doDaylightCycle false", "gamerule doMobSpawning false",
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
